# Local Android dictation assistant

Status: Draft  
Project: Dictator  
Audience: The owner and future implementers of this personal project  
Last updated: 2026-09-04

## 1. Purpose

Dictator is a personal Android dictation assistant inspired by the core interaction of Wispr Flow. It shows a small microphone bubble when an editable text field has focus, transcribes speech locally on the phone, and inserts the result at the cursor.

The app must work alongside the existing keyboard. It must not implement an input method or ask the owner to replace Samsung Keyboard or another installed keyboard.

This is a sideloaded application for one device. It does not need accounts, a cloud service, analytics, multi-user support, commercial onboarding, or Play Store publishing infrastructure.

The project starts with speech inference, not Android polish. The first technical gate is live, English, on-device transcription with Kyutai's `stt-1b-en_fr` model at faster than real-time speed on the target phone.

There is a temporary sideload shortcut in `app/`: an Accessibility overlay that inserts canned text. It exists so the bubble and insertion path can be tried on a phone before STT is ready. It does not satisfy the RTF gate. See `docs/decisions/2026-09-04-overlay-stub.md`.

## 2. Goals

The project should achieve these goals in this order:

1. Inspect the Android reference implementation and define the smallest reusable path for Kyutai STT.
2. Run `stt-1b-en_fr` on the target Android device with live microphone input.
3. Confirm that the model produces useful English dictation on the target device.
4. Keep Android inference ahead of the speaker so a sustained dictation session does not build a backlog.
5. Insert the final transcript into the focused field without losing existing text or changing the active keyboard.
6. Keep the runtime local. Audio and transcripts must not leave the device.
7. Keep the Android UI small enough that the owner can use the tool without learning a new workflow.

### Success gates

The following gates control the order of work:

- The Android speech prototype produces usable English dictation for ordinary short and long utterances on the target device.
- The Android speech prototype has a steady-state real-time factor below `1.0` on the target device. A lower value is preferred. The prototype must not be integrated with Accessibility or an overlay before this gate passes.
- The integrated app can complete the following flow:
  1. Open WhatsApp.
  2. Focus the message field.
  3. See the microphone bubble.
  4. Tap the bubble and speak, "Can you send me the document tomorrow morning?"
  5. Tap stop.
  6. See the text inserted at the cursor.
  7. Continue using Samsung Keyboard without changing the keyboard.
- A model or runtime failure produces a visible, recoverable error instead of silently losing the transcript.
- Normal runtime operation makes no network request and does not persist microphone recordings or transcript history.

The performance gate is the hard gate. A good overlay cannot compensate for a speech engine that falls behind.

## 3. Product boundary

### In scope for the first usable version

- English dictation using Kyutai `stt-1b-en_fr`.
- Local Mimi audio encoding and Kyutai streaming STT inference.
- A minimal setup and diagnostics Activity.
- Microphone capture from a foreground service.
- A floating microphone bubble shown for usable editable fields.
- Tap-to-start and tap-to-stop dictation.
- Partial transcript display while recording when the backend provides partials.
- Final transcript insertion at the current cursor or selection.
- Clipboard and paste fallback when direct insertion fails.
- Local error feedback and a foreground microphone notification.
- Android benchmark reports and fixed model compatibility fixtures.

### Explicitly out of scope initially

- A custom Android keyboard or `InputMethodService`.
- Cloud speech recognition, cloud storage, accounts, synchronization, or a web application.
- iOS support.
- Finnish speech recognition.
- AI rewriting, tone changes, grammar rewriting, or LLM post-processing.
- Transcript history, search, team features, analytics, or telemetry.
- Play Store publishing and commercial-grade onboarding.
- The larger 2.6B model.
- Push-to-talk as a first interaction mode.
- Automatic end-of-speech stopping as a first interaction mode.
- Custom vocabulary and word correction.
- Multi-device or multi-user behavior.

The following may be added only after the tap-based workflow is reliable: push-to-talk, a movable and remembered bubble position, vibration or sound feedback, automatic spacing and capitalization, cancel gestures, undo, model warm-up, keeping the model loaded, semantic end-of-speech stopping, partial transcript refinement, and custom vocabulary.

## 4. Users and assumptions

The only user is the owner of the Android device. The owner can grant microphone, Accessibility, and any required overlay permissions and can sideload an APK.

The first overlay APK targets Android 14 (API 34) and `arm64-v8a`. Chipset, RAM, and storage still belong in the first speech benchmark report. SDK min and target are 34 for this personal device build.

The initial language requirement is English. The selected checkpoint is `stt-1b-en_fr`, even though French support is not required for the first version.

