#!/usr/bin/env bash
# Rebuilds io.github.dokar3:quickjs-kt-android (pulled in by innertubex) for minSdk 21.
#
# The published AAR ships libquickjs.so built against API 23; it imports __register_atfork,
# which Android 5.x libc lacks, so the library fails to load there. This script recompiles the
# native library from the same tag with ANDROID_PLATFORM=21, swaps it into the original AAR and
# lowers the manifest minSdk. The Kotlin/Java classes are left untouched.
#
# Usage: ANDROID_NDK_HOME=/path/to/ndk [CMAKE=/path/to/cmake] scripts/build-quickjs-api21.sh
set -euo pipefail

VERSION=1.0.15
API=21
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/app/libs/quickjs-kt-android-$VERSION-api$API.aar"
CMAKE=${CMAKE:-cmake}
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to an NDK (r28c was used for the published build)}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

git -c advice.detachedHead=false clone -q --depth 1 --branch "v$VERSION" --recurse-submodules --shallow-submodules \
    https://github.com/dokar3/quickjs-kt "$WORK/src"
curl -sfL -o "$WORK/orig.aar" \
    "https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/$VERSION/quickjs-kt-android-$VERSION.aar"

mkdir "$WORK/aar"
unzip -q "$WORK/orig.aar" -d "$WORK/aar"

# Same flags as the upstream release build (quickjs/build.gradle.kts).
CFLAGS="-fstrict-aliasing -g0 -Os -fomit-frame-pointer -DNDEBUG -fvisibility=hidden"
for abi in "${ABIS[@]}"; do
    "$CMAKE" -S "$WORK/src/quickjs/native" -B "$WORK/build/$abi" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$API" -DANDROID_TOOLCHAIN=clang \
        -DCMAKE_BUILD_TYPE=MinSizeRel -DCMAKE_C_FLAGS="$CFLAGS" \
        -DTARGET_PLATFORM=android -DLIBRARY_TYPE=shared > /dev/null
    "$CMAKE" --build "$WORK/build/$abi" -j"$(nproc)" > /dev/null
    "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/llvm-strip --strip-unneeded \
        -o "$WORK/aar/jni/$abi/libquickjs.so" "$WORK/build/$abi/libquickjs.so"
done

sed -i "s/android:minSdkVersion=\"[0-9]*\"/android:minSdkVersion=\"$API\"/" "$WORK/aar/AndroidManifest.xml"

mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
(cd "$WORK/aar" && zip -qrX "$OUT" .)
echo "Wrote $OUT"
