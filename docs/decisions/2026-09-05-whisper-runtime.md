# whisper.cpp runtime with FUTO ACFT models

Date: 2026-09-05

Supersedes [the sherpa-onnx decision](2026-09-05-sherpa-onnx-runtime.md).

## Context

The streaming Zipformer reached RTF 0.1 on the Galaxy S23 but produced disappointing transcripts and no punctuation. FUTO Voice Input demonstrated the quality bar: whisper.cpp with Whisper models fine-tuned through their ACFT method, running fully offline. FUTO's app code is Source First licensed, so we reuse their approach and pinned models, not their code.

## Decision

Runtime: vendored whisper.cpp v1.9.3. Android builds a CPU-only `libdictator_whisper.so` through `native/build-android.sh` (`-DGGML_METAL=OFF`) with JNI in `native/dictator_jni.cpp`. macOS builds `libdictator_api.dylib` through `native/build-macos.sh` with ggml-metal and an embedded Metal library. Both call the same `native/transcribe.hpp` path. The host fixture binary remains a compatibility check, not a device benchmark.

Engine flow: audio accumulates during the session; `finish()` transcribes the whole clip with greedy sampling and FUTO's ACFT audio-context rule `audio_ctx = min(1500, ceil(samples / 320) + 32)`. English-only models use `language = "en"`. Multilingual models can auto-detect from a user-selected allow-list of up to four languages from Whisper's supported language catalog, and then transcribe with the selected language.

Model: FUTO ACFT fine-tuned Whisper GGML files, all verified against FUTO's own pinned digests and downloadable from `https://voiceinput.futo.org/VoiceInput/`. The app offers a selector:

- `tiny_en_acft_q8_0.bin` (English-39, fastest): 43,550,795 bytes, SHA-256 `4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc`.
- `base_en_acft_q8_0.bin` (English-74, balanced): 81,781,811 bytes, SHA-256 `e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8`.
- `small_en_acft_q8_0.bin` (English-244, most accurate): 264,477,561 bytes, SHA-256 `58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8`.
- Multilingual ACFT files are also available as `tiny_acft_q8_0.bin`, `base_acft_q8_0.bin`, and `small_acft_q8_0.bin`; the app verifies their pinned digests in `SttModelVariant.kt`.
- macOS also offers official Hugging Face ggml Q8 files that FUTO does not host: `ggml-medium-q8_0.bin` (823,369,779 bytes, SHA-256 `42a1ffcbe4167d224232443396968db4d02d4e8e87e213d3ee2e03095dea6502`) and `ggml-large-v3-turbo-q8_0.bin` (874,188,075 bytes, SHA-256 `317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1`). Those use the model default `audio_ctx` because they were not ACFT-trained. Android does not list them.

Apache-2.0. The base model transcribed Kyutai's `bria` fixture on the host through the shared transcription path with correct punctuation; the small model did the same with cleaner tail behavior.

## Validation

The pinned model transcribed Kyutai's `bria` fixture on the host through the shared transcription path with correct punctuation and near-identical wording to the 1B Kyutai model. Host runtime is not a device benchmark.

## Consequences

- Transcription happens once at stop; long sessions delay the final text by roughly RTF times the clip length. Partials are a follow-up if needed.
- The multilingual language detector compares only the selected languages, up to four, instead of allowing every Whisper language. Detection adds an encoder pass before transcription and needs device benchmarking.
- A transcription call selects one language for the whole clip. Sentence-level code-switching remains a follow-up requiring segmentation or per-segment detection.
- Thread count (default 4) is the first device tuning knob; the small ACFT model remains the quality upgrade path on the phone. macOS can also load official medium Q8 and large-v3-turbo Q8.
- Android stays CPU-only. macOS turns on ggml-metal and embeds the shader library so the dylib does not need a sidecar `.metallib`.
- Official whisper.cpp ggml files on Mac leave `audio_ctx` at the model default. ACFT files still use FUTO's clip-length formula.
