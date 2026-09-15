#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APP_ID="io.jyri.dictator"
TEST_ID="${APP_ID}.test"
ADB_BIN="${ADB:-adb}"

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
    echo "adb was not found. Set ADB to its path or add the Android SDK platform-tools to PATH." >&2
    exit 1
fi

if ! "$ADB_BIN" get-state >/dev/null 2>&1; then
    echo "No online Android device found. Connect a device, enable USB debugging, and accept the debugging prompt." >&2
    exit 1
fi

if [[ "${CLEAR_APP_DATA:-0}" == "1" ]]; then
    echo "Clearing $APP_ID data"
    "$ADB_BIN" shell pm clear "$APP_ID" >/dev/null || true
fi

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
"$ADB_BIN" install -r android/app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$ADB_BIN" install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

runner="$($ADB_BIN shell pm list instrumentation | tr -d '\r' | awk -v package="$TEST_ID" '$1 ~ "^instrumentation:" package "/" { sub(/^instrumentation:/, "", $1); print $1; exit }')"
if [[ -z "$runner" ]]; then
    echo "Could not find the installed instrumentation runner for $TEST_ID" >&2
    "$ADB_BIN" shell pm list instrumentation >&2
    exit 1
fi

args=(-w -r)
if [[ $# -gt 0 ]]; then
    args+=(-e class "$1")
fi

exec "$ADB_BIN" shell am instrument "${args[@]}" "$runner"
