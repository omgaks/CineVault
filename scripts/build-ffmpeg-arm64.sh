#!/usr/bin/env bash
# Build LGPL-only FFmpeg Android arm64 libraries for CineVault.
# Usage: ANDROID_NDK_HOME=/absolute/ndk bash scripts/build-ffmpeg-arm64.sh /path/to/FFmpeg-source
set -euo pipefail
SRC="${1:?Pass the local FFmpeg source directory (e.g. FFmpeg n7.1)}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
test -n "$NDK" || { echo "Set ANDROID_NDK_HOME" >&2; exit 2; }
test -f "$SRC/configure" || { echo "FFmpeg source configure script missing" >&2; exit 2; }
HOST="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
case "$(uname -s)" in Linux) HOST=linux-x86_64;; Darwin) HOST=darwin-x86_64;; *) echo "Use Linux or macOS (Windows: WSL)" >&2; exit 2;; esac
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST"
test -x "$TOOLCHAIN/bin/aarch64-linux-android24-clang" || { echo "Android NDK arm64 compiler missing" >&2; exit 2; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PREFIX="$ROOT/.native/ffmpeg/arm64-v8a"
mkdir -p "$PREFIX"
cd "$SRC"
./configure \
  --prefix="$PREFIX" --target-os=android --arch=aarch64 \
  --enable-cross-compile --sysroot="$TOOLCHAIN/sysroot" \
  --cc="$TOOLCHAIN/bin/aarch64-linux-android24-clang" \
  --cxx="$TOOLCHAIN/bin/aarch64-linux-android24-clang++" \
  --ar="$TOOLCHAIN/bin/llvm-ar" --nm="$TOOLCHAIN/bin/llvm-nm" \
  --ranlib="$TOOLCHAIN/bin/llvm-ranlib" --strip="$TOOLCHAIN/bin/llvm-strip" \
  --enable-shared --disable-static --disable-programs --disable-doc \
  --disable-gpl --disable-nonfree --disable-version3 --disable-symver \
  --enable-pic
make -j"${JOBS:-4}"
make install
echo "FFmpeg SDK: $PREFIX"
echo "Enable via: ./gradlew -PcinevaultFfmpegRoot=$PREFIX :app:assembleDebug"
echo "NOTE: inspect the resulting library SONAMEs and dependencies before packaging."
