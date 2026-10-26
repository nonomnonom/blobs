#pragma once

#include <memory>
#include <vector>

#include "llama.h"

namespace blobs {

inline constexpr int kKvDecodeBatchTokens = 256;

/**
 * Owns the one in-memory KV context used by the serialized local runtime.
 *
 * Only an exact token prefix is reused. The runtime resets it after a failed
 * or cancelled decode, before replacing model weights, and on memory trim.
 */
class KvCache {
  public:
    llama_context *prepare(llama_model *model, int capacity, int threads,
                           ggml_abort_callback abort_callback, void *abort_callback_data);

    // Removes stale KV cells and returns the first prompt token that must be decoded.
    int reuse_prefix(const std::vector<llama_token> &prompt_tokens);

    // Call only after every prompt token has been decoded successfully.
    void remember_prompt(const std::vector<llama_token> &prompt_tokens);
    void reset();

  private:
    using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;

    Context create_context(llama_model *model, int capacity, int threads,
                           ggml_abort_callback abort_callback, void *abort_callback_data,
                           ggml_type cache_type) const;

    Context context_{nullptr, llama_free};
    std::vector<llama_token> prompt_tokens_;
    llama_model *model_ = nullptr;
    int capacity_ = 0;
    int requested_threads_ = 0; // threads applied to the live context by the last prepare()
};

} // namespace blobs
