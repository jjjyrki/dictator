// Shared dictation transcription logic used by the Android JNI wrapper,
// the macOS C ABI, and the host compatibility check. Greedy sampling.
// ACFT models pass shortenAudioContext=true so audio_ctx matches clip length.
#pragma once

#include <algorithm>
#include <chrono>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>

#include "whisper.h"

namespace {
struct TranscribeDeadline {
    std::chrono::steady_clock::time_point end;
};

inline bool transcribeTimedOut(void * user) {
    auto * deadline = static_cast<TranscribeDeadline *>(user);
    return std::chrono::steady_clock::now() >= deadline->end;
}
}

inline std::string transcribeDictation(
    struct whisper_context * context,
    const float * pcm16kMono,
    size_t sampleCount,
    int threads,
    const char * language,
    const char * allowedLanguages = nullptr,
    bool shortenAudioContext = true
) {
    if (sampleCount == 0) {
        return std::string();
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    std::string lang = (language != nullptr && language[0] != '\0') ? language : "en";
    if (lang == "auto" && allowedLanguages != nullptr && allowedLanguages[0] != '\0') {
        std::vector<int> allowedIds;
        std::stringstream codes(allowedLanguages);
        std::string code;
        while (std::getline(codes, code, ',')) {
            const int id = whisper_lang_id(code.c_str());
            if (id < 0) return std::string();
            if (std::find(allowedIds.begin(), allowedIds.end(), id) == allowedIds.end()) {
                allowedIds.push_back(id);
            }
        }
        if (allowedIds.empty() || !whisper_is_multilingual(context)) {
            return std::string();
        }

        // whisper.cpp's built-in auto-detection considers every language. Run
        // it once, then restrict the decision to the configured candidates.
        std::vector<float> probabilities(whisper_lang_max_id() + 1, 0.0f);
        if (whisper_pcm_to_mel(
                context,
                pcm16kMono,
                static_cast<int>(sampleCount),
                threads) != 0 ||
            whisper_lang_auto_detect(
                context,
                0,
                threads,
                probabilities.data()) < 0) {
            return std::string();
        }
        const int selectedId = *std::max_element(
            allowedIds.begin(),
            allowedIds.end(),
            [&probabilities](int left, int right) {
                return probabilities[left] < probabilities[right];
            });
        const char * selectedLanguage = whisper_lang_str(selectedId);
        if (selectedLanguage == nullptr) return std::string();
        lang = selectedLanguage;
    }
    params.language = lang.c_str();
    params.n_threads = threads;
    params.no_context = true;
    params.single_segment = true;
    params.max_tokens = 256;
    params.greedy.best_of = 1;
    // ACFT models expect the audio context to match the clip length. Official
    // Whisper ggml files were not trained for that and loop on short clips if
    // audio_ctx is truncated. Leave 0 so whisper.cpp uses the model default.
    if (shortenAudioContext) {
        params.audio_ctx = static_cast<int>(std::min(
            1500.0,
            std::ceil(static_cast<double>(sampleCount) / 320.0) + 32.0));
    }

    const int audioSeconds = static_cast<int>(sampleCount / 16000);
    const int timeoutSeconds = std::max(15, std::min(60, audioSeconds * 8 + 8));
    TranscribeDeadline deadline{
        std::chrono::steady_clock::now() + std::chrono::seconds(timeoutSeconds)
    };
    params.abort_callback = transcribeTimedOut;
    params.abort_callback_user_data = &deadline;

    if (whisper_full(context, params, pcm16kMono, static_cast<int>(sampleCount)) != 0) {
        return std::string();
    }

    std::string text;
    const int segments = whisper_full_n_segments(context);
    for (int i = 0; i < segments; ++i) {
        text += whisper_full_get_segment_text(context, i);
    }
    return text;
}
