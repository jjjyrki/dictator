# Project documentation

The documentation records the product boundary, technical decisions, model work, and benchmark results for Dictator.

## Sections

- [Specifications](specifications/README.md) contains the product and engineering contract.
- [Reference project review](reference/moshi-android.md) records the relevant findings from `LaurentMazare/moshi-android`.
- [Overlay stub prototype](decisions/2026-09-04-overlay-stub.md) records the temporary Accessibility harness.
- [Rust STT runtime](decisions/2026-09-05-stt-runtime.md) records the superseded Kyutai Q8 attempt and its S23 result.
- [sherpa-onnx runtime](decisions/2026-09-05-sherpa-onnx-runtime.md) records the superseded Zipformer attempt (fast, inaccurate).
- [Whisper runtime](decisions/2026-09-05-whisper-runtime.md) records the current whisper.cpp + FUTO ACFT setup.
- `benchmarks/` will contain reproducible Android performance reports and optional model compatibility fixtures.
- [Tasks](tasks/README.md) holds implementation slices. The macOS menu-bar MVP is [TASK-0001](tasks/TASK-0001-macos-dictation-mvp.md).

## Documentation rules

- Keep the local-only, single-device scope visible in new documents.
- Record the exact model revision, runtime, device, Android version, ABI, and benchmark method for performance claims.
- Do not commit model weights, private recordings, or copied third-party source unless its license and maintenance value justify it.
- Treat accessibility and clipboard data as sensitive. Do not put field contents, dictated audio, or transcripts in logs.
- Update the main specification when a milestone changes a requirement or makes an open decision.
