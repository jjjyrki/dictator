# Dictator

Dictator is a personal, sideloaded Android dictation assistant. It puts a small microphone bubble above editable text fields, transcribes speech on the device, and inserts the result without replacing the user's keyboard.

The product question is whether on-device English dictation can transcribe faster than real time with good quality on the target Samsung Galaxy S23. The current APK uses the whisper.cpp runtime with a FUTO ACFT fine-tuned Whisper model. The Accessibility overlay remains a separate stub until that test passes.

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

The desktop baseline is skipped. The product path is runtime selection, then Android speech, then overlay. The current app contains both the STT spike and the temporary overlay harness, but the harness must not be treated as a product test until STT passes.

## Sideload the STT prototype

Target: Android 14+, `arm64-v8a` on the Galaxy S23. The APK includes the whisper.cpp JNI runtime. It downloads the 78 MB FUTO ACFT Whisper model on first setup. The model stays in app-private storage and works offline after installation.

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
native/build-android.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Dictator, install the model on Wi-Fi, load it, then record a short English sentence. The app reports audio duration, inference duration, real-time factor, and dropped frames. Do not enable or use the overlay as the acceptance test yet.

## Project boundaries

This is a local tool for one person's device. It has no accounts, cloud backend, synchronization, analytics, history UI, LLM post-processing, iOS app, Finnish recognition, or custom keyboard.

Model weights and generated artifacts do not belong in Git. Store their source, version, checksum, conversion steps, and benchmark results in documentation instead.
