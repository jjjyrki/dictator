#!/usr/bin/env bash
# Build libdictator_api.dylib with Metal, then the menu-bar app bundle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/native/build-macos.sh"

LIB_DIR="$ROOT/native/build-macos"
APP_DIR="$ROOT/macos/build/Dictator.app"
BIN_DIR="$APP_DIR/Contents/MacOS"

cd "$ROOT/macos"
swift test

swift build -c release --product Dictator

rm -rf "$APP_DIR"
mkdir -p "$BIN_DIR" "$APP_DIR/Contents/Resources"
cp "$ROOT/macos/.build/release/Dictator" "$BIN_DIR/Dictator"
cp "$LIB_DIR/libdictator_api.dylib" "$BIN_DIR/libdictator_api.dylib"
cp "$ROOT/macos/Info.plist" "$APP_DIR/Contents/Info.plist"
printf 'APPL????' > "$APP_DIR/Contents/PkgInfo"
install_name_tool -id @rpath/libdictator_api.dylib "$BIN_DIR/libdictator_api.dylib"
install_name_tool -add_rpath @executable_path "$BIN_DIR/Dictator" 2>/dev/null || true

# Linker-signed binaries use identifier "Dictator" and leave Info.plist unbound,
# so Input Monitoring never lists the app. Sign the dylib, then the bundle, with
# the same CFBundleIdentifier as Info.plist.
codesign --force --sign - --identifier io.jyri.dictator.macos.lib "$BIN_DIR/libdictator_api.dylib"
codesign --force --sign - --identifier io.jyri.dictator.macos "$BIN_DIR/Dictator"
codesign --force --deep --sign - --identifier io.jyri.dictator.macos "$APP_DIR"

INSTALL_DIR="/Applications/Dictator.app"
rm -rf "$INSTALL_DIR"
cp -R "$APP_DIR" "$INSTALL_DIR"

echo "Built $APP_DIR"
echo "Copied $INSTALL_DIR"
echo "Open /Applications/Dictator.app (not .build/release/Dictator)."
