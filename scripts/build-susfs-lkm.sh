#!/usr/bin/env bash
# Build the SUSFS LKM (inforcqb/susfs4ksu-lkm) for a KMI and install it into ksud assets.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
KMI=${KMI:-android15-6.6}
KDIR=${KDIR:-/opt/ddk/kdir/$KMI}
CLANG_BIN=${CLANG_BIN:-/usr/lib/llvm-18/bin}
SUSFS_SRC=${SUSFS_SRC:-/root/susfs-lkm-src}

[ -d "$KDIR" ] || { echo "KDIR not found: $KDIR"; exit 1; }

if [ ! -d "$SUSFS_SRC" ]; then
  git clone --depth 1 https://github.com/inforcqb/susfs4ksu-lkm.git "$SUSFS_SRC"
fi

export PATH="$CLANG_BIN:$PATH"
make -C "$KDIR" M="$SUSFS_SRC/kernel" ARCH=arm64 LLVM=1 LLVM_IAS=1 \
     KBUILD_MODPOST_WARN=1 modules -j"$(nproc)"

# Keep .symtab (kallsyms binding); drop debug sections only.
"$CLANG_BIN/llvm-strip" -d "$SUSFS_SRC/kernel/susfs_guard_lkm.ko"
mkdir -p "$ROOT/userspace/ksud/bin/aarch64"
cp -f "$SUSFS_SRC/kernel/susfs_guard_lkm.ko" \
      "$ROOT/userspace/ksud/bin/aarch64/${KMI}_susfs_guard_lkm.ko"
echo "built: userspace/ksud/bin/aarch64/${KMI}_susfs_guard_lkm.ko"
