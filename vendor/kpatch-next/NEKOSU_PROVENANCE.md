# NekoSU vendored KPatch-Next (KPM) source

- From: MilkSU `vendor/kpatch-next` snapshot (MilkSU = KernelSU-Next based manager/kernel fork).
- Upstream: https://github.com/KernelSU-Next/KPatch-Next.git
- Upstream commit recorded by the MilkSU snapshot: `456744b29efb9989445463ab29e368fa59a103c4`
- Upstream version: 0.13.5, revision 2 (`version`, `revision`).
- Imported into NekoSU on: 2026-11-18.
- Purpose: KPM (Kernel Patch Module) support PoC for NekoSU — vendored ELF format,
  loader headers, demo/test KPMs and host regression tests. The NekoSU kernel does
  not load this engine yet; see `vendor/kpatch-next/MILKSU_BUILD.md` and
  `userspace/ksud/src/kpm_image.rs` (NekoSU integration state shim).
- License: root GPL-2.0 (`LICENSE`). Individual SPDX notices, including
  GPL-2.0-or-later, remain authoritative. TLSF retains its BSD-style notice in
  `kernel/include/tlsf.h`. The MilkSU provenance/licence files shipped with the
  snapshot are retained unchanged (`MILKSU_PROVENANCE.md`, `MILKSU_BUILD.md`).
- Modifications by NekoSU: none inside this directory; only this provenance note
  and the helper `tools/build-kpatch.sh` were added.

## What NekoSU uses today

- `kernel/include/**` KPM ABI headers (`.kpm.info`, `.kpm.init`, `.kpm.exit`,
  `.kpm.ctl0`) for validating KPM ELF files in `userspace/ksud/src/kpm_elf.rs`.
- `kpms/**` demo modules as smoke-test inputs for `ksud kpm import`.
- `kernel/`, `tools/`, `tests/` are kept for provenance and future engine work;
  they are not part of the Gradle/Android build.

## Kernel-side status (2026-11-18)

KPatch runtime is **not integrated** in NekoSU: no `kpimg`/`kptools` assets, no
boot-image kernel patch transaction and no in-tree kernel hooks. `ksud kpm`
therefore reports `kernel does not expose KPatch runtime` and rejects
`load`/`control`/`unload`/`integration enable`; `status`/`import`/`list`/`autoload`
keep working for protocol/UI testing. See `NEKOSU_INTEGRATION.md`.
