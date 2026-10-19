#include <jni.h>
#include <algorithm>
#include <atomic>
#include <array>
#include <chrono>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <sys/stat.h>
#include <vector>
#include <stdexcept>
#include "llama.h"
#include "chat_prompt.h"

namespace {
std::atomic<bool> cancelled{false};
std::mutex owner;
std::once_flag initialized;
using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
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
// Destroy the context before its model, including at process shutdown.
Context cached_context(nullptr, llama_free);
std::vector<llama_token> cached_prompt;
void clear_context() {
    cached_context.reset();
    cached_prompt.clear();
}
struct ContextGuard {
    bool healthy = false;
    bool persistent;
    bool prompt_complete = false;
    bool recoverable_abort = false;
    ~ContextGuard() {
        if (healthy && persistent)
            return;
        if (persistent && prompt_complete && recoverable_abort && cached_context) {
            auto memory = llama_get_memory(cached_context.get());
            const auto keep = static_cast<llama_pos>(cached_prompt.size()) - 1;
            // Only the completed prompt is reusable after an aborted generation ubatch.
            // Remove its final token too: the next request must refresh its logits.
            if (memory && keep >= 0 && llama_memory_seq_pos_min(memory, 0) == 0 &&
                llama_memory_seq_pos_max(memory, 0) >= keep - 1 &&
                llama_memory_seq_rm(memory, 0, keep, -1)) {
                cached_prompt.resize(keep);
                return;
            }
        }
        clear_context();
    }
};
using Clock = std::chrono::steady_clock;
jlong elapsed_us(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - start).count();
}
struct Stage {
    jlong &value;
    Clock::time_point start = Clock::now();
    bool running = true;
    void stop() {
        if (running) {
            value = elapsed_us(start);
            running = false;
        }
    }
    ~Stage() { stop(); }
};
struct Measurements {
    JNIEnv *env;
    jlongArray output;
    Clock::time_point start = Clock::now();
    std::array<jlong, 17> values;
    Measurements(JNIEnv *env, jlongArray output) : env(env), output(output) {
        values.fill(-1);
        values[7] = values[10] = values[11] = 0;
    }
    ~Measurements() {
        values[0] = elapsed_us(start);
        // Keep diagnostic timings on failure without replacing the original JNI exception.
        auto error = env->ExceptionOccurred();
        if (error)
            env->ExceptionClear();
        env->SetLongArrayRegion(output, 0, values.size(), values.data());
        if (error) {
            if (env->ExceptionCheck())
                env->ExceptionClear();
            env->Throw(error);
            env->DeleteLocalRef(error);
        }
    }
};
bool abort_decode(void *) { return cancelled.load(); }
bool load_progress(float, void *) { return !cancelled.load(); }
std::optional<ModelFileIdentity> file_identity(const std::string &path) {
    struct stat info{};
    if (stat(path.c_str(), &info) != 0 || !S_ISREG(info.st_mode))
        return std::nullopt;
    return ModelFileIdentity{info.st_dev, info.st_ino, info.st_size, info.st_mtime,
                             info.st_mtim.tv_nsec};
}
bool same_identity(const ModelFileIdentity &left, const ModelFileIdentity &right) {
    return left.device == right.device && left.inode == right.inode && left.size == right.size &&
           left.modified_seconds == right.modified_seconds &&
           left.modified_nanoseconds == right.modified_nanoseconds;
}
int context_capacity(int prompt_tokens, int requested_output_tokens) {
    constexpr int minimum_context = 512;
    constexpr int maximum_context = 4096;
    const int output_tokens = std::clamp(requested_output_tokens, 0, 768);
    const int required = prompt_tokens + output_tokens;
    int capacity = minimum_context;
    while (capacity < required && capacity < maximum_context)
        capacity *= 2;
    if (capacity < required)
        throw std::runtime_error("CONTEXT_FULL");
    return capacity;
}
std::string utf8(JNIEnv *env, jbyteArray bytes) {
    std::string value(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, value.size(), reinterpret_cast<jbyte *>(value.data()));
    return value;
}
}

extern "C" JNIEXPORT void JNICALL Java_dev_blobs_app_inference_NativeBridge_reset(JNIEnv *,
                                                                                  jobject) {
    cancelled = false;
}

