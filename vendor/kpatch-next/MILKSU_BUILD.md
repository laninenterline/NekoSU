# Building the vendored engine and tools

Run from the MilkSU root in Linux/WSL.
The engine uses GNU AArch64 bare-metal tools (upstream CI uses GNU 12.2.rel1).

```sh
make -C vendor/kpatch-next/kernel TARGET_COMPILE=/path/to/bin/aarch64-none-elf-
```

This produces `kernel/kpimg` and `kernel/kpimg.elf`. The `hdr` prerequisite
generates `user/uapi`, `user/version`, and `tools/preset.h`; run it before
building userspace tools separately:

```sh
make -C vendor/kpatch-next/kernel TARGET_COMPILE=aarch64-none-elf- hdr
cmake -S vendor/kpatch-next/tools -B vendor/kpatch-next/tools/build/android \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DCMAKE_BUILD_TYPE=Release -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-33
cmake --build vendor/kpatch-next/tools/build/android --parallel
```

The Makefile explicitly disables outlined atomics, stack protectors, PIC, and
PIE for the freestanding payload. Keep those flags when using a distribution
`aarch64-linux-gnu-` compiler instead of the bare-metal toolchain. Early map
code must have no unresolved memcpy/stack-guard symbol or GOT dependency.

The Android tool build requires NDK zlib. Host kptools requires the host zlib
development package. The original all-in-one build script also builds the
legacy CLI; MilkSU's native Rust KPM backend does not require that executable.

Kernel prerequisites: little-endian ARM64, `CONFIG_KALLSYMS=y`,
`CONFIG_KALLSYMS_ALL=y`, no existing `CONFIG_KPM=y`, supported image/symbol
layout, and writable kernel text/page-table mappings. GKI/KMI identity alone
does not establish compatibility with PAC/BTI/CFI or protected-memory variants.
The kpimg payload is not a `.ko` and is installed in the kernel-bearing boot
image. The root ramdisk may reside in a different same-slot partition.

Host regressions (compile the actual loader with isolated host shims):

```sh
cc -std=gnu11 -pthread -Wall -Wextra -Werror -Wno-unused-parameter \
  -Wno-sign-compare \
  vendor/kpatch-next/tests/loader_host.c -o /tmp/milksu-kpm-loader-test
/tmp/milksu-kpm-loader-test
cargo test --manifest-path vendor/kpatch-next/tests/native/Cargo.toml
cc -std=gnu11 -O1 -g -fsanitize=address,undefined \
  -fno-sanitize-recover=undefined -Wno-unused-parameter -Wno-format \
  vendor/kpatch-next/tests/relocation_host.c -o /tmp/milksu-kpm-relocation-test
/tmp/milksu-kpm-relocation-test
```

The relocator harness validates the production `relo.c` arithmetic with
sanitizers. It does not execute ARM64 module instructions on the host.
Diagnostic engines emit bounded `KPM_LOAD checkpoint=` lines for file IO,
ELF validation, allocation, relocation and callback entry/return. Capture
`dmesg -w` during manual device loading to inspect those checkpoints.

The native Rust backend uses `goblin` for ELF parsing and validation. Its
dependencies are defined in the Cargo manifests.