The owner accepts the security implications of an Accessibility Service on a personal device. The app still has to handle that privilege narrowly and must not log screen contents, field contents, dictated audio, or transcripts.

## 5. User experience

### 5.1 Setup

`MainActivity` is the setup and diagnostics entry point. It should show:

- Whether microphone permission is granted.
- Whether the Accessibility Service is enabled.
- Whether a required overlay permission is granted.
- Whether the model assets are installed and pass their checksum.
- Which inference backend and model revision are active.
- A small set of recent diagnostic timings and error messages.
- Links to the relevant Android settings screens.
- Start and stop controls for the speech prototype before the overlay exists.

There is no account creation, sign-in, tutorial carousel, or required network service. If model assets are installed by a script or copied manually, the Activity must explain where they belong and how the checksum is verified.

The app must refuse to start recording when a required permission or model asset is missing. It should explain the missing prerequisite in plain language.

### 5.2 Normal tap-mode flow

1. The owner focuses an editable field in another Android application.
2. `DictationAccessibilityService` receives a focus or window event and checks whether the field is a usable target.
3. The microphone bubble appears without replacing or dismissing the current keyboard.
4. The owner taps the bubble.
5. The app starts a microphone foreground service, starts the speech engine, and changes the bubble to `Recording`.
6. Audio frames flow into Mimi and the streaming STT engine. Partial text may replace the previous partial text in the bubble or a small companion view.
7. The owner taps the bubble again.
8. Audio capture stops. The speech engine drains its pending input and returns a final transcript. The bubble shows `Processing` only when finalization takes long enough to be noticeable.
9. The app validates the target field and inserts the final transcript at the cursor or replaces the selected range.
10. The bubble briefly shows `Done` if useful, then returns to `Idle`.

A tap on the bubble must not make the app the active input method. The existing keyboard remains installed, selected, and usable.

An empty final transcript is not an insertion failure. The app should return to `Idle` without modifying the target field and may show a short "No speech detected" status.

### 5.3 Failure flow

The bubble or a visible local status message must communicate these failures:

- Microphone permission is missing or revoked.
- Another application owns the microphone.
- The model is missing, corrupt, unsupported, or still loading.
- The inference backend fails.
- The target field disappeared or changed application.
- Direct text insertion is not supported by the target application.
- Clipboard paste was rejected.

The final transcript must not be silently discarded. If safe insertion is not possible, the app should leave it in the clipboard and tell the owner that it was copied. It must not use this fallback for password fields.

### 5.4 Focus changes

The app must not insert a transcript into a different application just because the owner changed focus while inference was running.

At the start of a dictation session, the app captures the target package, window, node identity where available, editable state, and selection. Before insertion it obtains a fresh node and validates that it still represents the same target. If the target changed, the app must not reconstruct the old field contents and write them into the new field.

When validation fails, the app may attempt clipboard fallback and must show why automatic insertion was skipped.

### 5.5 Bubble behavior

The bubble should be small but easy to tap. Its visible icon may be smaller, but its touch target should meet the Android accessibility touch-target guidance where the platform permits.

The bubble must:

- Show only when a visible, enabled, editable, non-password field is focused.
- Hide when there is no usable target, when the relevant window changes, or when the Accessibility Service is disabled.
- Use an overlay window that is not focusable, so it does not take text focus from the target or hide the keyboard.
- Accept a tap to toggle recording in the first version.
- Expose a content description and state to accessibility tools.
- Show recording and error states without requiring a separate full-screen UI.

The first integrated version may use one fixed default position. Dragging and position persistence are polish work, not a reason to delay inference validation.

## 6. Functional requirements

### 6.1 Permissions and lifecycle

- `FR-001`: The app must request `RECORD_AUDIO` only when the owner starts setup or recording, not on unrelated launch.
- `FR-002`: The app must declare and start the microphone foreground service according to the target Android SDK's requirements. The notification must remain visible while the microphone or finalization work is active.
- `FR-003`: The app must explain how to enable its Accessibility Service. It must detect when the service is disabled and hide or disable the bubble.
- `FR-004`: The app must request `SYSTEM_ALERT_WINDOW` only if the chosen overlay implementation needs it. Prefer the Accessibility overlay path when it works on the target Android version.
- `FR-005`: Stopping recording must release the `AudioRecord`, stop the foreground work when no inference remains, and release native inference session resources.
- `FR-006`: A process death or service restart may abort the active session. The app must reset to a safe idle state on the next start. Resuming a partially recorded session is not required.

### 6.2 Focus detection

