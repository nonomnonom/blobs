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
    int context_tokens;
};

using TokenSink = std::function<bool(const char *, std::size_t)>;

/** Stage costs of the most recent generate() call, in milliseconds; -1 means the stage did not run. */
struct GenerationTimings {
    long long load_ms;
    long long template_ms;
    long long tokenize_ms;
    long long context_prepare_ms;
    long long grammar_ms;
    long long sampler_ms;
    long long prefill_ms;
    long long first_token_ms;
    long long total_ms;
    bool model_cached;
    int reused_prefix_tokens;
    int prompt_tokens;
    int context_capacity;
};

void reset_generation();
void stop_generation();
void release_model();
int generate(const GenerationRequest &request, const TokenSink &sink);

// Read from the JNI thread immediately after generate() returns; zeroed at the start of each call.
GenerationTimings last_generation_timings();

} // namespace blobs
