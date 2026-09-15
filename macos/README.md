# macOS Dictator

Menu-bar dictation on this Mac. FUTO ACFT Whisper files plus official Hugging Face `medium-q8_0` and `large-v3-turbo-q8_0`. Metal is on for this target only. Android still only lists tiny/base/small ACFT.

Official ggml files skip FUTO's shortened `audio_ctx` so short clips do not loop.

## Gestures

- Hold Fn: record. Release: transcribe and insert.
- Double-press Fn: latch. Press Fn again: stop and insert.
- A lone short tap cancels. Nothing is inserted.
- Escape cancels listening or an in-flight transcript. The key is not swallowed. Settings no longer steals frontmost focus on launch, so paste can target the app you were in.
- `[BLANK_AUDIO]`, `[MUSIC]`, and similar Whisper tags are discarded. Nothing is inserted.

Turn off Keyboard → Dictation and set Globe / Fn to Do Nothing so Apple does not also fire.

The watcher observes Globe/Fn with NSEvent plus a listen-only CG tap. It never uses a steal tap. If an older build made the keyboard stall, quit every Dictator process in Activity Monitor before opening this one.

Fn cannot fire while `axTrusted=false` and `hidListen=denied`. Grant Accessibility for `/Applications/Dictator.app`, then Retry Fn watcher. `nsEvents=0` and `Last Fn: no Fn event yet` mean the OS never delivered Globe/Fn to this process.

## Build

```sh
chmod +x native/build-macos.sh macos/build.sh
macos/build.sh
open /Applications/Dictator.app
```

Open `/Applications/Dictator.app`. Do not launch `.build/release/Dictator` or `swift run`. Those are linker-signed as `Dictator` with no bound Info.plist, so Input Monitoring never lists them.

`macos/build.sh` also copies the signed bundle to `/Applications/Dictator.app`. Open that copy after a rebuild. TCC lists Input Monitoring from an IOHID keyboard open, not from a listen-only CG tap.

If Accessibility is on in System Settings but Dictator still says missing, the toggle belongs to a different binary. Remove every Dictator row, then grant the one whose path is `/Applications/Dictator.app`.

If Input Monitoring is empty after a reset:

```sh
tccutil reset ListenEvent io.jyri.dictator.macos
open /Applications/Dictator.app
```

`hidListen=denied` plus `kIOReturnNotPermitted` means TCC already refused keyboard HID for this ad-hoc binary. Re-opening HID will not show a prompt. Grant Accessibility for this `/Applications/Dictator.app` row. That is the path that can see Globe/Fn when Input Monitoring is denied.

Ad-hoc signing has no Team ID. After a rebuild, remove stale Dictator rows and grant `/Applications/Dictator.app` again.

Click Retry Fn watcher after toggling. Settings shows Last Fn when Globe/Fn arrives. A hold should show Listening even if insert is still blocked. Transcript then goes to the clipboard.

If Fn still does nothing, press Globe/Fn once, then Copy diagnostics in Settings and paste the clipboard. The dump names the HID open result (`kIOReturnNotPermitted` is `-536870174`), TCC listen/post checks, codesign identity, and the last `flagsChanged` keycodes. It does not include typed characters.

Also grant Microphone, then download a model in Settings. Medium and large-v3-turbo are ~800 MB from Hugging Face. Progress should move in Settings. Load selected model runs off the main thread. Copy diagnostics includes `modelBytes`, `expected`, and `lastLoad`. If lastLoad starts with `Load failed`, paste that dump.

`swift test --package-path macos` covers Fn gestures, Escape gating, insert pid choice, AX text roles, insert verify, Whisper junk tags, and the listen-only watcher contract. AppKit tap/AX runtime still needs a signed `/Applications/Dictator.app`.

Core tests only:

```sh
swift test --package-path macos
```
