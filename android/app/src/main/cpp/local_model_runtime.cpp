#include "local_model_runtime.h"

#include <algorithm>
#include <atomic>
#include <chrono>
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
constexpr int kMaximumTokenPieceBytes = 4096;
constexpr int kAllModelLayers = 999;

GenerationTimings last_timings{-1, -1, -1, -1, -1, -1, -1, -1, -1, false, 0, 0, 0};

long long elapsed_ms(const std::chrono::steady_clock::time_point &from) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::steady_clock::now() - from)
        .count();
}

void begin_timings() {
    last_timings = GenerationTimings{-1, -1, -1, -1, -1, -1, -1, -1, -1, false, 0, 0, 0};
}

// Turns are serialized by the runtime, so one process-wide slot is enough and needs no lock.
struct TotalRecorder {
    const std::chrono::steady_clock::time_point &started;
    ~TotalRecorder() { last_timings.total_ms = elapsed_ms(started); }
};

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

bool abort_decode(void *) { return cancelled.load(); }

bool load_progress(float, void *) { return !cancelled.load(); }

std::optional<ModelFileIdentity> file_identity(const std::string &path) {
    struct stat info{};
    if (stat(path.c_str(), &info) != 0 || !S_ISREG(info.st_mode)) {
        return std::nullopt;
    }
    return ModelFileIdentity{
        info.st_dev, info.st_ino, info.st_size, info.st_mtime, info.st_mtim.tv_nsec,
    };
}

bool same_identity(const ModelFileIdentity &left, const ModelFileIdentity &right) {
    return left.device == right.device && left.inode == right.inode && left.size == right.size &&
           left.modified_seconds == right.modified_seconds &&
           left.modified_nanoseconds == right.modified_nanoseconds;
}