- `FR-010`: The Accessibility Service must track the currently focused editable node using the smallest useful set of Accessibility events.
- `FR-011`: The service must check that the node is visible, enabled, editable, and associated with a usable window before showing the bubble.
- `FR-012`: Password and other fields marked as sensitive must not show the dictation bubble by default.
- `FR-013`: The service must not dump the whole screen or retain unrelated node text. It may read the focused node's text and selection only when needed for insertion.
- `FR-014`: The service must update or hide the bubble when focus, window, package, screen state, or service permissions change.

### 6.3 Audio capture

- `FR-020`: `AudioCapture` must provide mono PCM audio in the sample format required by the selected Mimi model. The current expected format is 24 kHz mono PCM and must be verified against the exact model and upstream implementation before it is treated as fixed.
- `FR-021`: If the device cannot capture the required rate directly, the app must use a tested resampling path rather than silently passing audio with the wrong rate.
- `FR-022`: Audio capture must run off the main thread and feed bounded buffers to the speech engine.
- `FR-023`: The implementation must record buffer overruns, dropped frames, and inference backlog in debug metrics. It must not grow an unbounded queue to hide a slow model.
- `FR-024`: The app must stop reading from the microphone promptly after the owner taps stop or cancels the session.
- `FR-025`: The app must not save microphone audio during normal use. Any debug recording mode must be explicit, local, clearly labeled, and disabled by default.

### 6.4 Speech engine

- `FR-030`: The Android-facing speech API must hide Mimi, transformer, decoder, JNI, Rust, C++, and ExecuTorch implementation details from Android services.
- `FR-031`: The engine must support preparation, session start, bounded audio input, partial transcript callbacks, finalization, cancellation, and typed errors.
- `FR-032`: Partial transcripts may be revised. Consumers must replace the displayed partial text rather than append every callback blindly.
- `FR-033`: `finish()` must drain the input accepted before stop and return one authoritative final transcript.
- `FR-034`: `cancel()` must prevent insertion and release the active session without leaving a native worker running.
- `FR-035`: Tap mode must use an explicit stop. Semantic voice activity or end-of-speech detection may inform the engine but must not stop the first version unexpectedly.
- `FR-036`: The engine must report model load time, first partial time, finalization time, processing time, and errors to the local benchmark/debug layer without including transcript or audio contents.

A possible API shape is below. It is a contract sketch, not a demand for these exact types:

```kotlin
interface SpeechEngine {
    suspend fun prepare(): EngineInfo
    fun start(): SpeechSession
}

interface SpeechSession {
    val partialTranscript: Flow<String>

    fun feedAudio(pcm24kMono: ShortArray)
    suspend fun finish(): String
    fun cancel()
}
```

`feedAudio` must consume or copy each buffer before returning because `AudioCapture` may reuse its buffer. Calls must be serialized or the engine must document its concurrency rules. No inference call may block the main thread.

### 6.5 Text insertion

- `FR-040`: `TextInserter` must prefer an Accessibility-based insertion path.
- `FR-041`: The inserter must preserve text outside the current cursor or selection.
- `FR-042`: If a selection exists, the dictated text replaces the selected range. If there is only a cursor, the dictated text is inserted at that position.
- `FR-043`: After insertion, the app should restore the cursor immediately after the inserted text when the target supports selection actions.
- `FR-044`: The inserter must not use stale field contents after the field changed while recording or processing.
- `FR-045`: The app must not rewrite an entire field merely to append a short transcript unless the target exposes no safer direct operation and the full text and selection are known to be current.
- `FR-046`: If direct insertion fails, the app must attempt clipboard plus paste only for a non-sensitive target.
- `FR-047`: If paste cannot be confirmed or is rejected, the dictated text must remain in the clipboard and the app must show a visible failure status.
- `FR-048`: The app must not log clipboard contents, target text, or the final transcript.

The initial insertion algorithm should be:

1. Obtain a fresh focused node.
2. Validate package, window, node identity where available, editable state, enabled state, and non-password status.
3. Read the current selection. Clamp invalid selection positions to safe bounds.
4. Use a target-supported direct insertion or selection-preserving Accessibility action when available.
5. If the target supports only whole-text replacement, construct the new value from the current text and selection only when the snapshot is current and the operation is safe. Restore the new cursor position if possible.
6. Otherwise place the final transcript in the clipboard and invoke the target's paste action.
7. Report success, fallback success, or failure. Do not claim direct insertion succeeded when only the clipboard was updated.

Automatic spaces, capitalization based on surrounding text, and sentence continuation are not required for the first version. The first implementation should insert the model's final text exactly, then add those behaviors only with explicit tests.

### 6.6 Keyboard compatibility

