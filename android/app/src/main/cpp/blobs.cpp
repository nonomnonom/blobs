#include <jni.h>

#include <limits>
#include <new>
#include <stdexcept>
#include <string>

#include "local_model_runtime.h"

namespace {

std::string utf8(JNIEnv *env, jbyteArray bytes) {
    const auto length = env->GetArrayLength(bytes);
    std::string value(static_cast<std::size_t>(length), '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(value.data()));
    if (value.find('\0') != std::string::npos) {
        throw std::invalid_argument("NATIVE_TEXT");
    }
    return value;
}

void throw_java(JNIEnv *env, const char *type, const char *message) {
    const auto error = env->FindClass(type);
    if (error) {
        env->ThrowNew(error, message);
        env->DeleteLocalRef(error);
    }
}

} // namespace

extern "C" JNIEXPORT void JNICALL Java_dev_blobs_app_inference_NativeBridge_reset(JNIEnv *,
                                                                                  jobject) {
    blobs::reset_generation();
}

extern "C" JNIEXPORT void JNICALL Java_dev_blobs_app_inference_NativeBridge_stop(JNIEnv *,
                                                                                 jobject) {
    blobs::stop_generation();
}

extern "C" JNIEXPORT void JNICALL Java_dev_blobs_app_inference_NativeBridge_releaseModel(JNIEnv *,
                                                                                         jobject) {
    blobs::release_model();
}

extern "C" JNIEXPORT jint JNICALL Java_dev_blobs_app_inference_NativeBridge_generateNative(
    JNIEnv *env, jobject, jbyteArray path, jbyteArray system, jbyteArray user, jbyteArray grammar,
    jbyteArray grammar_root, jbyteArray backend_directory, jint limit, jint threads, jobject sink) {
    if (!path || !system || !user || !grammar || !grammar_root || !backend_directory || !sink ||
        limit < 1 || limit > 512 || threads < 1 || threads > 4) {
        throw_java(env, "java/lang/IllegalArgumentException", "NATIVE_ARGUMENT");
        return blobs::kGenerationFailure;
    }

    try {
        const auto sink_class = env->GetObjectClass(sink);
        if (!sink_class) {
            return blobs::kGenerationFailure;
        }
        const auto callback = env->GetMethodID(sink_class, "onToken", "([BI)Z");
        env->DeleteLocalRef(sink_class);
        if (!callback) {
            return blobs::kGenerationFailure;
        }

        bool callback_failed = false;
        jbyteArray output = nullptr;
        jsize output_capacity = 0;
        const blobs::GenerationRequest request{
            utf8(env, path),
            utf8(env, system),
            utf8(env, user),
            utf8(env, grammar),
            utf8(env, grammar_root),
            utf8(env, backend_directory),
            limit,
            threads,
        };
        const auto result = blobs::generate(request, [&](const char *token, std::size_t size) {
            if (size > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
                callback_failed = true;
                throw_java(env, "java/lang/IllegalStateException", "TOKEN_SIZE");
                return false;
            }
            const auto token_size = static_cast<jsize>(size);
            if (!output || output_capacity < token_size) {
                const auto replacement = env->NewByteArray(token_size);
                if (!replacement) {
                    callback_failed = true;
                    return false;
                }
                if (output) {
                    env->DeleteLocalRef(output);
                }
                output = replacement;
                output_capacity = token_size;
            }
            env->SetByteArrayRegion(output, 0, token_size, reinterpret_cast<const jbyte *>(token));
            if (env->ExceptionCheck()) {
                callback_failed = true;
                return false;
            }
            const auto accepted = env->CallBooleanMethod(sink, callback, output, token_size);
            if (env->ExceptionCheck()) {
                callback_failed = true;
                return false;
            }
            return accepted == JNI_TRUE;
        });
        if (output) {
            env->DeleteLocalRef(output);
        }
        return callback_failed ? blobs::kGenerationFailure : result;
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/IllegalStateException", "MEMORY");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    }
    return blobs::kGenerationFailure;
}
