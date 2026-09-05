# sherpa-onnx Zipformer runtime

Date: 2026-09-05
Superseded: 2026-09-05. The Zipformer transducer was fast (RTF 0.1 on the S23) but its accuracy and lack of punctuation disappointed on the first device test. Replaced by whisper.cpp with FUTO ACFT models. See [the whisper decision](2026-09-05-whisper-runtime.md).

Supersedes [the Rust/Candle Kyutai decision](2026-09-05-stt-runtime.md).

## Context

The Kyutai `stt-1b-en_fr` Q8 bundle measured RTF 2.24 on the Galaxy S23 with dropped frames: unusable for live dictation. The owner asked for a small model in the 100 to 200 MB class that runs much faster. A true-streaming architecture is also required; Whisper's fixed 30-second window re-processes overlapping audio and accumulates latency.

## Decision

Use sherpa-onnx (official JitPack AAR `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5`) with the English streaming Zipformer transducer:

- Model: `csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26`, revision `672fbf1b30579d6585301139bb363f42a0ad4a24`. Encoder is the `chunk-16-left-128` int8 file; decoder and joiner stay float (`left-128`), matching the official demo configuration. Quantizing the decoder/joiner measurably degrades accuracy on the first device test.
- Files pinned with SHA-256 in `SttModelInstaller`: encoder (71,083,163 bytes), decoder (2,092,621), joiner (1,026,405), `tokens.txt` (5,048). Total about 74 MB.
- License: Apache-2.0 for both runtime and model.
- Audio: 16 kHz mono PCM16 captured in 1,600-sample (100 ms) frames.
- Kotlin API: persistent `OnlineRecognizer`, per-session `OnlineStream`, `isReady`/`decode` loop, `inputFinished()` before the final result.
- CPU provider, 4 threads initially; thread count is the first tuning knob.

The Rust/Candle/Moshi native module, its JNI bridge, and the vendored sentencepiece-sys patch were removed.

## Consequences

- English only. Kyutai's bilingual en/fr and punctuation/capitalization quality are gone; text comes out unpunctuated.
- If S23 CPU still misses the RTF gate, options in order: thread-count sweep, QNN/HTP provider (SoC-specific artifacts), or a different model.
- Partial results may revise text; only the final result is inserted.
