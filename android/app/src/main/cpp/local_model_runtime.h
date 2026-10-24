#pragma once

#include <cstddef>
#include <functional>
#include <string>

namespace blobs {

inline constexpr int kGenerationFinished = 0;
inline constexpr int kGenerationCancelled = 1;
inline constexpr int kGenerationTokenLimit = 2;
inline constexpr int kGenerationFailure = 3;

struct GenerationRequest {
    std::string model_path;
    std::string system_prompt;
    std::string user_prompt;
    std::string grammar;
    std::string grammar_root;
    std::string backend_directory;
    int output_token_limit;
    int threads;
};

using TokenSink = std::function<bool(const char *, std::size_t)>;

void reset_generation();
void stop_generation();
void release_model();
int generate(const GenerationRequest &request, const TokenSink &sink);

} // namespace blobs