- `FR-050`: The app must not implement `InputMethodService`.
- `FR-051`: The app must not change the system's default keyboard or input method setting.
- `FR-052`: The overlay must not request input focus or consume touches outside its own control.
- `FR-053`: The app must work with Samsung Keyboard in the primary acceptance scenario and must not depend on a keyboard-specific private API.

## 7. State machine

The visible dictation state is intentionally small:

| State | Meaning | Allowed user action | Exit |
| --- | --- | --- | --- |
| `Idle` | No active recording or finalization | Tap to start | `Recording` or `Error` |
| `Recording` | Microphone is open and audio is flowing | Tap to stop | `Processing`, `Error`, or `Idle` on cancellation |
| `Processing` | Audio has stopped and the engine is finalizing | No second start; a future cancel action may abort | `Done`, `Error`, or `Idle` |
| `Done` | Final text was inserted | No action required | Return to `Idle` |
| `Error` | The last operation failed | Tap or timeout to dismiss | `Idle` |

Required transitions:

```text
Idle --tap--> Recording
Recording --tap--> Processing
Processing --final transcript and insertion success--> Done
Processing --empty transcript--> Idle
Recording or Processing --failure--> Error
Done or Error --dismiss/timeout--> Idle
```

The controller must reject duplicate start and stop events. A second tap during `Processing` must not start another session. A later cancel action can be added without changing the speech engine contract.

## 8. Proposed architecture

The initial app should stay in one Android process and use a small session coordinator. It does not need a general event bus or a persistent database.

### Components

| Component | Responsibility | Must not own |
| --- | --- | --- |
| `MainActivity` | Setup, permission status, prototype controls, diagnostics | Long-running microphone or inference work |
| `DictationAccessibilityService` | Focus events, target snapshots, bubble visibility requests, insertion entry point | Mimi or transformer details |
| `BubbleService` | Overlay window, rendering, tap events, state display, optional later drag behavior | Text-field discovery or model code |
| `DictationForegroundService` | Foreground lifetime, microphone and inference session while active, notification | Product setup UI |
| `AudioCapture` | `AudioRecord`, format conversion, bounded frame delivery | Text insertion and UI |
| `SpeechEngine` | Stable speech contract and callbacks | Android Accessibility APIs |
| `NativeSpeechEngine` | JNI or Rust/C++ bridge to Mimi and the STT transformer | Overlay and permissions |
| `TextInserter` | Selection-aware direct insertion and clipboard fallback | Speech decoding |
| `ModelManager` | Asset discovery, version metadata, checksum validation, backend selection | Downloading data during an active dictation session |
| `Benchmark` layer | Timings, resource samples, and report output without content data | Production UI behavior |

A small `DictationSessionController` may connect these components. It owns the current state, target snapshot, and session lifecycle. Keeping this state in one place is safer than making the three Android services infer each other's state. The controller may be an in-process object for the first version. If Android kills the process, the controller resets and no session is resumed.

### Runtime flow

1. `DictationAccessibilityService` receives a focus event and passes a minimal target description to the controller.
2. The controller asks `BubbleService` to show or hide the bubble.
3. A bubble tap starts `DictationForegroundService` from a user action. The service prepares the engine if needed, starts `AudioCapture`, and publishes `Recording`.
4. `AudioCapture` sends bounded PCM frames to the speech session. The engine publishes replaceable partial text to the controller and bubble.
5. A second bubble tap stops capture and calls `finish()` off the main thread.
6. The controller passes the final transcript and the captured target to `TextInserter` through the Accessibility Service.
7. The inserter reports the outcome. The controller shows `Done`, `Error`, or a clipboard fallback message and shuts down active foreground work.

No component should make the UI wait for model loading, finalization, or a clipboard operation. The Activity and bubble must remain responsive while those operations run.

### Service lifecycle

- The Accessibility Service may remain enabled while the owner uses the phone, but it must not keep the microphone open while idle.
- The overlay should exist only while a usable target is present, unless the target Android version requires a different lifecycle.
- The foreground service should run only during capture and any required finalization.
- The foreground notification must identify that the microphone is active and provide a stop action if the platform allows it.
- Service shutdown must be idempotent. Repeated stop, permission revocation, or app backgrounding must not leak an `AudioRecord`, native session, or overlay window.

## 9. Speech and model design

### 9.1 Model choice

The first model is Kyutai `stt-1b-en_fr`. The app needs English dictation only. Do not begin with the 2.6B model.

The exact checkpoint revision, tokenizer, Mimi revision, model license, input contract, output contract, and conversion requirements must be recorded before model assets are packaged. The app must not silently mix files from different revisions.

### 9.2 Runtime split

