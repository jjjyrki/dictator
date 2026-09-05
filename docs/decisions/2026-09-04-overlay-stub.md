# Overlay stub prototype

Date: 2026-09-04

These choices apply only to the first sideloadable APK. They do not change the product path where Android STT must beat real time before overlay work counts as done.

- GitHub: do not create `jjjyrki/dictator` yet. Keep the tree local.
- First tap-insert backend: `StubSpeechEngine` with the canned sentence `Can you send me the document tomorrow morning?`. No Android `SpeechRecognizer`. No Kyutai model.
- Target: Android 14 (API 34), `arm64-v8a` only. `minSdk` and `targetSdk` are 34.
- Overlay: `TYPE_ACCESSIBILITY_OVERLAY` owned by `DictationAccessibilityService`. No `SYSTEM_ALERT_WINDOW`.
- Bubble owner: the Accessibility Service, not a separate `BubbleService`.
- Microphone and foreground capture are omitted in this APK. The Idle/Recording/Processing states still follow the spec so the later speech engine can plug into `SpeechEngine`.
