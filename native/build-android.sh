#!/usr/bin/env bash
# Builds the whisper.cpp JNI runtime for arm64-v8a and packages it into the
# app's jniLibs. The native library is linked for Android 16 KB page sizes.
# Usage: native/build-android.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK_ROOT="${ANDROID_NDK_HOME:-}"

if [[ -z "$NDK_ROOT" ]]; then
  NDK_ROOT="$(find "$SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
fi

if [[ ! -d "$NDK_ROOT" ]]; then
  echo "Android NDK not found. Set ANDROID_NDK_HOME." >&2
  exit 1
fi

JNI_LIBS="$ROOT/android/app/src/main/jniLibs/arm64-v8a"
BUILD_DIR="$ROOT/native/build-android-arm64"

cmake -S "$ROOT/native" -B "$BUILD_DIR" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-34 \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS='-march=armv8.2-a+dotprod+i8mm' \
  -DCMAKE_CXX_FLAGS='-march=armv8.2-a+dotprod+i8mm' \
  -DGGML_NATIVE=OFF \
  -DGGML_BACKEND_DL=OFF \
  -DGGML_CPU_ALL_VARIANTS=OFF \
  -DGGML_BLAS=OFF \
  -DDICTATOR_BUILD_JNI=ON \
  -DGGML_METAL=OFF \
  -DGGML_OPENMP=OFF \
  -DGGML_CPU_AARCH64=ON \
  -DBUILD_SHARED_LIBS=OFF
cmake --build "$BUILD_DIR" --parallel "$(sysctl -n hw.ncpu)" --target dictator_whisper

mkdir -p "$JNI_LIBS"
cp "$BUILD_DIR/libdictator_whisper.so" "$JNI_LIBS/"
TOOLCHAIN_BIN="$(dirname "$(find "$NDK_ROOT/toolchains/llvm/prebuilt" -path '*bin/llvm-strip' | head -n 1)")"
"$TOOLCHAIN_BIN/llvm-strip" --strip-unneeded "$JNI_LIBS/libdictator_whisper.so"

if ! "$TOOLCHAIN_BIN/llvm-readelf" -l "$JNI_LIBS/libdictator_whisper.so" | \
    awk '$1 == "LOAD" { found = 1; if ($NF != "0x4000") invalid = 1 } END { exit !found || invalid }'; then
  echo "The JNI library is not 16 KB ELF-aligned." >&2
  "$TOOLCHAIN_BIN/llvm-readelf" -l "$JNI_LIBS/libdictator_whisper.so" >&2
  exit 1
fi