int context_capacity(int prompt_tokens, int output_tokens, int fixed_capacity) {
    // One capacity for every turn: a bucket that moves makes KvCache::prepare free the context, and
    // the prefix cache then never gets a hit. The Kotlin side already pins this value
    // (inference/InferenceContextPolicy.kt), so an oversized request is a rejection, not a re-bucket.
    if (prompt_tokens + output_tokens > fixed_capacity) {
        throw std::runtime_error("CONTEXT_FULL");
    }
    return fixed_capacity;
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
    if (llama_chat_apply_template(chat_template, messages, 2, true, rendered.data(),
                                  rendered.size()) != length) {
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

llama_model *load_model(const std::string &path, bool *cached) {
    const auto identity = file_identity(path);
    const bool cache_hit = cached_model && identity && cached_model_identity &&
                           cached_model_path == path &&
                           same_identity(*identity, *cached_model_identity);
    *cached = cache_hit;
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

} // namespace

void reset_generation() { cancelled = false; }

void stop_generation() { cancelled = true; }

void release_model() {
    std::lock_guard<std::mutex> lock(model_owner);
    kv_cache.reset();
    cached_model.reset();
    cached_model_path.clear();
    cached_model_identity.reset();
}

int generate(const GenerationRequest &request, const TokenSink &sink) {
    const bool grammar_without_root = !request.grammar.empty() && request.grammar_root.empty();
    if (request.model_path.empty() || request.system_prompt.empty() ||
        request.user_prompt.empty() || request.output_token_limit < 1 ||
        request.output_token_limit > 512 || request.threads < 1 || request.threads > 4 ||
        request.context_tokens < 1 || request.output_token_limit >= request.context_tokens ||
        grammar_without_root || !sink) {
        throw std::invalid_argument("NATIVE_ARGUMENT");
    }

    std::lock_guard<std::mutex> lock(model_owner);
    begin_timings();
    const auto started = std::chrono::steady_clock::now();
    const TotalRecorder total_recorder{started};
    std::call_once(backend_initialized,
                   [&request] { initialize_backends(request.backend_directory); });
    if (cancelled.load()) {
        return kGenerationCancelled;
    }

    bool model_cached = false;
    const auto load_started = std::chrono::steady_clock::now();
    llama_model *model = load_model(request.model_path, &model_cached);
    last_timings.load_ms = elapsed_ms(load_started);
    last_timings.model_cached = model_cached;
    if (!model) {
        return kGenerationCancelled;
    }
    const auto template_started = std::chrono::steady_clock::now();
    const auto prompt = render_prompt(model, request.system_prompt, request.user_prompt);
    last_timings.template_ms = elapsed_ms(template_started);
    const auto *vocab = llama_model_get_vocab(model);
    const auto tokenize_started = std::chrono::steady_clock::now();
    const int token_count =
        -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
    if (token_count <= 0) {
        last_timings.tokenize_ms = elapsed_ms(tokenize_started);
        throw std::runtime_error("CONTEXT_FULL");
    }
    std::vector<llama_token> prompt_tokens(static_cast<std::size_t>(token_count));
    if (llama_tokenize(vocab, prompt.data(), prompt.size(), prompt_tokens.data(), token_count, true,
                       true) < 0) {
        last_timings.tokenize_ms = elapsed_ms(tokenize_started);
        throw std::runtime_error("TOKENIZE");
    }
    last_timings.tokenize_ms = elapsed_ms(tokenize_started);
    last_timings.prompt_tokens = token_count;

    const int capacity =
        context_capacity(token_count, request.output_token_limit, request.context_tokens);
    last_timings.context_capacity = capacity;
    const auto prepare_started = std::chrono::steady_clock::now();
    llama_context *context =
        kv_cache.prepare(model, capacity, request.threads, abort_decode, nullptr);
    last_timings.context_prepare_ms = elapsed_ms(prepare_started);
    if (!context) {
        throw std::runtime_error("CONTEXT_ALLOC");
    }
    const int prompt_start = kv_cache.reuse_prefix(prompt_tokens);
    last_timings.reused_prefix_tokens = prompt_start;

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
    const auto prefill_started = std::chrono::steady_clock::now();
    for (int position = prompt_start; position < token_count; position += kKvDecodeBatchTokens) {
        if (cancelled.load()) {
            discard_cache();
            return kGenerationCancelled;
        }
        int decode_result = 0;
        try {
            decode_result =
                decode(prompt_tokens.data() + position,
                       std::min(kKvDecodeBatchTokens, token_count - position), position);
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
    last_timings.prefill_ms = elapsed_ms(prefill_started);

    using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
    const auto sampler_started = std::chrono::steady_clock::now();
    Sampler sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),
                    llama_sampler_free);
    if (!sampler) {
        throw std::runtime_error("CONTEXT_ALLOC");
    }
    const auto add_sampler = [&](llama_sampler *component) {
        if (!component) {
            throw std::runtime_error("CONTEXT_ALLOC");
        }
        llama_sampler_chain_add(sampler.get(), component);
    };
    last_timings.sampler_ms = elapsed_ms(sampler_started);
    if (!request.grammar.empty()) {
        // Constrained decoding: the sampler can only accept a token that keeps the response
        // document valid. A grammar that fails to parse is a build fault, not a model result.
        const auto grammar_started = std::chrono::steady_clock::now();
        llama_sampler *constraint = llama_sampler_init_grammar(vocab, request.grammar.c_str(),
                                                               request.grammar_root.c_str());
        if (!constraint) {
            throw std::runtime_error("GRAMMAR");
        }
        llama_sampler_chain_add(sampler.get(), constraint);
        last_timings.grammar_ms = elapsed_ms(grammar_started);
    }
    const auto tail_started = std::chrono::steady_clock::now();
    add_sampler(llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 256, 1.0f, 0.0f, 1.2f));
    add_sampler(llama_sampler_init_top_k(20));
    add_sampler(llama_sampler_init_top_p(0.9f, 1));
    add_sampler(llama_sampler_init_temp(0.3f));
    add_sampler(llama_sampler_init_dist(42));
    last_timings.sampler_ms += elapsed_ms(tail_started);

    std::vector<char> piece(256);
    const auto generation_started = std::chrono::steady_clock::now();
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
        if (index == 0) {
            last_timings.first_token_ms = elapsed_ms(generation_started);
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

GenerationTimings last_generation_timings() { return last_timings; }

} // namespace blobs
