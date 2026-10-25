#include "kv_cache.h"

#include <algorithm>
#include <cstddef>
#include <iterator>

namespace blobs {

KvCache::Context KvCache::create_context(llama_model *model, int capacity, int threads,
                                         ggml_abort_callback abort_callback,
                                         void *abort_callback_data, ggml_type cache_type) const {
    auto params = llama_context_default_params();
    params.n_ctx = capacity;
    params.n_batch = kKvDecodeBatchTokens;
    params.n_ubatch = kKvDecodeBatchTokens / 2;
    params.n_seq_max = 1;
    params.n_threads = threads;
    params.n_threads_batch = threads;
    params.type_k = cache_type;
    params.type_v = cache_type;
    params.kv_unified = true;
    params.abort_callback = abort_callback;
    params.abort_callback_data = abort_callback_data;
    return Context(llama_init_from_model(model, params), llama_free);
}

llama_context *KvCache::prepare(llama_model *model, int capacity, int threads,
                                ggml_abort_callback abort_callback, void *abort_callback_data) {
    if (context_ && model_ == model && capacity_ == capacity) {
        // A thread count is a scheduling choice, not a cache identity. Rebuilding the context to
        // change it would throw away the prefix the caller is trying to reuse.
        if (requested_threads_ != threads) {
            llama_set_n_threads(context_.get(), threads, threads);
            requested_threads_ = threads;
        }
        return context_.get();
    }

    reset();
    // Q8_0 halves K/V element storage relative to llama.cpp's F16 default.
    // llama.cpp enables Flash Attention automatically when the backend supports
    // it; an unsupported model or backend falls back to the lossless default.
    context_ = create_context(model, capacity, threads, abort_callback, abort_callback_data,
                              GGML_TYPE_Q8_0);
    if (!context_) {
        context_ = create_context(model, capacity, threads, abort_callback, abort_callback_data,
                                  GGML_TYPE_F16);
    }
    if (!context_) {
        return nullptr;
    }

    model_ = model;
    capacity_ = capacity;
    requested_threads_ = threads;
    return context_.get();
}

int KvCache::reuse_prefix(const std::vector<llama_token> &prompt_tokens) {
    if (!context_) {
        return 0;
    }

    const auto comparable_tokens = std::min(prompt_tokens_.size(), prompt_tokens.size());
    const auto mismatch =
        std::mismatch(prompt_tokens_.begin(),
                      prompt_tokens_.begin() + static_cast<std::ptrdiff_t>(comparable_tokens),
                      prompt_tokens.begin());
    const int matching_tokens =
        static_cast<int>(std::distance(prompt_tokens_.begin(), mismatch.first));
    auto *memory = llama_get_memory(context_.get());
    if (matching_tokens == 0 || !memory) {
        if (memory) {
            llama_memory_clear(memory, true);
        }
        prompt_tokens_.clear();
        return 0;
    }

    // Logits are not part of the KV cache. Remove and replay the final shared
    // token so the sampler observes logits for this request's actual prompt.
    const int replay_from = matching_tokens - 1;
    if (!llama_memory_seq_rm(memory, 0, replay_from, -1)) {
        llama_memory_clear(memory, true);
        prompt_tokens_.clear();
        return 0;
    }
    prompt_tokens_.resize(static_cast<std::size_t>(matching_tokens));
    return replay_from;
}

void KvCache::remember_prompt(const std::vector<llama_token> &prompt_tokens) {
    prompt_tokens_ = prompt_tokens;
}

void KvCache::reset() {
    context_.reset();
    prompt_tokens_.clear();
    model_ = nullptr;
    capacity_ = 0;
    requested_threads_ = 0;
}

} // namespace blobs
