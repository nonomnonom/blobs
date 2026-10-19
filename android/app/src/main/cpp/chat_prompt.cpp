#include "chat_prompt.h"
#include "llama.h"
#include "qwen35_2b_template.inc"
#include "qwen35_template.inc"

#include <stdexcept>
#include <vector>

namespace {
void trim_edges(std::string &text) {
    constexpr auto whitespace = " \t\r\n\v\f";
    const auto first = text.find_first_not_of(whitespace);
    if (first == std::string::npos) {
        text.clear();
        return;
    }
    const auto last = text.find_last_not_of(whitespace);
    text = text.substr(first, last - first + 1);
}
}

PreparedChatPrompt prepare_chat_prompt(std::string_view architecture, const char *chat_template,
                                       std::string system, std::string user) {
    if (!chat_template)
        throw std::runtime_error("MODEL_TEMPLATE");
    const std::string_view format(chat_template);
    const bool qwen35 = architecture == "qwen35";
    const bool gemma4 = architecture == "gemma4";
    if (qwen35 && format != qwen35_text_template && format != qwen35_2b_text_template)
        throw std::runtime_error("MODEL_TEMPLATE");
    SamplingSettings sampling;
    if (qwen35 && format == qwen35_2b_text_template) {
        sampling.top_p = 1.0f;
        sampling.temperature = 1.0f;
        sampling.presence_penalty = 2.0f;
    }
    for (size_t pos = 0; (pos = user.find("<|", pos)) != std::string::npos; pos += 3)
        user.replace(pos, 2, "< |");
    if (qwen35) {
        trim_edges(system);
        trim_edges(user);
    }
    if (gemma4) {
        trim_edges(system);
        trim_edges(user);
        std::string prompt = "<|turn>system\n" + system + "<turn|>\n";
        prompt += "<|turn>user\n" + user + "<turn|>\n<|turn>model\n";
        return {std::move(prompt), false, sampling};
    }
    const llama_chat_message messages[] = {{"system", system.c_str()}, {"user", user.c_str()}};
    const int length = llama_chat_apply_template(chat_template, messages, 2, true, nullptr, 0);
    if (length <= 0 || length > 200000)
        throw std::runtime_error("MODEL_TEMPLATE");
    std::vector<char> formatted(length + 1);
    if (llama_chat_apply_template(chat_template, messages, 2, true, formatted.data(),
                                  formatted.size()) != length)
        throw std::runtime_error("MODEL_TEMPLATE");
    std::string prompt(formatted.data(), length);
    // Instruct-2507 shares Qwen3's architecture but has no switchable thinking branch.
    const bool qwen3 = (architecture == "qwen3" || architecture == "qwen3moe") &&
                       format.find("enable_thinking") != std::string_view::npos &&
                       format.find("<think>\\n\\n</think>\\n\\n") != std::string_view::npos;
    if (qwen3 || qwen35)
        prompt += "<think>\n\n</think>\n\n";
    return {std::move(prompt), qwen3 || qwen35, sampling};
}
