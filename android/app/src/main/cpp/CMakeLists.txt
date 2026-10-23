cmake_minimum_required(VERSION 3.22.1)
project(blobs LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release CACHE STRING "Native inference is optimized in every APK" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
option(BLOBS_ENABLE_SNAPDRAGON_BACKENDS
    "Build the optional Snapdragon OpenCL and Hexagon llama.cpp backends" OFF)

if(BLOBS_ENABLE_SNAPDRAGON_BACKENDS)
    if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
        message(FATAL_ERROR "Snapdragon backends require the arm64-v8a Android ABI")
    endif()
    if(NOT DEFINED HEXAGON_SDK_ROOT AND DEFINED ENV{HEXAGON_SDK_ROOT})
        set(HEXAGON_SDK_ROOT "$ENV{HEXAGON_SDK_ROOT}")
    endif()
    if(NOT DEFINED HEXAGON_TOOLS_ROOT AND DEFINED ENV{HEXAGON_TOOLS_ROOT})
        set(HEXAGON_TOOLS_ROOT "$ENV{HEXAGON_TOOLS_ROOT}")
    endif()
    if(NOT DEFINED CMAKE_PREFIX_PATH OR CMAKE_PREFIX_PATH STREQUAL "")
        if(DEFINED ENV{OPENCL_SDK_ROOT})
            set(CMAKE_PREFIX_PATH "$ENV{OPENCL_SDK_ROOT}")
        endif()
    endif()
    if(NOT IS_DIRECTORY "${HEXAGON_SDK_ROOT}")
        message(FATAL_ERROR "Set HEXAGON_SDK_ROOT before enabling Snapdragon backends")
    endif()
    if(NOT IS_DIRECTORY "${HEXAGON_TOOLS_ROOT}/Tools")
        message(FATAL_ERROR "Set HEXAGON_TOOLS_ROOT before enabling Snapdragon backends")
    endif()
    if(NOT DEFINED CMAKE_PREFIX_PATH OR CMAKE_PREFIX_PATH STREQUAL "")
        message(FATAL_ERROR "Set OPENCL_SDK_ROOT before enabling Snapdragon backends")
    endif()
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_HEXAGON ON CACHE BOOL "" FORCE)
else()
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
endif()
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
    set(FETCHCONTENT_SOURCE_DIR_KLEIDIAI "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/kleidiai-v1.31.0" CACHE PATH "Pinned, verified Arm kernels" FORCE)
    if(NOT EXISTS "${FETCHCONTENT_SOURCE_DIR_KLEIDIAI}/CMakeLists.txt")
        message(FATAL_ERROR "Prepare pinned native sources with: uv run --project ../corpus --locked python tools/fetch_runtime.py")
    endif()
else()
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
endif()
if(ANDROID_ABI STREQUAL "x86_64" OR CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    # SSE4.2 is part of Android's x86_64 ABI; AVX features remain runtime-dependent.
    set(GGML_SSE42 ON CACHE BOOL "" FORCE)
endif()
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
add_subdirectory(../../../../third_party/llama.cpp-620d2225cc436ab0b001e3200e590e3b286ef9ab ${CMAKE_CURRENT_BINARY_DIR}/llama)
add_library(blobs SHARED blobs.cpp kv_cache.cpp local_model_runtime.cpp)
target_link_libraries(blobs PRIVATE llama android log)
target_link_options(blobs PRIVATE "-Wl,-z,max-page-size=16384")

if(BLOBS_ENABLE_SNAPDRAGON_BACKENDS)
    target_compile_definitions(blobs PRIVATE BLOBS_DYNAMIC_BACKENDS=1)
    foreach(runtime_target llama ggml ggml-base ggml-cpu ggml-opencl ggml-hexagon)
        add_dependencies(blobs ${runtime_target})
        add_custom_command(
            TARGET blobs POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
                $<TARGET_FILE:${runtime_target}>
                $<TARGET_FILE_DIR:blobs>
            VERBATIM
        )
    endforeach()
    foreach(htp_target htp-v73 htp-v75 htp-v79 htp-v81)
        add_dependencies(blobs ${htp_target})
    endforeach()
    add_custom_command(
        TARGET blobs POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            ${CMAKE_CURRENT_BINARY_DIR}/llama/ggml/src/ggml-hexagon/libggml-htp-v73.so
            ${CMAKE_CURRENT_BINARY_DIR}/llama/ggml/src/ggml-hexagon/libggml-htp-v75.so
            ${CMAKE_CURRENT_BINARY_DIR}/llama/ggml/src/ggml-hexagon/libggml-htp-v79.so
            ${CMAKE_CURRENT_BINARY_DIR}/llama/ggml/src/ggml-hexagon/libggml-htp-v81.so
            $<TARGET_FILE_DIR:blobs>
        VERBATIM
    )
endif()