Mimi and the STT transformer must remain independently replaceable:

- Mimi should use the existing native, Rust, or Candle implementation from the Moshi Android work if it is practical and compatible.
- The transformer should use ExecuTorch as the preferred Android runtime direction.
- The Android-facing `SpeechEngine` must not expose whether a component runs through Candle, ExecuTorch, JNI, CPU, XNNPACK, QNN, Vulkan, or another backend.

The first baseline should use the least complicated working configuration. Candidate acceleration paths are:

- CPU with XNNPACK where supported.
- Qualcomm QNN on the target Snapdragon device if the exported graph and installed SDK support it.
- Vulkan only if measurement shows a useful, stable improvement.

Hardware acceleration is optional. A backend that is difficult to reproduce or less accurate is not a win merely because it is available.

### 9.3 Audio contract

The current expected input to Mimi is 24 kHz, mono PCM. The implementation must verify this against the selected checkpoint and the upstream Moshi Android code during the investigation phase.

The audio path must define:

- PCM sample type and numeric range.
- Channel count.
- Sample rate.
- Frame size and hop size.
- Resampling behavior.
- Buffer ownership.
- Timestamp or sequence handling.
- What happens when inference falls behind.

The Android prototype must show that the exact same audio contract works with both prerecorded audio and the device microphone. A successful build that feeds the wrong sample rate is not a valid prototype.

### 9.4 Export and conversion investigation

Before implementing a full native bridge, inspect and document:

1. How `LaurentMazare/moshi-android` captures microphone audio.
2. How its Mimi implementation is loaded and called on Android.
3. How it integrates ExecuTorch, including supported operators and model packaging.
4. Which conversational transformer or experiment the current Android project uses.
5. Which parts can be reused unchanged for a streaming STT transformer.
6. How the official Kyutai STT inference path represents state, caches, token decoding, and end of speech.
7. How to export the STT transformer for ExecuTorch without changing model semantics.
8. How to validate the exported model against official model outputs or a fixed compatibility fixture on Android.

The conversion pipeline should:

- Record upstream repository commits and model revision.
- Keep conversion scripts and configuration in `models/`.
- Store checksums and generated artifact names.
- Avoid committing large weights or generated binaries.
- Produce a deterministic or clearly versioned export.
- Include a fixed input fixture and expected output from the official model or an upstream test vector when available.
- Compare FP16 output before quantization.
- Quantize only after the FP16 path is correct.

Do not rewrite working Moshi Android infrastructure before establishing that the existing code cannot be reused.

### 9.5 Quantization order

If the raw model is too slow or too large, benchmark changes in this order:

1. Establish a correct Android FP16 or closest supported baseline.
2. Export the transformer to ExecuTorch.
3. Benchmark INT8.
4. Try INT4 only if INT8 does not meet the speed or memory gate.
5. Test QNN or another accelerator on the target device.
6. Compare speed, memory, thermal behavior, failure rate, and transcript quality after every change.

Quantization must not be accepted from speed alone. The report must include fixed-audio output comparisons and a human review of ordinary dictation, punctuation, longer sentences, and pauses.

### 9.6 No post-processing in the first version

The first version must return the STT model's text. It must not send text to an LLM or add a cloud grammar service. Basic deterministic cleanup may be considered later, but it needs its own tests because changing spaces or punctuation can damage insertion quality.

## 10. Desktop baseline decision

The desktop transcription baseline is intentionally skipped. After the reference-project inspection, the project goes directly to the Android speech prototype because the product runs on one phone and Android runtime performance is the deciding risk.

This does not make upstream reference code irrelevant. Use official model outputs, upstream test vectors, or a temporary desktop diagnostic when they answer a specific conversion or compatibility question. Do not create a separate desktop application, desktop benchmark suite, or Whisper comparison as a prerequisite.

The consequences are deliberate:

- Model quality is judged on the target phone with a small, repeatable set of English prompts and ordinary live dictation.
- Android is the first speech environment and the first performance environment.
- If Android output is ambiguous, add only the smallest diagnostic needed to answer the question. It does not become a new milestone.

## 11. Android speech prototype milestone

This is the first implementation milestone after source investigation. It is the smallest useful Android experiment:

- One Activity.
- Start button.
- Stop button.
- Live transcript text area.
- Microphone permission.
- Local model loading.
- Microphone to Mimi to Kyutai STT to visible transcript.

It must not include the Accessibility Service, floating bubble, clipboard insertion, or day-to-day integration yet. This keeps Android inference problems separate from focus and overlay problems.

The prototype must support both:

- A prerecorded audio path for repeatable tests.
- Live microphone input for real-time behavior.