extern "C" JNIEXPORT void JNICALL Java_dev_blobs_app_inference_NativeBridge_stop(JNIEnv *,
                                                                                 jobject) {
    cancelled = true;
}

extern "C" JNIEXPORT void JNICALL Java_dev_blobs_app_inference_NativeBridge_releaseModel(JNIEnv *,
                                                                                         jobject) {
    std::lock_guard<std::mutex> lock(owner);
    clear_context();
    cached_model.reset();
    cached_model_path.clear();
    cached_model_identity.reset();
}

extern "C" JNIEXPORT jint JNICALL Java_dev_blobs_app_inference_NativeBridge_generateMeasured(
    JNIEnv *env, jobject, jbyteArray path, jbyteArray system, jbyteArray user, jint limit,
    jint threads, jobject sink, jlongArray metrics) {
    if (!metrics || env->GetArrayLength(metrics) != 17) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "METRICS_SIZE");
        return 3;
    }
    if (threads < 1 || threads > 4) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "THREAD_COUNT");
        return 3;
    }
    Measurements measured(env, metrics);
    measured.values[12] = threads;
    std::lock_guard<std::mutex> lock(owner);
    measured.values[1] = elapsed_us(measured.start);
    try {
        std::call_once(initialized, [] { llama_backend_init(); });
        if (cancelled)
            return 1;
        const auto model_path = utf8(env, path);
        const auto identity = file_identity(model_path);
        const bool cache_hit = cached_model && identity && cached_model_identity &&
                               cached_model_path == model_path &&
                               same_identity(*identity, *cached_model_identity);
        measured.values[14] = cache_hit ? 1 : 0;
        Model transient_model(nullptr, llama_model_free);
        llama_model *model = nullptr;
        if (cache_hit) {
            measured.values[2] = 0;
            model = cached_model.get();
        } else {
            clear_context();
            cached_model.reset();
            cached_model_path.clear();
            cached_model_identity.reset();
            auto params = llama_model_default_params();
            params.n_gpu_layers = 0;
            params.progress_callback = load_progress;
            Stage loading{measured.values[2]};
            transient_model.reset(llama_model_load_from_file(model_path.c_str(), params));
            loading.stop();
            if (!transient_model) {
                if (cancelled)
                    return 1;
                throw std::runtime_error("MODEL_LOAD");
            }
            if (identity) {
                cached_model = std::move(transient_model);
                cached_model_path = model_path;
                cached_model_identity = identity;
                model = cached_model.get();
            } else {
                model = transient_model.get();
            }
        }
        Stage preparing{measured.values[3]};
        auto sys = utf8(env, system), question = utf8(env, user);
        const char *tmpl = llama_model_chat_template(model, nullptr);
        char architecture[64] = {};
        llama_model_meta_val_str(model, "general.architecture", architecture, sizeof(architecture));
        const auto prepared =
            prepare_chat_prompt(architecture, tmpl, std::move(sys), std::move(question));
        const auto &prompt = prepared.text;
        measured.values[11] = prepared.non_thinking_prefix ? 1 : 0;
        const auto *vocab = llama_model_get_vocab(model);
        int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        if (count <= 0 || count > 3072)
            throw std::runtime_error("CONTEXT_FULL");
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) <
            0)
            throw std::runtime_error("TOKENIZE");
        measured.values[9] = count;
        preparing.stop();
        ContextGuard context_guard{false, identity.has_value()};
        Stage allocating{measured.values[4]};
        auto cp = llama_context_default_params();
        cp.n_ctx = context_capacity(count, limit);
        cp.n_batch = 256;
        cp.n_ubatch = 128;
        cp.n_threads = threads;
        cp.n_threads_batch = threads;
        cp.abort_callback = abort_decode;
        int reused = 0;
        if (cached_context && llama_n_ctx(cached_context.get()) >= cp.n_ctx) {
            // Re-evaluate the last input token to produce logits, even for an identical prompt.
            const int maximum = std::min(count - 1, static_cast<int>(cached_prompt.size()));
            while (reused < maximum && tokens[reused] == cached_prompt[reused])
                ++reused;
            auto memory = llama_get_memory(cached_context.get());
            // Sliding-window/recurrent contexts may no longer hold the required prefix.
            if (!memory || llama_memory_seq_pos_min(memory, 0) != 0 ||
                llama_memory_seq_pos_max(memory, 0) < reused - 1 ||
                !llama_memory_seq_rm(memory, 0, reused, -1)) {
                clear_context();
                reused = 0;
            }
        } else {
            clear_context();
        }
        if (!cached_context) {
            cached_context.reset(llama_init_from_model(model, cp));
            if (!cached_context)
                throw std::runtime_error("CONTEXT_ALLOC");
        }
        auto *context = cached_context.get();
        llama_set_n_threads(context, threads, threads);
        measured.values[13] = llama_n_ctx(context);
        measured.values[15] = reused;
        measured.values[16] = 0;
        using Batch = std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)>;
        Batch batch(llama_batch_ext_init(context), llama_batch_ext_free);
        if (!batch)
            throw std::runtime_error("CONTEXT_ALLOC");
        allocating.stop();
        auto decode = [&](const llama_token *data, int n, int offset) {
            llama_batch_ext_clear(batch.get());
            for (int i = 0; i < n; ++i) {
                int index = llama_batch_ext_add_token(batch.get(), 0, data[i]);
                llama_pos pos = offset + i;
                llama_batch_ext_set_pos(batch.get(), index, &pos);
            }
            llama_batch_ext_set_output_logits(batch.get(), n - 1, true);
            context_guard.healthy = false;
            const int result = llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get());
            context_guard.recoverable_abort = result == 2 && cancelled.load();
            if (result == 0)
                context_guard.healthy = true;
            return result;
        };
        Stage prefill{measured.values[5]};
        for (int pos = reused; pos < count; pos += 256) {
            if (cancelled)
                return 1;
            if (decode(tokens.data() + pos, std::min(256, count - pos), pos)) {
                if (cancelled)
                    return 1;
                throw std::runtime_error("DECODE");
            }
            measured.values[16] += std::min(256, count - pos);
            // A later cancelled prefill must not preserve a partially replaced prompt.
            context_guard.healthy = false;
        }
        cached_prompt = tokens;
        context_guard.healthy = true;
        context_guard.prompt_complete = true;
        prefill.stop();
        using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
        Sampler sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),
                        llama_sampler_free);
        const auto &sampling = prepared.sampling;
        llama_sampler_chain_add(
            sampler.get(),
            llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), sampling.repeat_last_n,
                                         sampling.repeat_penalty, sampling.frequency_penalty,
                                         sampling.presence_penalty));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(sampling.top_k));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(sampling.top_p, 1));
        if (sampling.min_p > 0.0f)
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_min_p(sampling.min_p, 1));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(sampling.temperature));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(sampling.seed));
        auto klass = env->GetObjectClass(sink);
        auto callback = env->GetMethodID(klass, "onToken", "([B)V");
        if (!callback)
            return 3;
        Stage generating{measured.values[6]};
        for (int i = 0; i < std::min(static_cast<int>(limit), 768); ++i) {
            if (cancelled)
                return 1;
            auto token = llama_sampler_sample(sampler.get(), context, -1);
            if (llama_vocab_is_eog(vocab, token))
                return 0;
            std::vector<char> piece(256);
            int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (n < 0) {
                piece.resize(-n);
                n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (n < 0)
                throw std::runtime_error("TOKEN_DECODE");
            auto bytes = env->NewByteArray(n);
            if (!bytes)
                return 3;
            env->SetByteArrayRegion(bytes, 0, n, reinterpret_cast<const jbyte *>(piece.data()));
            if (measured.values[8] < 0)
                measured.values[8] = elapsed_us(measured.start);
            ++measured.values[10];
            const auto callback_start = Clock::now();
            env->CallVoidMethod(sink, callback, bytes);
            measured.values[7] += elapsed_us(callback_start);
            env->DeleteLocalRef(bytes);
            if (env->ExceptionCheck())
                return 3;
            if (cancelled)
                return 1;
            // No next sample needs logits after the final output token.
            if (i + 1 == std::min(static_cast<int>(limit), 768))
                break;
            if (decode(&token, 1, count + i)) {
                if (cancelled)
                    return 1;
                throw std::runtime_error("DECODE");
            }
        }
        return 2;
    } catch (const std::bad_alloc &) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "MEMORY");
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }
    return 3;
}
