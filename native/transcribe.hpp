// Shared dictation transcription logic used by both the Android JNI wrapper
// and the host compatibility check. It follows FUTO Voice Input's ACFT recipe:
// greedy sampling with a shortened audio context sized to the clip.
#pragma once

#include <cmath>
#include <string>
#include <vector>

#include "whisper.h"

inline std::string transcribeDictation(
    struct whisper_context * context,
    const float * pcm16kMono,
    size_t sampleCount,
    int threads,
    const char * language
) {
    if (sampleCount == 0) {
        return std::string();
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    params.beam_search.beam_size = 5;
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    const std::string lang = (language != nullptr && language[0] != '\0') ? language : "en";
    params.language = lang.c_str();
    params.n_threads = threads;
    // ACFT models expect the audio context to match the clip length. The same
    // formula is used by FUTO Voice Input's voiceinput.cpp.
    params.audio_ctx = static_cast<int>(std::min(
        1500.0,
        std::ceil(static_cast<double>(sampleCount) / 320.0) + 32.0));

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
