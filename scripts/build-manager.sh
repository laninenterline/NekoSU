#!/usr/bin/env bash
# Build the manager APK: ksud (with embedded ko assets) + gradle.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
KMI=${KMI:-android15-6.6}
ASSET_DIR="$ROOT/userspace/ksud/bin/aarch64"
NDKBIN=${NDKBIN:-/root/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin}
TASK=${TASK:-assembleDebug}

[ -f "$ASSET_DIR/${KMI}_kernelsu.ko" ] || { echo "missing ${KMI}_kernelsu.ko; run scripts/build-lkm.sh"; exit 1; }
[ -f "$ASSET_DIR/${KMI}_susfs_guard_lkm.ko" ] || echo "warning: ${KMI}_susfs_guard_lkm.ko missing (SUSFS will be skipped)"

export PATH="$HOME/.cargo/bin:$PATH"
export RUSTUP_DIST_SERVER=${RUSTUP_DIST_SERVER:-https://mirrors.aliyun.com/rustup}
export RUSTUP_UPDATE_ROOT=${RUSTUP_UPDATE_ROOT:-https://mirrors.aliyun.com/rustup/rustup}
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-/root/android-ndk-r29}
export CC_aarch64_linux_android="$NDKBIN/aarch64-linux-android29-clang"
export CXX_aarch64_linux_android="$NDKBIN/aarch64-linux-android29-clang++"
export AR_aarch64_linux_android="$NDKBIN/llvm-ar"

cd "$ROOT"
cargo +nightly clean -p ksud >/dev/null 2>&1 || true
cargo +nightly build --target aarch64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml

mkdir -p manager/app/src/main/jniLibs/arm64-v8a
cp -f userspace/ksud/target/aarch64-linux-android/release/ksud \
      manager/app/src/main/jniLibs/arm64-v8a/libksud.so

cd manager
./gradlew ":app:$TASK" --no-configuration-cache
APK=$(ls -t app/build/outputs/apk/*/*.apk | head -1)
echo "APK: $ROOT/manager/$APK"
