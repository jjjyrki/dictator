id: TASK-0001
status: in_progress
summary: macOS menu-bar dictation MVP with Fn hold/latch, Metal whisper.cpp, and focused-field insert

# Goal

Ship a personal macOS Dictator that reuses Android's FUTO ACFT Whisper path and inserts into the focused editor. Fn is the only trigger.

# Problem

The Android app is usable. There is no Mac port. Desktop was previously skipped in the product spec.

# Scope

In scope:

- Menu bar extra plus a recording HUD. No caret-following bubble.
- Fn hold = push-to-talk. Fn double-press = latch until Fn again.
- Record on the first Fn down. A lone short tap cancels with no insert.
- Accessibility insert at the caret. Paste is fallback. If there is no editable focus at start, HUD error and no recording. If the target is gone at stop, HUD error and no insert (no clipboard dump).
- Same FUTO ACFT model picker as Android (tiny/base/small, English-only or multilingual), plus Mac-only official whisper.cpp `medium-q8_0` and `large-v3-turbo-q8_0`. Official ggml files do not use FUTO's shortened `audio_ctx`. Multilingual allow-list up to four languages, default English + Finnish. Default model stays multilingual Small ACFT.
- MERGE insert by default. REPLACE is a settings toggle.
- Live partials off by default, same opt-in as Android.
- Vendored whisper.cpp with Metal on Mac only. Android stays CPU-only JNI.
- First-run copy: turn off Keyboard → Dictation and set Globe/Fn to Do Nothing. Dictator owns Fn through a CGEvent tap (Accessibility + Input Monitoring).
- Local model download with FUTO URLs for ACFT files and Hugging Face `ggerganov/whisper.cpp` for the official Mac-only Q8 files, both SHA-256 pinned.

Out of scope:

- iOS
- Accounts, cloud, analytics
- Replacing the system keyboard / Input Method Kit
- Launch at login
- Caret-adjacent bubble
- Core ML encoder
- Sharing Fn with Apple dictation

# Functional requirements

## 1) Trigger

- Hold past 250ms is push-to-talk. Release commits.
- Press-release under 250ms starts a 300ms window. A second Fn latches. Window expiry cancels.
- Latch stops on the next Fn press.
- Swallow Fn so Globe/dictation does not also fire.

## 2) Session

- Capture 16 kHz mono float. Transcribe the whole clip on commit through `transcribe.hpp`.
- HUD shows recording, processing, mic level, optional partials, and errors.
- Empty transcript is not an error. Return to idle with no insert.

## 3) Insert

- Snapshot the focused AX element at start. MERGE uses `InsertionText` at the selection. REPLACE writes the whole value.
- Paste (temporary clipboard, restore after) only if AX set-value fails to change the field. HUD says Copied if paste also misses so you can Cmd+V.
- Do not treat a successful AX return as insert unless the field text actually changed.

## 4) Settings

- Menu bar opens a setup window: permissions, model download/load, languages, MERGE/REPLACE, partials, Apple-dictation instructions.

# Acceptance criteria

- [ ] Holding Fn records and releasing inserts at the caret in TextEdit.
- [ ] Double-press Fn latches, a later Fn press stops and inserts.
- [ ] A single quick Fn tap records nothing lasting and inserts nothing.
- [ ] Fn with no editable focus shows a HUD error and does not start the mic.
- [ ] English and Finnish work with multilingual Small and the default allow-list.
- [ ] Mac picker lists official medium Q8 and large-v3-turbo Q8; those downloads come from Hugging Face and do not use ACFT `audio_ctx`.
- [ ] Mac cmake/build uses Metal. `native/build-android.sh` still passes `-DGGML_METAL=OFF`.
- [ ] DictatorCore unit tests cover Fn gestures and insertion text.
- [ ] Audio and transcripts stay on-device.

# Testing expectations

- `swift test --package-path macos` for gesture and insertion logic.
- `native/build-macos.sh` produces `libdictator_api.dylib`.
- Manual: TextEdit MERGE, a placeholder-y field with REPLACE, Chrome/Safari composer, no-focus HUD error, permission denial copy.

# Risks and mitigations

- Risk: Globe/Fn still reaches Apple dictation on some OS builds.
  - Mitigation: first-run instructions plus event-tap swallow. If Fn cannot be stolen, document it. Do not silently bind Right Option in this task.
- Risk: AX set-value is rejected in Electron/Chrome.
  - Mitigation: paste fallback with pasteboard restore. Fail visible in HUD if both miss.
- Risk: Metal shader / ggml backend mismatch.
  - Mitigation: embed the Metal library (`GGML_METAL_EMBED_LIBRARY=ON`). Keep `transcribe.hpp` shared.

# Follow-ups

- Launch at login
- Right Option fallback if Fn cannot be claimed
- Streaming encode instead of full-clip `whisper_full`
- Caret HUD
