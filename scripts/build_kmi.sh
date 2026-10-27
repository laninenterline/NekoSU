#!/bin/bash
# Build <KMI>_kernelsu.ko and <KMI>_susfs_guard_lkm.ko from official DDK GKI images.
# Usage: build_kmi.sh <target>   e.g. android14-6.1
set -euo pipefail
T=${1:?target like android14-6.1}
export PATH=/usr/lib/llvm-18/bin:$PATH
PB=/root/ddk-prebuilts
KD=/opt/ddk/kdir/$T
SD=/opt/ddk/src/$T
cd "$PB"
for f in "kdir-min/kdir.$T.tar.zst" "src/src.$T.tar.zst"; do
  if [ "$(stat -c%s "$f" 2>/dev/null || echo 0)" -lt 1000000 ]; then
    echo "pulling $f ..."
    git lfs pull --include="$f" >/dev/null 2>&1 || true
    git lfs fetch origin --include="$f" >/dev/null 2>&1 || true
    git lfs checkout "$f" >/dev/null 2>&1 || true
  fi
  [ "$(stat -c%s "$f" 2>/dev/null || echo 0)" -gt 1000000 ] || { echo "FAIL pull $f"; exit 1; }
done
rm -rf "$KD" "$SD"; mkdir -p "$KD" "$SD"
tar --zstd -xf "kdir-min/kdir.$T.tar.zst" -C "$KD"
tar --zstd -xf "src/src.$T.tar.zst" -C "$SD"
for d in "$KD" "$SD"; do
  if [ -d "$d/$T" ]; then mv "$d/$T" "$d.tmp"; rmdir "$d"; mv "$d.tmp" "$d"; fi
done
echo "[$T] extracted: kdir=$(du -sh "$KD" | cut -f1) src=$(du -sh "$SD" | cut -f1)"
# remove x86_64 host artifacts so they get rebuilt natively
for tree in "$KD" "$SD"; do
  while IFS= read -r f; do
    case "$(file -b "$f" 2>/dev/null)" in *x86-64*|*x86_64*) rm -f "$f";; esac
  done < <(find "$tree/scripts" "$tree/tools" -type f \( -name '*.o' -o -perm -111 \) 2>/dev/null)
done
# native host tools
make -C "$KD" ARCH=arm64 LLVM=1 LLVM_IAS=1 olddefconfig </dev/null > /root/kmi-$T-tools.log 2>&1 || true
make -C "$KD" ARCH=arm64 LLVM=1 LLVM_IAS=1 scripts >> /root/kmi-$T-tools.log 2>&1 || true
make -C "$KD" ARCH=arm64 LLVM=1 LLVM_IAS=1 modules_prepare >> /root/kmi-$T-tools.log 2>&1 || true
[ -x "$KD/scripts/basic/fixdep" ] || make -C "$KD" ARCH=arm64 LLVM=1 LLVM_IAS=1 scripts_basic >> /root/kmi-$T-tools.log 2>&1 || true
[ -x "$KD/scripts/mod/modpost" ] || make -C "$KD" ARCH=arm64 LLVM=1 LLVM_IAS=1 scripts/mod/modpost >> /root/kmi-$T-tools.log 2>&1 || true
[ -x "$KD/scripts/mod/modpost" ] && [ -x "$KD/scripts/basic/fixdep" ] || {
  echo "FAIL host tools"; tail -25 /root/kmi-$T-tools.log; exit 1; }
# build kernelsu.ko (release-trust cert)
cd /root/NekoSU
cp -f "$KD/Module.symvers" /root/Module.symvers.full
: > "$KD/Module.symvers"
rm -rf out/kmi-$T; mkdir -p out/kmi-$T
set +e
env CONFIG_KSU=m CONFIG_KSU_TRACEPOINT_HOOK=y CONFIG_KSU_MULTI_MANAGER_SUPPORT=y \
  KSU_EXPECTED_SIZE=318 KSU_EXPECTED_HASH=e6b6840253ef5c04252ccd3a0e7b3d832dbe59730b8f1d895b5d3e89ac9d84d0 \
  make -C "$KD" M=/root/NekoSU/out/kmi-$T src=/root/NekoSU/kernel ARCH=arm64 LLVM=1 LLVM_IAS=1 \
  KBUILD_MODPOST_WARN=1 modules -j8 CONFIG_DEBUG_INFO_BTF_MODULES= CONFIG_DEBUG_INFO_BTF= > /root/kmi-$T-ko.log 2>&1
RC=$?
set -e
cp -f /root/Module.symvers.full "$KD/Module.symvers"
if [ $RC -ne 0 ]; then echo "FAIL kernelsu build ($T)"; grep -E "error:|fatal error" /root/kmi-$T-ko.log | head -5; exit 1; fi
echo "[$T] KSU_VERMAGIC=$(strings out/kmi-$T/kernelsu.ko | grep '^vermagic=' | head -1)"
echo "[$T] CERT_HASH_COUNT=$(strings out/kmi-$T/kernelsu.ko | grep -c e6b6840253ef5c04252ccd3a0e7b3d832dbe59730b8f1d895b5d3e89ac9d84d0)"
llvm-strip -d out/kmi-$T/kernelsu.ko
cp -f out/kmi-$T/kernelsu.ko userspace/ksud/bin/aarch64/${T}_kernelsu.ko
# build SUSFS ko
set +e
make -C "$KD" M=/root/susfs-lkm-src/kernel ARCH=arm64 LLVM=1 LLVM_IAS=1 KBUILD_MODPOST_WARN=1 \
  modules -j8 CONFIG_DEBUG_INFO_BTF_MODULES= CONFIG_DEBUG_INFO_BTF= > /root/kmi-$T-susfs.log 2>&1
RC=$?
set -e
if [ $RC -ne 0 ]; then echo "WARN susfs build failed ($T)"; grep -E "error:|fatal error" /root/kmi-$T-susfs.log | head -5; else
  echo "[$T] SUSFS_VERMAGIC=$(strings /root/susfs-lkm-src/kernel/susfs_guard_lkm.ko | grep '^vermagic=' | head -1)"
  llvm-strip -d /root/susfs-lkm-src/kernel/susfs_guard_lkm.ko
  cp -f /root/susfs-lkm-src/kernel/susfs_guard_lkm.ko userspace/ksud/bin/aarch64/${T}_susfs_guard_lkm.ko
fi
ls -l --block-size=1 userspace/ksud/bin/aarch64/${T}_*.ko | awk '{print $9, $5}'
# cleanup big dirs to save disk
rm -rf "$KD" "$SD"
echo "[$T] DONE"