### Measurements

Every benchmark report must define and record:

| Metric | Definition |
| --- | --- |
| Real-time factor | Wall-clock speech processing time divided by the audio duration. Report steady-state and end-to-end values separately. |
| First partial latency | Time from the first accepted microphone frame to the first visible partial transcript. |
| Finalization latency | Time from stop to the final transcript. |
| Model loading time | Time from process or load request to a ready engine. Report cold and warm cases. |
| Peak RAM | Maximum app process memory during load and during a sustained session. |
| CPU use | Average and peak CPU use during capture and inference. |
| Accelerator use | GPU, NPU, QNN, or other backend information when measurable. |
| Thermal behavior | Device temperature or thermal status before, during, and after sustained dictation. |
| Battery impact | Battery percentage and duration for a documented session, with screen and power conditions recorded. |
| Audio backlog | Queue depth, dropped frames, overruns, or time spent behind the live input. |
| Failure rate | Crashes, engine errors, stalls, and sessions that fail to finalize. |

### Android gate

The target device must demonstrate a steady-state RTF below `1.0` on representative live and prerecorded sessions. The benchmark must include repeated short sessions and at least one sustained session long enough to expose thermal or queue problems. A preferred stretch goal is an RTF around `0.7` or lower, but that is not a first-release requirement.

The report must also show that:

- Partial text continues to arrive without an unbounded backlog.
- Finalization completes after stop.
- The app survives several minutes of dictation without a crash or unreleased microphone.
- RAM, thermal behavior, and battery impact are acceptable for personal daily use, based on measured results rather than an invented universal limit.

If the RTF gate fails, work returns to the runtime and model phases. Do not compensate by hiding delays in the overlay.

## 12. Android integration milestone

After the Android speech gate passes, add the Wispr-style workflow in small increments.

### Integration order

1. Add `DictationAccessibilityService` and verify focused editable-node detection with a fake or test Activity.
2. Add the overlay and show a non-recording bubble only for valid fields.
3. Connect a bubble tap to start and stop the already validated speech engine.
4. Insert into a controlled test field at the cursor.
5. Add selection replacement and focus-change protection.
6. Add clipboard and paste fallback.
7. Test real applications, beginning with WhatsApp and Samsung Keyboard.
8. Add setup diagnostics for permissions, model assets, backend, and recent errors.

### Target field behavior

The first integration test set must include:

- An empty single-line field.
- Text with the cursor at the start, middle, and end.
- A selected range.
- A multiline field.
- A long existing value.
- A field in a second application.
- A field that does not expose a usable Accessibility action.
- A password field, which must not show the bubble or receive clipboard fallback.
- Focus changes during recording and during finalization.
- A field where the keyboard remains visible throughout.

The inserter must use a fake Accessibility tree and a real test Activity for deterministic tests before relying only on manual third-party app testing.

## 13. Privacy and security

The app has powerful permissions, so the implementation must keep its data handling narrow.

- Runtime speech recognition is local. The app must not upload audio, transcripts, field text, model telemetry, or crash payloads.
- Normal sessions must keep audio and transcripts in memory only until insertion or error handling completes.
- Logs may include state transitions, timings, model revision, backend, package name when needed for debugging, and error categories. Logs must not include field contents, audio, clipboard text, or transcript text.
- Do not enable Accessibility event logging or whole-screen snapshots.
- Hide the bubble for password fields and other nodes explicitly marked sensitive.
- Do not use clipboard fallback for password fields.
- Store model files in app-controlled storage and verify their checksums before loading.
- Do not request broad storage access. Use app-specific storage or a user-selected file flow if model installation needs it.
- Show a microphone foreground notification while recording or finalizing.
- Make it easy to stop the service and revoke permissions from setup.
- Any debug mode that records audio or saves transcripts must require an explicit action, display its state, and be documented as sensitive.

Clipboard restoration is not a first-release requirement. Reading and restoring an existing clipboard can expose user data and can race the target paste operation. The first implementation may leave the dictated text in the clipboard after a fallback operation. If restoration is added later, it needs a focused privacy and timing design.

## 14. Testing strategy

### Model compatibility tests

- Use official model outputs, upstream test vectors, or fixed Android fixtures when available.
- Repeat the same inputs after every export or quantization change.
- Keep expected output, Android output, and human review notes together in the benchmark report.
- Test both streaming partials and final output.
- Include a failure test for missing or invalid model assets.
- Do not require a desktop application or desktop quality baseline.

### Native and speech tests

