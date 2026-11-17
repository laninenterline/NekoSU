# NekoSU ↔ KPatch-Next (KPM) integration status

**Status: kernel-side KPatch runtime is NOT integrated (2026-11-18).**
`ksud kpm load`, `control`, `unload` and `integration enable` therefore return an
explicit `kernel does not expose KPatch runtime` error. `status`, `list`,
`import`, `info`, `remove`, `autoload` and the integration journal work and are
useful for protocol/UI testing, but they do not make KPMs executable.

This document records why, and what would be needed to change it. It is written
to prevent the PoC from being mistaken for a working KPM engine.

## What NekoSU currently ships

| Component | Path | Status |
| --- | --- | --- |
| KPatch-Next source (vendor) | `vendor/kpatch-next/` | present, GPL-2.0, unchanged |
| KPM ELF validation / registry / kpm command | `userspace/ksud/src/kpm*.rs` | works (no kernel runtime needed) |
| Integration journal + explicit status | `userspace/ksud/src/kpm_image.rs` | works; reports runtime absent |
| kpimg payload (kernel patch runtime) | — | **absent** |
| kptools (Android boot-image patcher) | — | **absent** |
| Boot-image patch transaction for kpimg | — | **absent** |
| Kernel-side KPatch/KPM code | — | **absent (by design, see below)** |
| Manager KPM page / repository / import | `manager/.../kpm*` | works for status/import/list; load is rejected |

## How KPatch-Next actually works

From `vendor/kpatch-next/doc/en/guide.md`: KPatch-Next has three components.

1. **kptools** parses a (stripped) kernel image, appends **kpimg** to the kernel,
   writes relocation/symbol information into it and redirects the kernel entry to
   kpimg. This is a **boot-image kernel patch**, not a kernel module load.
2. **kpimg** is a freestanding AArch64 payload (built with
   `aarch64-none-elf-*`, see `kernel/Makefile`). During early boot it patches the
   running kernel and exposes the user API through the repurposed
   `truncate` syscall (`0x1000` hello, `0x1020` load path, `0x1023` load buffer,
   `0x1031` list, …).
3. **kpatch / kpuser** only talks to an already-running kpimg through those
   supercalls (`user/kpatch.c`, `user/supercall.h`). It cannot install the runtime.

KPMs are ELF modules compiled against kpimg's headers (`<kpmodule.h>`,
`<kputils.h>`, `kpver`, …) and are loaded/relocated by kpimg itself.

The unused alternative is the separate **KPatch-Next-Module** project
(`https://github.com/KernelSU-Next/KPatch-Next-Module`, recorded in
`MILKSU_PROVENANCE.md`): it provides a runtime-loading kernel module for
KernelSU-Next. It is not vendored here, is built per kernel/KMI, and NekoSU
does not ship prebuilt copies.

## Why NekoSU cannot use it today

1. **Nothing in `kernel/` implements KPatch.** NekoSU's `kernel/` is a
   KernelSU-style GKI driver (hooks, sepolicy, manager, susfs, …). It contains no
   KPatch loader, no KPatch supercall handler and no `kpimg` symbol exports. The
   same is true for the MilkSU kernel sources: MilkSU's KPatch support is the
   boot-image kpimg injection in its `ksud kpm_image`, not a kernel patch.
   (Verified by grepping both trees for `kpatch|kpimg|kpm`.)
2. **kpimg is not a loadable module.** It is a flat AArch64 binary produced by
   `objcopy -O binary` from a freestanding linker script and injected into the
   kernel image by kptools; it is not an ELF `ET_REL` module and cannot be
   `insmod`-ed by NekoSU's existing LKM loader.
3. **Missing artifacts.** NekoSU has no `kpimg` or Android `kptools` binaries and
   no toolchain to build them in the allowed environment (kpimg needs GNU
   `aarch64-none-elf`, kptools needs the Android NDK; see
   `tools/build-kpatch.sh` and `MILKSU_BUILD.md`). Delivering an "enable" path
   without these would be a fake capability.
