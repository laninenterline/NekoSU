#!/usr/bin/env bash
# Build the manager APK: ksud (with embedded ko assets) for every requested Android ABI + gradle.
#
# ABIS selects the packaged daemons (default: arm64-v8a and x86_64). Only ABIs with a staged
# libksud.so are packaged by Gradle, so set ABIS="arm64-v8a" to build an arm64-only APK or
# stage additional daemons yourself.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
KMI=${KMI:-android15-6.6}
ABIS=${ABIS:-"arm64-v8a x86_64"}
ASSET_DIR="$ROOT/userspace/ksud/bin/aarch64"
NDKBIN=${NDKBIN:-/root/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin}
TASK=${TASK:-assembleDebug}

case " $ABIS " in
  *" arm64-v8a "*)
    [ -f "$ASSET_DIR/${KMI}_kernelsu.ko" ] || { echo "missing ${KMI}_kernelsu.ko; run scripts/build-lkm.sh"; exit 1; }
    [ -f "$ASSET_DIR/${KMI}_susfs_guard_lkm.ko" ] || echo "warning: ${KMI}_susfs_guard_lkm.ko missing (SUSFS will be skipped)"
    ;;
esac

export PATH="$HOME/.cargo/bin:$PATH"
export RUSTUP_DIST_SERVER=${RUSTUP_DIST_SERVER:-https://mirrors.aliyun.com/rustup}
export RUSTUP_UPDATE_ROOT=${RUSTUP_UPDATE_ROOT:-https://mirrors.aliyun.com/rustup/rustup}
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-/root/android-ndk-r29}
export BINDGEN_EXTRA_CLANG_ARGS=""
export BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android=""
export BINDGEN_EXTRA_CLANG_ARGS_x86_64_linux_android=""

SYSROOT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

build_abi() {
  local abi="$1" triple api undef
  case "$abi" in
    arm64-v8a)
      triple=aarch64-linux-android
      api=29
      ;;
    x86_64)
      triple=x86_64-linux-android
      api=29
      ;;
    *)
      echo "unsupported ABI: $abi (supported: arm64-v8a x86_64)" >&2
      exit 1
      ;;
  esac
  undef=${triple//-/_}

  local clang="$NDKBIN/${triple}${api}-clang"
  [ -x "$clang" ] || { echo "missing Android clang: $clang" >&2; exit 1; }

  echo "Building ksud for $abi ($triple)"
  rustup +nightly target add "$triple" >/dev/null 2>&1 || true
  cd "$ROOT"
  env \
    "CC_${undef}=$clang" \
    "CXX_${undef}=${clang}++" \
    "AR_${undef}=$NDKBIN/llvm-ar" \
    "CARGO_TARGET_$(echo "$undef" | tr '[:lower:]' '[:upper:]')_LINKER=$clang" \
    "BINDGEN_EXTRA_CLANG_ARGS=--target=$triple --sysroot=$SYSROOT" \
    "BINDGEN_EXTRA_CLANG_ARGS_${undef}=--target=$triple --sysroot=$SYSROOT" \
    cargo +nightly build --target "$triple" --release \
      --manifest-path userspace/ksud/Cargo.toml

  local staged="manager/app/src/main/jniLibs/$abi"
  mkdir -p "$staged"
  cp -f "userspace/ksud/target/$triple/release/ksud" "$staged/libksud.so"
}

cd "$ROOT"
cargo +nightly clean -p ksud >/dev/null 2>&1 || true
for abi in $ABIS; do
  build_abi "$abi"
done

cd manager
# The Gradle build packages every ABI with a staged daemon; pin the requested set for CI so a
# missing daemon fails the build instead of silently producing an APK without ksud.
NEKOSU_ABIS="$ABIS" ./gradlew ":app:$TASK" --no-configuration-cache
APK=$(ls -t app/build/outputs/apk/*/*.apk | head -1)
echo "APK: $ROOT/manager/$APK"
