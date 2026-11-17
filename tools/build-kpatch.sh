#!/usr/bin/env bash
# Minimal KPatch-Next / KPM build helper for NekoSU.
#
# This is an OPTIONAL developer helper; the Android/Gradle build never invokes it.
# Run on a Linux host from the NekoSU repo root.
#
# Usage:
#   tools/build-kpatch.sh kpm <source.c> [output.kpm]   # compile one KPM (AArch64 ET_REL)
#   tools/build-kpatch.sh engine                        # build vendored kpimg payload
#   tools/build-kpatch.sh hdr                           # generate kpimg headers
#   tools/build-kpatch.sh tools                         # build vendored kptools (Android NDK)
#
# Environment:
#   TARGET_COMPILE   AArch64 bare-metal compiler prefix, e.g. aarch64-none-elf-
#                    (used by `engine`/`hdr`, and by `kpm` when set)
#   CC               compiler for `kpm` when TARGET_COMPILE is unset
#                    (default: clang with --target=aarch64-none-elf)
#   ANDROID_NDK      Android NDK root (required by `tools`)
#   ANDROID_ABI      NDK ABI, default arm64-v8a
#   ANDROID_PLATFORM NDK platform, default android-33
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
KP=${KP_DIR:-"$ROOT/vendor/kpatch-next"}
OUT=${OUT:-"$ROOT/out/kpatch"}

die() { echo "error: $*" >&2; exit 1; }

[[ -d "$KP" ]] || die "vendored KPatch-Next not found: $KP"

build_kpm() {
    local src=${1:?usage: tools/build-kpatch.sh kpm <source.c> [output.kpm]}
    [[ -f "$src" ]] || die "source not found: $src"
    local out=${2:-"$OUT/$(basename "${src%.c}").kpm"}
    mkdir -p "$(dirname "$out")"

    local includes=(
        -I"$KP/kernel"
        -I"$KP/kernel/include"
        -I"$KP/kernel/patch/include"
        -I"$KP/kernel/linux/include"
        -I"$KP/kernel/linux/arch/arm64/include"
        -I"$KP/kernel/linux/tools/arch/arm64/include"
    )
    # Freestanding flags required by the KPM loader (see vendor/kpatch-next/MILKSU_BUILD.md).
    local flags=(
        -std=gnu11 -O2
        -ffreestanding -fno-builtin -fno-common
        -fno-stack-protector -fno-pic -fno-pie -fno-plt
        -fno-asynchronous-unwind-tables -fno-unwind-tables
        -fno-tree-loop-distribute-patterns
        -mgeneral-regs-only -mno-outline-atomics -ffixed-x18
    )

    if [[ -n "${TARGET_COMPILE:-}" ]]; then
        "${TARGET_COMPILE}gcc" "${flags[@]}" "${includes[@]}" -c "$src" -o "$out"
    elif [[ -n "${CC:-}" ]]; then
        "$CC" --target=aarch64-none-elf "${flags[@]}" "${includes[@]}" -c "$src" -o "$out"
    elif command -v clang >/dev/null 2>&1; then
        clang --target=aarch64-none-elf "${flags[@]}" "${includes[@]}" -c "$src" -o "$out"
    elif command -v aarch64-linux-gnu-gcc >/dev/null 2>&1; then
        aarch64-linux-gnu-gcc "${flags[@]}" "${includes[@]}" -c "$src" -o "$out"
    else
        die "no AArch64 compiler found; set TARGET_COMPILE or CC"
    fi

    echo "built KPM: $out"
    if command -v readelf >/dev/null 2>&1; then
        readelf -h "$out" | sed -n '1,10p'
    fi
}

build_engine() {
    [[ -n "${TARGET_COMPILE:-}" ]] || die "set TARGET_COMPILE (e.g. aarch64-none-elf-) for engine build"
    make -C "$KP/kernel" TARGET_COMPILE="$TARGET_COMPILE"
}

build_hdr() {
    [[ -n "${TARGET_COMPILE:-}" ]] || die "set TARGET_COMPILE (e.g. aarch64-none-elf-) for hdr build"
    make -C "$KP/kernel" TARGET_COMPILE="$TARGET_COMPILE" hdr
}

build_tools() {
    [[ -n "${ANDROID_NDK:-}" ]] || die "set ANDROID_NDK for tools build"
    local build_dir="$KP/tools/build/android"
    cmake -S "$KP/tools" -B "$build_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DCMAKE_BUILD_TYPE=Release \
        -DANDROID_ABI="${ANDROID_ABI:-arm64-v8a}" \
        -DANDROID_PLATFORM="${ANDROID_PLATFORM:-android-33}"
    cmake --build "$build_dir" --parallel
}

case "${1:-help}" in
    kpm) shift; build_kpm "$@" ;;
    engine) build_engine ;;
    hdr) build_hdr ;;
    tools) build_tools ;;
    help|-h|--help)
        sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
        ;;
    *) die "unknown command: $1 (try: kpm, engine, hdr, tools)" ;;
esac
