#include "chat_prompt.h"
#include "nlohmann/json.hpp"

#include <cmath>
#include <fstream>
#include <iostream>
#include <stdexcept>

int main(int argc, char **argv) {
    try {
        if (argc != 2) throw std::runtime_error("Expected fixture JSON path");
        std::ifstream input(argv[1]);
        const auto fixture = nlohmann::json::parse(input);
        auto results = nlohmann::json::array();
        bool passed = true;
        for (const auto &item : fixture.at("cases")) {
            auto result = nlohmann::json{{"case_id", item.at("id")}, {"passed", false}};
            const auto architecture = item.at("architecture").get<std::string>();
            const auto format = item.value("template", "");
            try {
                const auto prompt = prepare_chat_prompt(architecture,
                    item.value("null_template", false) ? nullptr : format.c_str(),
                    item.at("system"), item.at("user"));
                const auto sampling = nlohmann::json{
                    {"top_k", prompt.sampling.top_k},
                    {"top_p", prompt.sampling.top_p},
                    {"min_p", prompt.sampling.min_p},
                    {"temperature", prompt.sampling.temperature},
                    {"repeat_last_n", prompt.sampling.repeat_last_n},
                    {"repeat_penalty", prompt.sampling.repeat_penalty},
                    {"frequency_penalty", prompt.sampling.frequency_penalty},
                    {"presence_penalty", prompt.sampling.presence_penalty},
                    {"seed", prompt.sampling.seed},
                };
                bool sampling_matches = true;
                if (item.contains("sampling")) {
                    const auto &expected = item.at("sampling");
                    for (const auto *key : {"top_k", "repeat_last_n", "seed"})
                        sampling_matches = sampling_matches && sampling.at(key) == expected.at(key);
                    for (const auto *key : {"top_p", "min_p", "temperature", "repeat_penalty",
                                           "frequency_penalty", "presence_penalty"})
                        sampling_matches = sampling_matches &&
                            std::abs(sampling.at(key).get<double>() - expected.at(key).get<double>()) <= 1e-6;
                }
                result["passed"] = !item.contains("error") && prompt.text == item.at("expected") &&
                    prompt.non_thinking_prefix == item.at("non_thinking_prefix") &&
                    sampling_matches;
                result["actual_prompt"] = prompt.text;
                result["non_thinking_prefix"] = prompt.non_thinking_prefix;
                result["sampling"] = sampling;
            } catch (const std::exception &error) {
                result["error"] = error.what();
                result["passed"] = item.contains("error") && item.at("error") == error.what();
            }
            passed = passed && result.at("passed").get<bool>();
            results.push_back(result);
        }
        std::cout << nlohmann::json{{"format", 1}, {"complete", true},
            {"passed", passed}, {"results", results}}.dump(2) << '\n';
        return passed ? 0 : 1;
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 2;
    }
}
