# Project documentation

The documentation records the product boundary, technical decisions, model work, and benchmark results for Dictator.

## Sections

- [Specifications](specifications/README.md) contains the product and engineering contract.
- [Reference project review](reference/moshi-android.md) records the relevant findings from `LaurentMazare/moshi-android`.
- [Overlay stub prototype](decisions/2026-09-04-overlay-stub.md) records the temporary Accessibility harness.
- [Rust STT runtime](decisions/2026-09-05-stt-runtime.md) records the S23 runtime, model files, and Q8 decision.
- `benchmarks/` will contain reproducible Android performance reports and optional model compatibility fixtures.
- `tasks/` may contain implementation slices after the feasibility gates in the main specification pass.

## Documentation rules

- Keep the local-only, single-device scope visible in new documents.
- Record the exact model revision, runtime, device, Android version, ABI, and benchmark method for performance claims.
- Do not commit model weights, private recordings, or copied third-party source unless its license and maintenance value justify it.
- Treat accessibility and clipboard data as sensitive. Do not put field contents, dictated audio, or transcripts in logs.
- Update the main specification when a milestone changes a requirement or makes an open decision.
