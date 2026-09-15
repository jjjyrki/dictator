#!/usr/bin/env bash
# Builds whisper.cpp for the Mac menu-bar app with Metal enabled and the
# Metal library embedded. Usage: native/build-macos.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT/native/build-macos"

cmake -S "$ROOT/native" -B "$BUILD_DIR" \
  -DCMAKE_BUILD_TYPE=Release \
  -DDICTATOR_BUILD_JNI=OFF \
  -DGGML_METAL=ON \
  -DGGML_METAL_EMBED_LIBRARY=ON \
  -DGGML_BLAS=OFF \
  -DGGML_OPENMP=OFF \
  -DBUILD_SHARED_LIBS=OFF
cmake --build "$BUILD_DIR" --parallel "$(sysctl -n hw.ncpu)" --target dictator_api

echo "Built $BUILD_DIR/libdictator_api.dylib"
