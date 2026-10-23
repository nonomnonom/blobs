#include "local_model_runtime.h"

#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <sys/stat.h>
#include <vector>

#include "ggml-backend.h"
#include "kv_cache.h"
#include "llama.h"

namespace blobs {
namespace {

std::atomic<bool> cancelled{false};
std::mutex model_owner;
std::once_flag backend_initialized;
constexpr int kMinimumContextTokens = 512;
constexpr int kMaximumContextTokens = 4096;
constexpr int kMaximumTokenPieceBytes = 4096;
constexpr int kAllModelLayers = 999;

using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;

struct ModelFileIdentity {
    dev_t device;
    ino_t inode;
    off_t size;
    time_t modified_seconds;
    long modified_nanoseconds;
};

Model cached_model(nullptr, llama_model_free);
std::string cached_model_path;
std::optional<ModelFileIdentity> cached_model_identity;
KvCache kv_cache;

bool abort_decode(void *) {
    return cancelled.load();
}

bool load_progress(float, void *) {
    return !cancelled.load();
}

std::optional<ModelFileIdentity> file_identity(const std::string &path) {
    struct stat info {};
    if (stat(path.c_str(), &info) != 0 || !S_ISREG(info.st_mode)) {
        return std::nullopt;
    }
    return ModelFileIdentity{
        info.st_dev,
        info.st_ino,
        info.st_size,
        info.st_mtime,
        info.st_mtim.tv_nsec,
    };
}

bool same_identity(const ModelFileIdentity &left, const ModelFileIdentity &right) {
    return left.device == right.device && left.inode == right.inode && left.size == right.size &&
           left.modified_seconds == right.modified_seconds &&
           left.modified_nanoseconds == right.modified_nanoseconds;
}

int context_capacity(int prompt_tokens, int output_tokens) {
    const int required = prompt_tokens + output_tokens;
    int capacity = kMinimumContextTokens;
    while (capacity < required && capacity < kMaximumContextTokens) {
        capacity *= 2;
    }
    if (capacity < required) {
        throw std::runtime_error("CONTEXT_FULL");
    }
    return capacity;
}

std::string render_prompt(llama_model *model, const std::string &system, const std::string &user) {
    const char *chat_template = llama_model_chat_template(model, nullptr);
    if (!chat_template) {
        throw std::runtime_error("MODEL_TEMPLATE");
    }
    const llama_chat_message messages[] = {
        {"system", system.c_str()},
        {"user", user.c_str()},
    };
    const int length = llama_chat_apply_template(chat_template, messages, 2, true, nullptr, 0);
    if (length <= 0 || length > 200000) {
        throw std::runtime_error("MODEL_TEMPLATE");
    }
    std::vector<char> rendered(static_cast<std::size_t>(length) + 1);
    if (llama_chat_apply_template(
            chat_template,
            messages,
            2,
            true,
            rendered.data(),
            rendered.size()
        ) != length) {
        throw std::runtime_error("MODEL_TEMPLATE");
    }
    std::string prompt(rendered.data(), static_cast<std::size_t>(length));
    char architecture[64] = {};
    llama_model_meta_val_str(model, "general.architecture", architecture, sizeof(architecture));
    const std::string architecture_name(architecture);
    if (architecture_name == "qwen3" || architecture_name == "qwen3moe" ||
        architecture_name == "qwen35") {
        prompt += "<think>\n\n</think>\n\n";
    }
    return prompt;
}

llama_model *load_model(const std::string &path) {
    const auto identity = file_identity(path);
    const bool cache_hit = cached_model && identity && cached_model_identity &&
                           cached_model_path == path &&
                           same_identity(*identity, *cached_model_identity);
    if (cache_hit) {
        return cached_model.get();
    }

    kv_cache.reset();
    cached_model.reset();
    cached_model_path.clear();
    cached_model_identity.reset();
    auto params = llama_model_default_params();
#if defined(BLOBS_DYNAMIC_BACKENDS)
    params.n_gpu_layers = kAllModelLayers;
#else
    params.n_gpu_layers = 0;
#endif
    params.progress_callback = load_progress;
    Model loaded(llama_model_load_from_file(path.c_str(), params), llama_model_free);
#if defined(BLOBS_DYNAMIC_BACKENDS)
    // An accelerator load can fail before llama.cpp reports an available device
    // (for example, when a package-local module cannot allocate its buffers).
    // The regular build already starts on CPU; the optional build must always
    // make this one CPU attempt before treating the model as unavailable.
    if (!loaded && !cancelled.load()) {
        params.n_gpu_layers = 0;
        loaded.reset(llama_model_load_from_file(path.c_str(), params));
    }
#endif
    if (!loaded) {
        if (cancelled.load()) {
            return nullptr;
        }
        throw std::runtime_error("MODEL_LOAD");
    }
    if (!identity) {
        throw std::runtime_error("MODEL_FILE");
    }
    cached_model = std::move(loaded);
    cached_model_path = path;
    cached_model_identity = identity;
    return cached_model.get();
}

void initialize_backends(const std::string &directory) {
#if defined(BLOBS_DYNAMIC_BACKENDS)
    if (!directory.empty()) {
        ggml_backend_load_all_from_path(directory.c_str());
    }
#endif
    llama_backend_init();
}

}  // namespace

void reset_generation() {
    cancelled = false;
}

void stop_generation() {
    cancelled = true;
}

void release_model() {
    std::lock_guard<std::mutex> lock(model_owner);
    kv_cache.reset();
    cached_model.reset();
    cached_model_path.clear();
    cached_model_identity.reset();
}

int generate(const GenerationRequest &request, const TokenSink &sink) {
    if (request.model_path.empty() || request.system_prompt.empty() || request.user_prompt.empty() ||
        request.output_token_limit < 1 || request.output_token_limit > 512 || request.threads < 1 ||
        request.threads > 4 || !sink) {
        throw std::invalid_argument("NATIVE_ARGUMENT");
    }

    std::lock_guard<std::mutex> lock(model_owner);
    std::call_once(backend_initialized, [&request] {
        initialize_backends(request.backend_directory);
    });
    if (cancelled.load()) {
        return kGenerationCancelled;
    }

    llama_model *model = load_model(request.model_path);
    if (!model) {
        return kGenerationCancelled;
    }
    const auto prompt = render_prompt(model, request.system_prompt, request.user_prompt);
    const auto *vocab = llama_model_get_vocab(model);
    const int token_count = -llama_tokenize(
        vocab,
        prompt.data(),
        prompt.size(),
        nullptr,
        0,
        true,
        true
    );
    if (token_count <= 0 || token_count + request.output_token_limit > kMaximumContextTokens) {
        throw std::runtime_error("CONTEXT_FULL");
    }
    std::vector<llama_token> prompt_tokens(static_cast<std::size_t>(token_count));
    if (llama_tokenize(
            vocab,
            prompt.data(),
            prompt.size(),
            prompt_tokens.data(),
            token_count,
            true,
            true
        ) < 0) {
        throw std::runtime_error("TOKENIZE");
    }

    const int capacity = context_capacity(token_count, request.output_token_limit);
    llama_context *context = kv_cache.prepare(
        model,
        capacity,
        request.threads,
        abort_decode,
        nullptr
    );
    if (!context) {
        throw std::runtime_error("CONTEXT_ALLOC");
    }
    const int prompt_start = kv_cache.reuse_prefix(prompt_tokens);

    using Batch = std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)>;
    Batch batch(llama_batch_ext_init(context), llama_batch_ext_free);
    if (!batch) {
        kv_cache.reset();
        throw std::runtime_error("CONTEXT_ALLOC");
    }
    const auto discard_cache = [&] {
        batch.reset();
        kv_cache.reset();
    };
    const auto decode = [&](const llama_token *tokens, int amount, int offset) {
        llama_batch_ext_clear(batch.get());
        for (int index = 0; index < amount; ++index) {
            const int token_index = llama_batch_ext_add_token(batch.get(), 0, tokens[index]);
            if (token_index < 0) {
                throw std::runtime_error("BATCH");
            }
            const llama_pos position = offset + index;
            llama_batch_ext_set_pos(batch.get(), token_index, &position);
        }
        llama_batch_ext_set_output_logits(batch.get(), amount - 1, true);
        return llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get());
    };
    for (int position = prompt_start; position < token_count; position += kKvDecodeBatchTokens) {
        if (cancelled.load()) {
            discard_cache();
            return kGenerationCancelled;
        }
        int decode_result = 0;
        try {
            decode_result = decode(
                prompt_tokens.data() + position,
                std::min(kKvDecodeBatchTokens, token_count - position),
                position
            );
        } catch (...) {
            discard_cache();
            throw;
        }
        if (decode_result) {
            if (cancelled.load()) {
                discard_cache();
                return kGenerationCancelled;
            }
            discard_cache();
            throw std::runtime_error("DECODE");
        }
    }
    kv_cache.remember_prompt(prompt_tokens);

    using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
    Sampler sampler(
        llama_sampler_chain_init(llama_sampler_chain_default_params()),
        llama_sampler_free
    );
    if (!sampler) {
        throw std::runtime_error("CONTEXT_ALLOC");
    }
    const auto add_sampler = [&](llama_sampler *component) {
        if (!component) {
            throw std::runtime_error("CONTEXT_ALLOC");
        }
        llama_sampler_chain_add(sampler.get(), component);
    };
    add_sampler(
        llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 256, 1.0f, 0.0f, 1.2f)
    );
    add_sampler(llama_sampler_init_top_k(20));
    add_sampler(llama_sampler_init_top_p(0.9f, 1));
    add_sampler(llama_sampler_init_temp(0.3f));
    add_sampler(llama_sampler_init_dist(42));

    std::vector<char> piece(256);
    for (int index = 0; index < request.output_token_limit; ++index) {
        if (cancelled.load()) {
            discard_cache();
            return kGenerationCancelled;
        }
        const auto token = llama_sampler_sample(sampler.get(), context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            return kGenerationFinished;
        }
        int size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (size < 0) {
            if (size < -kMaximumTokenPieceBytes) {
                discard_cache();
                throw std::runtime_error("TOKEN_DECODE");
            }
            piece.resize(static_cast<std::size_t>(-size));
            size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (size < 0 || size > kMaximumTokenPieceBytes) {
            discard_cache();
            throw std::runtime_error("TOKEN_DECODE");
        }
        if (!sink(piece.data(), static_cast<std::size_t>(size))) {
            if (cancelled.load()) {
                discard_cache();
                return kGenerationCancelled;
            }
            return kGenerationTokenLimit;
        }
        if (index + 1 < request.output_token_limit) {
            int decode_result = 0;
            try {
                decode_result = decode(&token, 1, token_count + index);
            } catch (...) {
                discard_cache();
                throw;
            }
            if (decode_result) {
                if (cancelled.load()) {
                    discard_cache();
                    return kGenerationCancelled;
                }
                discard_cache();
                throw std::runtime_error("DECODE");
            }
        }
    }
    return kGenerationTokenLimit;
}

}  // namespace blobs
