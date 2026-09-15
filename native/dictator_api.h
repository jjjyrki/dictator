// C ABI for the shared dictation transcription path (`transcribe.hpp`).
#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct DictatorWhisper DictatorWhisper;

DictatorWhisper *dictator_whisper_create(
    const char *model_path,
    int shorten_audio_ctx,
    char *error,
    int error_length);

char *dictator_whisper_transcribe(
    DictatorWhisper *handle,
    const float *pcm16k_mono,
    size_t sample_count,
    int threads,
    const char *language,
    const char *allowed_languages);

void dictator_whisper_free_string(char *value);

void dictator_whisper_close(DictatorWhisper *handle);

#ifdef __cplusplus
}
#endif
