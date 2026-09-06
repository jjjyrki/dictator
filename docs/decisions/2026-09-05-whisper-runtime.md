# whisper.cpp runtime with FUTO ACFT models

Date: 2026-09-05

Supersedes [the sherpa-onnx decision](2026-09-05-sherpa-onnx-runtime.md).

## Context

The streaming Zipformer reached RTF 0.1 on the Galaxy S23 but produced disappointing transcripts and no punctuation. FUTO Voice Input demonstrated the quality bar: whisper.cpp with Whisper models fine-tuned through their ACFT method, running fully offline. FUTO's app code is Source First licensed, so we reuse their approach and pinned models, not their code.

## Decision

Runtime: vendored whisper.cpp v1.9.3 (CPU-only ggml subset) built as `libdictator_whisper.so` through `native/build-android.sh` with the NDK CMake toolchain. A small JNI layer (`native/dictator_jni.cpp`) exposes create/transcribe/close. The dictation transcription logic (`native/transcribe.hpp`) is shared with a host compatibility binary.

Engine flow: audio accumulates during the session; `finish()` transcribes the whole clip with greedy sampling and FUTO's ACFT audio-context rule `audio_ctx = min(1500, ceil(samples / 320) + 32)`. English-only models use `language = "en"`. Multilingual models can auto-detect from a user-selected allow-list, currently English and Finnish, and then transcribe with the selected language.

Model: FUTO ACFT fine-tuned Whisper GGML files, all verified against FUTO's own pinned digests and downloadable from `https://voiceinput.futo.org/VoiceInput/`. The app offers a selector:

- `tiny_en_acft_q8_0.bin` (English-39, fastest): 43,550,795 bytes, SHA-256 `4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc`.
- `base_en_acft_q8_0.bin` (English-74, balanced): 81,781,811 bytes, SHA-256 `e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8`.
- `small_en_acft_q8_0.bin` (English-244, most accurate): 264,477,561 bytes, SHA-256 `58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8`.
- Multilingual ACFT files are also available as `tiny_acft_q8_0.bin`, `base_acft_q8_0.bin`, and `small_acft_q8_0.bin`; the app verifies their pinned digests in `SttModelVariant.kt`.

Apache-2.0. The base model transcribed Kyutai's `bria` fixture on the host through the shared transcription path with correct punctuation; the small model did the same with cleaner tail behavior.

## Validation

The pinned model transcribed Kyutai's `bria` fixture on the host through the shared transcription path with correct punctuation and near-identical wording to the 1B Kyutai model. Host runtime is not a device benchmark.

## Consequences

- Transcription happens once at stop; long sessions delay the final text by roughly RTF times the clip length. Partials are a follow-up if needed.
- The multilingual language detector compares only the selected languages instead of allowing every Whisper language. Detection adds an encoder pass before transcription and needs device benchmarking.
- A transcription call selects one language for the whole clip. Sentence-level code-switching remains a follow-up requiring segmentation or per-segment detection.
- Thread count (default 4) is the first device tuning knob; the small model remains the quality upgrade path if RTF allows.
