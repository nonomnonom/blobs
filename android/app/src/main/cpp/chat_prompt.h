#pragma once

#include <cstdint>
#include <string>
#include <string_view>

struct SamplingSettings {
    int32_t top_k = 20;
    float top_p = 0.8f;
    float min_p = 0.0f;
    float temperature = 0.3f;
    int32_t repeat_last_n = 256;
    float repeat_penalty = 1.0f;
    float frequency_penalty = 0.0f;
    float presence_penalty = 1.5f;
    uint32_t seed = 42;
};

struct PreparedChatPrompt {
    std::string text;
    bool non_thinking_prefix;
    SamplingSettings sampling;
};

PreparedChatPrompt prepare_chat_prompt(std::string_view architecture, const char *chat_template,
                                      std::string system, std::string user);
