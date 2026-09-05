# Rust STT runtime and Q8 model bundle

Date: 2026-09-05
Superseded: 2026-09-05. The Kyutai Q8 bundle reached RTF 2.24 on the Galaxy S23, far above the real-time gate. Replaced by sherpa-onnx with a streaming English Zipformer int8 model (about 73 MB). See [the sherpa-onnx decision](2026-09-05-sherpa-onnx-runtime.md).

Dictator transcribes speech only. It has no TTS or spoken output.

## Decision

The first model-backed Android build uses a Rust `cdylib` with JNI, Candle, Kyutai's `moshi::asr::State`, and SentencePiece. Kotlin captures 24 kHz mono PCM, uses a bounded eight-frame queue, and passes 1,920-sample frames to Rust. The runtime keeps Mimi and the language model loaded between recording sessions.

The target is a Samsung Galaxy S23 on `arm64-v8a`. The first test stays in the main activity. It must produce a real transcript and report real-time factor before the Accessibility overlay uses this engine.

The initial model bundle is Q8, not Q4:

- Quantized language model: `stephvax/kyutai-stt-1b-en_fr-candle-gguf`, revision `177aac749e0afc58d20dc5ec20814ef7b766a1d2`, `model.q8_0.gguf`, 1,051,290,688 bytes, SHA-256 `7bbceaf823610ba1d33bc6b1218105ade3ebd02177a3234f3950e0ca5e7c5c0c`.
- Official config, tokenizer, and Mimi: `kyutai/stt-1b-en_fr-candle`, revision `095e38f6242006a93c2541149b181988397f5c7c`.
- Mimi SHA-256: `09b782f0629851a271227fb9d36db65c041790365f11bbe5d3d59369cf863f50`.
- Tokenizer SHA-256: `cd87dd5d17169151782ac700280ec057e5d658a9afbe238a048ea5ff318cce69`.
- Config SHA-256: `a3f1c6f7a39fca1fb1bbff68eaabc560b8037d2cdc68aa1f489859949a4223de`.

The app downloads the four files into app-private storage, verifies byte count and SHA-256, then loads them offline. The APK does not contain model weights or recordings.

## Why Q8

The smaller `cstr/kyutai-stt-1b-GGUF` Q4 model targets `moshi.cpp` and is incompatible with Candle's tensor names. It fails to load because Candle cannot find `text_emb.weight`.

The Candle-compatible Q4 and Q8 conversions from `stephvax` preserve the official tensor layout. Their published CPU measurements show Q4 does not improve speed, while Q8 has near-identical quality and materially better throughput. The Q8 LM plus Mimi needs about 1.44 GB installed. Leave at least 3 GB free while installing. Use Q4 only if the S23 cannot retain Q8 in memory.

## Validation

The pinned Q8 file passed SHA-256 verification, loaded through the Rust Candle/Moshi runtime, and transcribed Kyutai's `audio/bria.mp3` fixture after conversion to 24 kHz mono PCM. This checked model compatibility only. It did not benchmark desktop performance.

## Build

`native/build-android.sh` builds `libdictator_stt.so` for `arm64-v8a`, strips it with the Android NDK, and packages `libc++_shared.so`, which SentencePiece requires. The native project vendors one build-only patch to `sentencepiece-sys` so its Android shared library links `liblog`.

Kyutai's STT weights and the derivative Q8 weights are CC-BY 4.0. Preserve their attribution if the project is distributed.