4. **Missing boot-image transaction.** MilkSU's `kpm_image.rs` (~64 KiB) and
   `image_transaction.rs` (~77 KiB) implement kernel-partition read, kptools
   patch/verify/recover, original-kernel backup, per-slot state, digest pinning
   and rollback. NekoSU's `lkm_image.rs` uses a different contract: it appends a
   `KSULKM1` capsule and bootstrap that loads `kernelsu.ko`. Composing the two
   (kpimg entry redirection + KSULKM capsule) is a new design that must be
   verified on real boot images; it is not a copy/paste port.
5. **Kernel prerequisites are not guaranteed.** kpimg needs
   `CONFIG_KALLSYMS=y` (ideally `CONFIG_KALLSYMS_ALL=y`), parseable image layout,
   writable kernel text/page tables and compatible arm64 instruction patching.
   NekoSU supports a range of stock/LKM devices whose kernels may not satisfy
   these; silently enabling KPM there would produce boot loops or broken modules.
6. **A fake supercall is not acceptable.** Implementing a HELLO/VERSION handler
   inside `kernelsu.ko` would make the engine probe report "active" while no
   loader exists, causing later `load` calls to fail with kernel errors. The
   project rule is: report absence explicitly, never pretend capability.

## What ksud reports

`userspace/ksud/src/kpm_image.rs` keeps the integration journal at
`/data/adb/ksu/kpm/integration.json`. The `kpm status` envelope's `data` object
contains the usual KSU fields plus the explicit runtime fields, for example:

```json
{
  "supported": true,
  "runtime_supported": false,
  "can_unload": false,
  "can_configure": true,
  "can_enable": true,
  "can_disable": false,
  "configured_enabled": false,
  "active": false,
  "reboot_required": false,
  "transition_failed": false,
  "state_known": true,
  "integration_verified": true,
  "provider": "none",
  "runtime_exposed": false,
  "runtime_error": "kernel does not expose KPatch runtime",
  "reason": "kernel does not expose KPatch runtime; KPM loading/control/unload are unavailable on this build (see vendor/kpatch-next/NEKOSU_INTEGRATION.md)"
}
```

`kpm_image`'s internal transition JSON additionally carries
`kernel_runtime_exposed`, `kernel_runtime_error` and `kernel_runtime_detail`;
`kpm.rs` folds the first two into `runtime_exposed` / `runtime_error` above and
keeps the human-readable `reason`.

Error paths:

| Command | Result |
| --- | --- |
| `ksud kpm status` | ok; explicit reason fields above |
| `ksud kpm list/import/info/remove/autoload` | ok (registry only) |
| `ksud kpm integration enable` | error `kernel_runtime_absent: kernel does not expose KPatch runtime; …` |
| `ksud kpm integration disable` | ok (clears a legacy PoC journal state) |
| `ksud kpm load/control/unload` | error `engine_inactive: kernel does not expose KPatch runtime; …` |

Manager shows the status `reason` and the backend error message, so the UI is
explicit about the limitation instead of hanging or crashing.

## What would be needed to actually enable KPM

1. Build and pin `kpimg` (GNU aarch64-none-elf) and Android `kptools`
   (NDK arm64-v8a). `tools/build-kpatch.sh engine` and
   `tools/build-kpatch.sh tools` wrap the vendored build; embed both as ksud
   assets with SHA-256 manifests.
2. Port a boot-image kernel patch transaction: parse/verify/extract kernel
   (`android_bootimg`), run kptools patch/recover, store original kernel, keep
   per-slot state and roll back on failure or version change.
3. Integrate with NekoSU's boot flow: decide ordering/coexistence with the
   `KSULKM` capsule (or patch-built-in-kernel-only), and re-verify the boot
   image after flashing.
4. Validate the runtime: HELLO magic, VERSION, CAPABILITIES (`0x3f` managed
   contract), then route `load` through buffer loading (`0x1023`) or pathname
   loading (`0x1020`).
5. Prove on real devices (arm64, GKI and/or vendor kernels): status → enable →
   reboot → active → load demo KPM (`kpms/demo-hello`) → control → unload /
   pending-reboot, plus rollback and autoload.
6. Only then flip the status to supported and remove the explicit errors above.

Until steps 1–5 are done, the KPM manager must be treated as a protocol/UI PoC,
not a working KPM feature.
