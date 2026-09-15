# Dictator

Dictator is a personal dictation assistant. The Android app puts a microphone bubble above editable fields, transcribes on the device with whisper.cpp and FUTO ACFT models, and inserts the result without replacing the keyboard.

There is also a macOS menu-bar port: hold or double-press Fn, transcribe locally with Metal whisper.cpp, and insert into the focused field. See [macos/README.md](macos/README.md) and [TASK-0001](docs/tasks/TASK-0001-macos-dictation-mvp.md).

## Start here

- [Product and engineering specification](docs/specifications/00-local-android-dictation-assistant.md)
- [Documentation guide](docs/README.md)
- [Specification index](docs/specifications/README.md)

## Development order

1. Inspect the existing Moshi Android implementation and the Kyutai STT model.
2. Build a minimal Android microphone-to-transcript prototype.
3. Benchmark runtimes and quantization on the target device.
4. Add the accessibility service, overlay, and text insertion.
5. Polish only after the core workflow works.

Android remains the original product path: runtime, then speech, then overlay. The overlay is not the acceptance test until STT on the S23 is good enough. macOS is a parallel personal MVP, not a replacement for that gate.

## Sideload the STT prototype

Target: Android 14+, `arm64-v8a` on the Galaxy S23. The APK includes the whisper.cpp JNI runtime. It downloads the selected FUTO ACFT Whisper model on first setup; the choices are English-only or multilingual and range from about 43 MB to 264 MB. Models stay in app-private storage and work offline after installation.

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
native/build-android.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`native/build-android.sh` links the JNI library with 16 KB ELF page alignment and verifies its `LOAD` segments, so rebuilt APKs support Android devices using 16 KB pages.

Open Dictator and tap **Model** to choose an English-only or multilingual tiny, base, or small model. For a multilingual model, tap **Select spoken languages** and choose up to four languages from Whisper's supported language list. Select an installed model to load it; for a model marked not installed, choose it first and tap **Download model**. Then record a short sentence. The app shows the transcript and keeps the audio and inference duration in **Last test**. Do not enable or use the overlay as the acceptance test yet.

## Run the ADB UI tests

The instrumentation suite covers the setup screen, model menu, persisted settings, and the link to Android's Accessibility settings. It does not download a model or make speech assertions, so it is safe to run repeatedly on a connected test device.

```sh
# Build, install, and run every instrumentation test.
scripts/run-adb-tests.sh

# Run one test class or method.
scripts/run-adb-tests.sh io.jyri.dictator.MainActivityInstrumentationTest
scripts/run-adb-tests.sh io.jyri.dictator.MainActivityInstrumentationTest#launchShowsSetupControls
```

After installing the APKs, the same runner can be invoked directly:

```sh
adb shell am instrument -w -r io.jyri.dictator.test/androidx.test.runner.AndroidJUnitRunner
```

The script leaves app data alone by default. Use `CLEAR_APP_DATA=1 scripts/run-adb-tests.sh` on a disposable device when you need a clean no-model state. The device must run Android 14 or newer. An arm64 device is required if it already has a model installed and the test launches the native Whisper engine.

Gradle can run the same suite after a device is connected:

```sh
./gradlew :app:connectedDebugAndroidTest
```

## Project boundaries

This is a local tool for one person's devices. It has no accounts, cloud backend, synchronization, analytics, history UI, LLM post-processing, iOS app, or custom keyboard.

Model weights and generated artifacts do not belong in Git. Store their source, version, checksum, conversion steps, and benchmark results in documentation instead.
