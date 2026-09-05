# Reference project review: moshi-android

Status: Reviewed for Dictator Phase 0  
Repository: <https://github.com/LaurentMazare/moshi-android>  
Reviewed snapshot: `3d775dea98b08dd9ea054f23134dd458fbee0507`  
Snapshot date: 2025-01-06

## Purpose

This document records what the existing Android experiment can provide for Dictator. It is a reference implementation, not a drop-in Kyutai STT application.

The most useful parts are its native Android audio path, its Candle-based Mimi calls, its Rust Android entry point, and its small ExecuTorch loading example. The repository does not connect microphone audio, Mimi codes, an ExecuTorch transformer, and text decoding into speech recognition.

## Stack and layout

The reference project is a small Rust Android application built with `cargo-apk` and `eframe`/`egui`.

Relevant files:

- `README.md` contains the build and `cargo-apk` instructions.
- `Cargo.toml` declares Android permissions, native dependencies, and the optional ExecuTorch feature.
- `build.sh` sets Android build flags and runs the release build.
- `src/lib.rs` contains the Android entry point, audio callbacks, Mimi workers, model loading, ExecuTorch experiments, and demo UI.
- `src/utils.rs` contains build metadata.
- `assets/model.pte` is an opaque ExecuTorch smoke-test program.
- `assets/bria.safetensors` contains bundled Mimi code samples for the playback demo.

The project uses:

- Oboe for native Android audio input and output.
- Candle for Mimi.
- JNI and the Android NDK context for permission and app-file access.
- `xctch` for optional ExecuTorch program loading and execution.
- `egui` for the demo UI.

## Microphone capture

`src/lib.rs` defines an `Oboe::AudioInputCallback` implementation that receives mono `f32` frames. The input stream is configured for:

- 24,000 Hz.
- Mono audio.
- `f32` samples.
- Oboe low-latency mode.
- Shared mode.
- 1,920 frames per callback.

The callback copies each frame buffer into an unbounded Rust `mpsc` channel. A worker reshapes each buffer to `(1, 1, pcm_len)` and calls Mimi's `encode_step`.

Useful source areas are `src/lib.rs:35-51` for the callback and `src/lib.rs:183-200` for stream creation.

The audio format is a strong starting point for Dictator, but the exact sample contract still has to be checked against the selected `stt-1b-en_fr` checkpoint. The unbounded channel must not be copied into the production design. Dictator needs bounded buffering, backlog metrics, and an explicit policy for dropped or late frames.

## Mimi on Android

Mimi is loaded through Candle, not ExecuTorch:

```rust
use candle_transformers::models::mimi;
```

`src/lib.rs:319-330` downloads `model.safetensors` from the `kyutai/mimi` Hugging Face repository and caches it under Android's `getFilesDir()`.

The project warms Mimi with a 1,920-sample zero block, decodes the result, and then resets model state. That warm-up and reset sequence may be reusable once the model revision and state contract are confirmed.

The encode worker uses `encode_step` for microphone input. A separate playback path uses `decode_step` with bundled code samples. The reference project therefore demonstrates stateful Mimi execution in both directions, but it does not feed Mimi output into an STT transformer.

The Android entry point sets `RAYON_NUM_THREADS=1` because the author observed worse performance with eight threads on a Pixel 6a. The build also enables ARM NEON and FP16-related flags. These are useful benchmark candidates, not defaults to copy without measurement.

## ExecuTorch integration

ExecuTorch is optional in `Cargo.toml` through the `executorch` feature and the `xctch` dependency. `build.sh` enables the feature for a release build. The Android entry point initializes the ExecuTorch PAL when the feature is enabled.

There are two independent experiments in `src/lib.rs`:

1. A bundled `assets/model.pte` smoke test loads a program, opens the `forward` method, supplies scalar tensor inputs, executes it, and reads an `f32` output.
2. A UI action downloads `lmz/moshi-swift/moshi-lm-300m-q.pte`, opens `forward`, and executes repeated steps with an `i64` tensor shaped `[1, 17, 1]`.

These examples prove that a `.pte` program can be loaded and called from the Android Rust process. They do not establish the input signature, recurrent state, tokenizer, vocabulary, or output semantics of the 300M model. The binary assets are opaque in the repository.

No XNNPACK, Qualcomm QNN, Vulkan, or other hardware delegate setup was found in the inspected source.

## Current transformer experiment

The named transformer-side artifact is `lmz/moshi-swift/moshi-lm-300m-q.pte`. The source calls it a 300M model, but does not document its architecture or show a speech-recognition pipeline.

There is no `stt-1b-en_fr` checkpoint, STT adapter, token decoder, or text vocabulary in this repository. The current experiment must therefore be treated as an ExecuTorch loading example, not as evidence that the Kyutai STT transformer will run unchanged.

## Reusable pieces for Dictator

Likely reusable after license and dependency review:

- Oboe input setup for 24 kHz mono audio.
- Runtime `RECORD_AUDIO` permission request through JNI.
- Rust Android entry point and `cargo-apk` packaging approach.
- App-private model caching through `getFilesDir()`.
- Candle Mimi loading, stateful `encode_step`, warm-up, and reset patterns.
- Basic `xctch` program loading, method invocation, tensor input, and output extraction.
- ARM build flags and single-thread tuning as candidates for the benchmark matrix.

The following needs new work:

- Exporting and loading the Kyutai STT 1B transformer.
- Determining its exact ExecuTorch method signature and recurrent state tensors.
- Mapping Mimi code frames to the STT model's expected input streams.
- Streaming token decoding and text vocabulary handling.
- Handling model delay, finalization, and semantic end of speech.
- Replacing the unbounded audio channel with bounded backpressure.
- Defining cancellation and clean worker shutdown.
- Packaging model assets without requiring a network request during normal dictation.

## Dictator decisions informed by this review

1. Keep Mimi and the transformer behind separate adapters.
2. Reuse the reference audio and Mimi path where it matches the selected model contract.
3. Use ExecuTorch for the transformer only after a correct model export and input/output fixture exist.
4. Treat the reference 300M artifact as a smoke-test aid. Do not use it as a substitute for `stt-1b-en_fr`.
5. Benchmark the reference threading and ARM settings on the actual target phone instead of assuming the Pixel 6a observation applies.
6. Start the Android speech prototype directly. Do not create a separate desktop baseline as a project gate.

## Remaining questions

- What exact Mimi revision, codebook layout, and PCM contract does `stt-1b-en_fr` require?
- What state and input tensors does the STT transformer need for streaming inference?
- Can the transformer export run through the same `xctch`/ExecuTorch integration, or does it require new native bindings?
- Which operators and delegates are available on the target Android device?
- Can the model assets be installed once through setup and then used with the network blocked?
- Which Android SDK and NDK versions are compatible with the reference code and the target phone?

These questions belong in the Android prototype and model-conversion work. None requires building the overlay first.
