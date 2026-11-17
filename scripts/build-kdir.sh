#!/usr/bin/env bash
# Build a DDK-style KDIR from GKI source (android15-6.6.89_r00, 4K pages).
# Usage: scripts/build-kdir.sh
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
GKI_COMMIT=${GKI_COMMIT:-65999c7d05eccec53506a00d53f85575b230ab51} # android15-6.6.89_r00
CLANG_BIN=${CLANG_BIN:-/usr/lib/llvm-18/bin}
KDIR=${KDIR:-/opt/ddk/kdir/android15-6.6}
SRC=${SRC:-/root/ksrc89}

[ -x "$CLANG_BIN/clang" ] || { echo "clang not found: $CLANG_BIN"; exit 1; }

if [ ! -d "$SRC" ]; then
  echo "Downloading GKI source ($GKI_COMMIT)..."
  mkdir -p "$SRC"
  curl -fL -o /tmp/gki-src.tar.gz \
    "https://android.googlesource.com/kernel/common/+archive/${GKI_COMMIT}.tar.gz"
  tar -xzf /tmp/gki-src.tar.gz -C "$SRC"
  rm -f /tmp/gki-src.tar.gz
fi

export PATH="$CLANG_BIN:$PATH"
mkdir -p "$KDIR"
make -C "$SRC" O="$KDIR" ARCH=arm64 LLVM=1 gki_defconfig
"$SRC/scripts/config" --file "$KDIR/.config" \
  -d DEBUG_INFO -e DEBUG_INFO_NONE -d DEBUG_INFO_BTF -d DEBUG_INFO_BTF_MODULES -d WERROR
make -C "$SRC" O="$KDIR" ARCH=arm64 LLVM=1 olddefconfig
make -C "$SRC" O="$KDIR" ARCH=arm64 LLVM=1 -j"$(nproc)"
echo "KDIR ready: $KDIR (release: $(cat "$KDIR/include/config/kernel.release"))"