- Validate PCM sample rate, channels, sample type, frame sizes, and resampling.
- Test bounded buffer behavior and report dropped frames.
- Test partial transcript replacement and final transcript authority.
- Test finish, cancel, repeated finish, and engine failure paths.
- Test model load and unload without leaking native resources.
- Run a small golden-audio test in CI if the fixture and runtime are practical. Do not run the full 1B model on every ordinary unit test.

### Android unit and instrumentation tests

- Test the state machine and duplicate tap handling.
- Test setup status when each permission or model prerequisite is missing.
- Test focus filtering for editable, non-editable, disabled, invisible, password, and changed-window nodes.
- Test insertion at cursor positions and selected ranges.
- Test stale target protection.
- Test direct insertion failure and clipboard fallback.
- Test that a fallback failure leaves the final text in the clipboard and exposes an error state.
- Test overlay visibility and `FLAG_NOT_FOCUSABLE` behavior on the supported Android version.
- Test service shutdown, permission revocation, and process restart reset.

Use a fake `SpeechEngine` for Android service and insertion tests. The real model belongs in device benchmarks, not in every UI test.

### Manual device tests

The primary manual matrix is:

- Target Android phone and Android version.
- Samsung Keyboard.
- WhatsApp message field.
- At least one other messaging or notes application.
- A browser text field.
- Single-line and multiline fields.
- Cold and warm model starts.
- Short and sustained dictation.
- Screen rotation or window changes where relevant.
- Microphone contention with another app.
- Revoked permissions and missing model assets.

Record the exact device, app versions, keyboard version, model revision, and backend for every notable result.

### Privacy checks

- Run with network access blocked and confirm normal transcription still works.
- Inspect logs for field contents, clipboard text, transcripts, and audio.
- Confirm no audio file or transcript history appears after a normal session.
- Confirm password fields never expose the bubble or fallback clipboard behavior.

## 15. Development milestones

### Phase 0: source and architecture investigation

Deliverables:

- A temporary checkout or documented inspection of `LaurentMazare/moshi-android`.
- A source note covering Mimi execution, microphone capture, model loading, ExecuTorch integration, and the transformer used by the current Android experiment.
- A source note covering the Kyutai `stt-1b-en_fr` inference path, state, tokenizer, audio contract, and export requirements.
- A compatibility decision that lists reusable code, code that must change, and unresolved risks.
- A record of the exact model and upstream revisions.

Do not start with Accessibility or the floating UI. This phase can happen before the Android project is fully scaffolded.

### Phase 1: Android speech prototype

Deliverables:

- A minimal Activity with start, stop, and live transcript controls.
- Local model loading and microphone capture.
- Mimi and Kyutai STT streaming on the target device.
- Repeatable device benchmark instrumentation.
- A small target-device English quality check.

Exit gate: usable English output and steady-state RTF below `1.0` with no sustained backlog.

### Phase 2: runtime and performance

Deliverables as needed:

- ExecuTorch export and FP16 baseline.
- INT8 benchmark and accuracy comparison.
- INT4 benchmark only if still needed.
- XNNPACK, QNN, or Vulkan measurements where supported.
- A selected production backend and a documented fallback behavior.

Every optimization must have before-and-after speed, memory, thermal, battery, and quality evidence.

### Phase 3: Android workflow integration

Deliverables:

- Accessibility focus detection.
- Bubble overlay and tap interaction.
- Foreground recording service.
- Selection-aware insertion.
- Clipboard fallback and visible errors.
- WhatsApp plus Samsung Keyboard acceptance run.

Exit gate: the owner can complete the end-to-end success scenario repeatedly without changing keyboards or losing existing field content.

### Phase 4: polish

Only after Phase 3 passes, consider:

- Push-to-talk.
- Movable and remembered bubble position.
- Vibration and sound feedback.
- Automatic end-of-speech stopping.
- Spacing and capitalization rules.
- Cancel gesture and undo.
- Partial transcript presentation improvements.
- Model warm-up or keeping the model loaded.
- Custom vocabulary.

Each addition needs a clear reason and a regression test. The project should remain a small personal tool.

## 16. Repository structure

The planned layout is:

```text
app/
  Android application, Activity, services, permissions, and Android-facing integration
speech/
  Stable Android-facing speech API and session types
native/
  Rust or C++ native bridge, Mimi integration, and transformer runtime bindings
models/
  Model metadata, export configuration, conversion and quantization scripts
benchmark/
  Android benchmark harness, optional audio fixtures, and local result templates
docs/
  Specifications, decisions, model notes, and benchmark documentation
```

Model weights, private recordings, generated native libraries, and device-specific secrets must be excluded from version control. The repository should contain the steps and metadata needed to reproduce or obtain them, not unnecessary copies of them.

## 17. Risks and mitigations

