#include "dictator_api.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include "transcribe.hpp"
#include "whisper.h"

struct DictatorWhisper {
    struct whisper_context *context = nullptr;
    bool shortenAudioContext = true;
};

namespace {

std::string gLoadLog;

char *copyCString(const std::string &value) {
    char *out = static_cast<char *>(std::malloc(value.size() + 1));
    if (out == nullptr) {
        return nullptr;
    }
    std::memcpy(out, value.c_str(), value.size() + 1);
    return out;
}

void writeError(char *error, int error_length, const char *message) {
    if (error == nullptr || error_length <= 0) {
        return;
    }
    std::snprintf(error, static_cast<size_t>(error_length), "%s", message);
}

void captureLoadLog(enum ggml_log_level level, const char *text, void * /*user_data*/) {
    if (text == nullptr || level < GGML_LOG_LEVEL_WARN) {
        return;
    }
    gLoadLog.append(text);
    if (gLoadLog.size() > 900) {
        gLoadLog.erase(0, gLoadLog.size() - 900);
    }
}

struct whisper_context *initModel(const char *model_path, bool use_gpu) {
    whisper_context_params contextParams = whisper_context_default_params();
    contextParams.use_gpu = use_gpu;
    // Default flash_attn=true aborts in ggml-metal on medium/large for some Macs.
    contextParams.flash_attn = false;
    return whisper_init_from_file_with_params(model_path, contextParams);
}

} // namespace

extern "C" DictatorWhisper *dictator_whisper_create(
    const char *model_path,
    int shorten_audio_ctx,
    char *error,
    int error_length) {
    if (model_path == nullptr || model_path[0] == '\0') {
        writeError(error, error_length, "missing Whisper model path");
        return nullptr;
    }
    gLoadLog.clear();
    whisper_log_set(captureLoadLog, nullptr);
    struct whisper_context *context = initModel(model_path, true);
    if (context == nullptr) {
        context = initModel(model_path, false);
    }
    if (context == nullptr) {
        if (!gLoadLog.empty()) {
            writeError(error, error_length, gLoadLog.c_str());
        } else {
            writeError(error, error_length, "could not load the Whisper model file");
        }
        return nullptr;
    }
    auto *handle = new DictatorWhisper();
    handle->context = context;
    handle->shortenAudioContext = shorten_audio_ctx != 0;
    return handle;
}

extern "C" char *dictator_whisper_transcribe(
    DictatorWhisper *handle,
    const float *pcm16k_mono,
    size_t sample_count,
    int threads,
    const char *language,
    const char *allowed_languages) {
    if (handle == nullptr || handle->context == nullptr || pcm16k_mono == nullptr) {
        return nullptr;
    }
    const std::string text = transcribeDictation(
        handle->context,
        pcm16k_mono,
        sample_count,
        threads,
        language,
        allowed_languages,
        handle->shortenAudioContext);
    return copyCString(text);
}

extern "C" void dictator_whisper_free_string(char *value) {
    std::free(value);
}

extern "C" void dictator_whisper_close(DictatorWhisper *handle) {
    if (handle == nullptr) {
        return;
    }
    if (handle->context != nullptr) {
        whisper_free(handle->context);
        handle->context = nullptr;
    }
    delete handle;
}
