#include <jni.h>
#include <algorithm>
#include <atomic>
#include <array>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <stdexcept>
#include "llama.h"
#include "chat_prompt.h"

namespace {
std::atomic<bool> cancelled{false};
std::mutex owner;
std::once_flag initialized;
using Clock = std::chrono::steady_clock;
jlong elapsed_us(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - start).count();
}
struct Stage {
    jlong &value;
    Clock::time_point start = Clock::now();
    bool running = true;
    void stop() { if (running) { value = elapsed_us(start); running = false; } }
    ~Stage() { stop(); }
};
struct Measurements {
    JNIEnv *env;
    jlongArray output;
    Clock::time_point start = Clock::now();
    std::array<jlong, 13> values;
    Measurements(JNIEnv *env, jlongArray output) : env(env), output(output) {
        values.fill(-1);
        values[7] = values[10] = values[11] = 0;
    }
    ~Measurements() {
        values[0] = elapsed_us(start);
        // Keep diagnostic timings on failure without replacing the original JNI exception.
        auto error = env->ExceptionOccurred();
        if (error) env->ExceptionClear();
        env->SetLongArrayRegion(output, 0, values.size(), values.data());
        if (error) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->Throw(error);
            env->DeleteLocalRef(error);
        }
    }
};
bool abort_decode(void *) { return cancelled.load(); }
bool load_progress(float, void *) { return !cancelled.load(); }
std::string utf8(JNIEnv *env, jbyteArray bytes) {
    std::string value(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, value.size(), reinterpret_cast<jbyte *>(value.data()));
    return value;
}
}

extern "C" JNIEXPORT void JNICALL
Java_dev_blobs_app_inference_NativeBridge_reset(JNIEnv *, jobject) { cancelled = false; }

extern "C" JNIEXPORT void JNICALL
Java_dev_blobs_app_inference_NativeBridge_stop(JNIEnv *, jobject) { cancelled = true; }

extern "C" JNIEXPORT jint JNICALL
Java_dev_blobs_app_inference_NativeBridge_generateMeasured(
    JNIEnv *env, jobject, jbyteArray path, jbyteArray system, jbyteArray user, jint limit, jint threads, jobject sink,
    jlongArray metrics) {
    if (!metrics || env->GetArrayLength(metrics) != 13) {
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
        if (cancelled) return 1;
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.progress_callback = load_progress;
        using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
        Stage loading{measured.values[2]};
        Model model(llama_model_load_from_file(utf8(env, path).c_str(), params), llama_model_free);
        loading.stop();
        if (!model) {
            if (cancelled) return 1;
            throw std::runtime_error("MODEL_LOAD");
        }
        Stage preparing{measured.values[3]};
        auto sys = utf8(env, system), question = utf8(env, user);
        const char *tmpl = llama_model_chat_template(model.get(), nullptr);
        char architecture[64] = {};
        llama_model_meta_val_str(model.get(), "general.architecture", architecture, sizeof(architecture));
        const auto prepared = prepare_chat_prompt(architecture, tmpl, std::move(sys), std::move(question));
        const auto &prompt = prepared.text;
        measured.values[11] = prepared.non_thinking_prefix ? 1 : 0;
        const auto *vocab = llama_model_get_vocab(model.get());
        int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        if (count <= 0 || count > 3072) throw std::runtime_error("CONTEXT_FULL");
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) < 0)
            throw std::runtime_error("TOKENIZE");
        measured.values[9] = count;
        preparing.stop();
        Stage allocating{measured.values[4]};
        auto cp = llama_context_default_params();
        cp.n_ctx = 4096; cp.n_batch = 256; cp.n_ubatch = 128;
        cp.n_threads = threads; cp.n_threads_batch = threads;
        cp.abort_callback = abort_decode;
        using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
        Context context(llama_init_from_model(model.get(), cp), llama_free);
        if (!context) throw std::runtime_error("CONTEXT_ALLOC");
        using Batch = std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)>;
        Batch batch(llama_batch_ext_init(context.get()), llama_batch_ext_free);
        if (!batch) throw std::runtime_error("CONTEXT_ALLOC");
        allocating.stop();
        auto decode = [&](const llama_token *data, int n, int offset) {
            llama_batch_ext_clear(batch.get());
            for (int i = 0; i < n; ++i) {
                int index = llama_batch_ext_add_token(batch.get(), 0, data[i]);
                llama_pos pos = offset + i;
                llama_batch_ext_set_pos(batch.get(), index, &pos);
            }
            llama_batch_ext_set_output_logits(batch.get(), n-1, true);
            return llama_process(context.get(), LLAMA_PROCESS_TYPE_DECODE, batch.get());
        };
        Stage prefill{measured.values[5]};
        for (int pos = 0; pos < count; pos += 256) {
            if (cancelled) return 1;
            if (decode(tokens.data()+pos, std::min(256, count-pos), pos)) {
                if (cancelled) return 1;
                throw std::runtime_error("DECODE");
            }
        }
        prefill.stop();
        using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
        Sampler sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        const auto &sampling = prepared.sampling;
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(
            llama_vocab_n_tokens(vocab), sampling.repeat_last_n, sampling.repeat_penalty,
            sampling.frequency_penalty, sampling.presence_penalty));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(sampling.top_k));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(sampling.top_p, 1));
        if (sampling.min_p > 0.0f)
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_min_p(sampling.min_p, 1));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(sampling.temperature));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(sampling.seed));
        auto klass = env->GetObjectClass(sink);
        auto callback = env->GetMethodID(klass, "onToken", "([B)V");
        if (!callback) return 3;
        Stage generating{measured.values[6]};
        for (int i = 0; i < std::min(static_cast<int>(limit), 768); ++i) {
            if (cancelled) return 1;
            auto token = llama_sampler_sample(sampler.get(), context.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) return 0;
            std::vector<char> piece(256);
            int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (n < 0) {
                piece.resize(-n);
                n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (n < 0) throw std::runtime_error("TOKEN_DECODE");
            auto bytes = env->NewByteArray(n);
            if (!bytes) return 3;
            env->SetByteArrayRegion(bytes, 0, n, reinterpret_cast<const jbyte *>(piece.data()));
            if (measured.values[8] < 0) measured.values[8] = elapsed_us(measured.start);
            ++measured.values[10];
            const auto callback_start = Clock::now();
            env->CallVoidMethod(sink, callback, bytes);
            measured.values[7] += elapsed_us(callback_start);
            env->DeleteLocalRef(bytes);
            if (env->ExceptionCheck()) return 3;
            if (decode(&token, 1, count+i)) {
                if (cancelled) return 1;
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
