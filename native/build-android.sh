#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PATH="$HOME/.cargo/bin:$PATH"
if ! command -v cargo >/dev/null 2>&1; then
  RUST_TOOLCHAIN_BIN="$(find "$HOME/.rustup/toolchains" -mindepth 1 -maxdepth 1 -type d -name 'stable-*' | head -n 1)/bin"
  export PATH="$RUST_TOOLCHAIN_BIN:$PATH"
fi

SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK_ROOT="${ANDROID_NDK_HOME:-}"

if [[ -z "$NDK_ROOT" ]]; then
  NDK_ROOT="$(find "$SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
fi

if [[ ! -d "$NDK_ROOT" ]]; then
  echo "Android NDK not found. Set ANDROID_NDK_HOME." >&2
  exit 1
fi
if ! command -v cargo >/dev/null 2>&1 || ! cargo ndk --version >/dev/null 2>&1; then
  echo "Rust and cargo-ndk are required. Install them before building native code." >&2
  exit 1
fi

PREBUILT="$(find "$NDK_ROOT/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
JNI_LIBS="$ROOT/app/src/main/jniLibs/arm64-v8a"

export ANDROID_NDK_HOME="$NDK_ROOT"
export ANDROID_NDK_ROOT="$NDK_ROOT"
export ANDROID_NDK="$NDK_ROOT"
export CMAKE_ANDROID_NDK="$NDK_ROOT"

mkdir -p "$JNI_LIBS"
cd "$ROOT/native"
cargo ndk -t arm64-v8a -o "$ROOT/app/src/main/jniLibs" build --release
"$PREBUILT/bin/llvm-strip" --strip-unneeded "$JNI_LIBS/libdictator_stt.so"
cp "$PREBUILT/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$JNI_LIBS/"
