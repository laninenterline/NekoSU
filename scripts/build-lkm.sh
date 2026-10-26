#!/usr/bin/env bash
# Build kernelsu.ko for a KMI and install it into the ksud embedded assets.
#
# Required env:
#   KSU_EXPECTED_SIZE / KSU_EXPECTED_HASH - manager APK v2 signing cert size + sha256
# Optional env:
#   KMI (default android15-6.6), KDIR, CLANG_BIN
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
KMI=${KMI:-android15-6.6}
KDIR=${KDIR:-/opt/ddk/kdir/$KMI}
CLANG_BIN=${CLANG_BIN:-/usr/lib/llvm-18/bin}
OUT="$ROOT/out/$KMI"

: "${KSU_EXPECTED_SIZE:?set KSU_EXPECTED_SIZE (APK signing cert DER size)}"
: "${KSU_EXPECTED_HASH:?set KSU_EXPECTED_HASH (APK signing cert DER sha256)}"

[ -d "$KDIR" ] || { echo "KDIR not found: $KDIR (run scripts/build-kdir.sh first)"; exit 1; }
[ -x "$CLANG_BIN/clang" ] || { echo "clang not found: $CLANG_BIN"; exit 1; }

mkdir -p "$OUT"
SYMVERS_BAK=$(mktemp)
cp -f "$KDIR/Module.symvers" "$SYMVERS_BAK"
trap 'cp -f "$SYMVERS_BAK" "$KDIR/Module.symvers"; rm -f "$SYMVERS_BAK"' EXIT
# Empty Module.symvers => empty __versions section (required by the kallsyms loader).
: > "$KDIR/Module.symvers"

export PATH="$CLANG_BIN:$PATH"
env \
  CONFIG_KSU=m CONFIG_KSU_TRACEPOINT_HOOK=y CONFIG_KSU_MULTI_MANAGER_SUPPORT=y \
  KSU_EXPECTED_SIZE="$KSU_EXPECTED_SIZE" KSU_EXPECTED_HASH="$KSU_EXPECTED_HASH" \
  make -C "$KDIR" M="$OUT" src="$ROOT/kernel" ARCH=arm64 LLVM=1 LLVM_IAS=1 \
       KBUILD_MODPOST_WARN=1 modules -j"$(nproc)"

"$CLANG_BIN/llvm-strip" -d "$OUT/kernelsu.ko"
mkdir -p "$ROOT/userspace/ksud/bin/aarch64"
cp -f "$OUT/kernelsu.ko" "$ROOT/userspace/ksud/bin/aarch64/${KMI}_kernelsu.ko"
echo "built: userspace/ksud/bin/aarch64/${KMI}_kernelsu.ko"