### The 1B model is too slow on the target phone

Measure before building UX. Establish FP16, INT8, and accelerator baselines in that order. If the target cannot meet RTF `< 1.0`, stop the integration work and reassess the model or runtime.

### The Moshi Android experiment does not map cleanly to Kyutai STT

Inspect tensor shapes, state handling, token decoding, and audio framing before copying code. Keep Mimi and transformer adapters separate. Record the minimum required change instead of rewriting the whole stack.

### ExecuTorch lacks an operator or state pattern required by the STT model

Establish an authoritative model compatibility fixture and an Android FP16 export test first. Record unsupported operators and test a narrowly scoped fallback backend rather than hiding conversion differences in the Android UI.

### Quantization damages dictation quality

Compare every quantized output with fixed audio and human review. Keep the last accurate backend available until the replacement passes both speed and quality checks.

### Accessibility insertion differs across applications

Use a controlled test Activity and a fake node model first. Validate target identity and selection. Prefer paste fallback over unsafe whole-field reconstruction. Keep password fields excluded.

### The overlay steals focus or hides the keyboard

Use a non-focusable overlay and test with Samsung Keyboard on the target Android version. Do not implement an input method as a workaround.

### Android kills or restricts a service

Follow the target SDK's foreground-service and Accessibility lifecycle rules. Start microphone work from the explicit bubble action, show the required notification, and make process death reset safely.

### Sustained inference overheats or drains the battery

Measure several-minute sessions, not only short demos. Record thermal and battery data and decide from the target device's actual behavior.

### Model or upstream license terms change

Record the exact model and code revisions and review their licenses before packaging or redistributing anything. Personal sideloading does not remove the need to understand those terms.

## 18. Open decisions

These decisions should be made during the investigation or prototype phases, not guessed in the UI layer:

1. Target phone is Samsung Galaxy S23, `arm64-v8a`, Android 14 (API 34), with 8 GB RAM assumed until measured on the device.
2. Minimum and target Android SDK versions are 34. Revisit if a second device appears.
3. First STT bundle is pinned in [the Rust STT decision](../decisions/2026-09-05-stt-runtime.md): official `stt-1b-en_fr-candle` config/tokenizer/Mimi plus a Candle-compatible Q8 GGUF language model.
4. The app installs the four pinned assets to app-private storage and verifies byte counts and SHA-256. Update behavior remains open.
5. Whether `TYPE_ACCESSIBILITY_OVERLAY` on the target phone stays non-focusable and tap-reliable. The overlay APK starts with that path and no `SYSTEM_ALERT_WINDOW`.
6. ExecuTorch is not in the first runtime. Reconsider only if Candle CPU inference misses the S23 RTF gate.
7. Mimi uses the reusable Rust/Candle path through `moshi::asr::State`.
8. Q8 is the first target because it preserves accuracy and throughput. Q4 is a memory fallback only if Q8 cannot stay resident on the S23.
9. Which Accessibility insertion action works across the primary target applications.
10. Bubble ownership. The overlay APK attaches the window from `DictationAccessibilityService`. Split out a `BubbleService` only if that lifecycle fails on the phone.
11. How much model warm-up latency is acceptable for daily use.
12. Whether clipboard restoration is worth its privacy and timing cost.

Until these decisions are measured, the spec treats the preferred runtime and overlay paths as directions, not facts.

## 19. First usable-version acceptance checklist

- [ ] The exact model and upstream revisions are recorded.
- [ ] The Android prototype produces usable English dictation on the target device.
- [ ] The Android prototype transcribes live microphone audio locally.
- [ ] The target-device benchmark reports RTF, first partial latency, finalization latency, load time, RAM, CPU, thermal behavior, battery impact, backlog, and failures.
- [ ] Steady-state Android RTF is below `1.0` on representative sessions.
- [ ] The app does not make runtime network requests.
- [ ] The microphone is not recorded or retained during normal use.
- [ ] The Accessibility Service detects valid editable fields and excludes password fields.
- [ ] The bubble appears and disappears with focus without taking input focus.
- [ ] Tapping starts and stops recording without replacing Samsung Keyboard.
- [ ] Text inserts at the cursor and replaces a selected range without losing surrounding text.
- [ ] Focus changes do not cause insertion into a different application.
- [ ] Clipboard and paste fallback works for a cooperating test field.
- [ ] Failed insertion leaves the transcript in the clipboard and shows an error, except for sensitive fields.
- [ ] WhatsApp plus Samsung Keyboard passes the end-to-end success scenario repeatedly.
- [ ] Unit, instrumentation, and target-device regression tests cover the changed paths.
