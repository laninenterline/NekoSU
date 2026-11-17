# MilkSU KPatch vendor source

- Upstream: https://github.com/KernelSU-Next/KPatch-Next.git
- Source commit: 456744b29efb9989445463ab29e368fa59a103c4
- Upstream version: 0.13.5, revision 2.
- Imported on: 2026-11-03.
- The vendored source snapshot excludes upstream Git metadata.
- Root license: GPL version 2, retained in `LICENSE`.
- Individual SPDX notices, including GPL-2.0-or-later, remain authoritative.
- TLSF retains its BSD-style notice in `kernel/include/tlsf.h`.

MilkSU changes add bounded ELF validation, serialized loader operations,
resident lifecycle states, a loader-capabilities supercall, and host regression
tests. Once a KPM init callback has run, code and arguments remain resident until
reboot: this ABI cannot prove that every hook, timer, work item, or other callback
has drained. An exit error never frees code. A successful exit still returns
`-EINPROGRESS` for unload and reports `pending_unload=1`.

The optional MilkSU capabilities supercall is `0x1033`. Its result is the bit mask `0x3f`:
bounded ELF, resident unload, serialized operations, state info, and bounded
enumeration, plus the current MilkSU loader contract. The full mask selects
MilkSU's verified buffer-loading contract. Standard KPatch-Next returns
`ENOSYS` for this extension and uses its original `0x1020` pathname-loading
ABI instead; no capability bits are fabricated. Other probe failures and
incomplete advertised MilkSU contracts remain errors.

The early `base/map.c` bootstrap uses a volatile byte copy instead of struct
assignment, preventing GCC from emitting an out-of-blob `memcpy` call before
the kernel/payload relocation stage.
Freestanding compiler flags explicitly disable stack protection, PIC/PIE and
outlined atomics so distribution GCC defaults cannot add runtime dependencies.

The production relocator handles full-width 64-bit data before computing
16/32-bit overflow masks. Host UBSan tests cover relocation arithmetic and
overflow boundaries. Loader checkpoints support runtime fault localization.

The loader validates relocated callback targets with
`module_callback_in_text()` and calls the range cache synchronization
routine over the complete executable allocation before invoking init. The host
adapter only permits known native test callbacks in valid pointer slots;
tests exercise the production address-boundary helper directly and assert
the range-cache arguments and ordering before init. Host tests do not execute
the ARM64 cache maintenance instructions.

The loader uses freestanding `lib_*` byte and string routines for ELF parsing
and resident module data, avoiding optional kernel string-function bindings
on those paths.

The MilkSU KPM initialization retains structure discovery, compiler compatibility,
the root-only command transport and module services. It does not install
KPatch's SELinux bypass, task-stack extension/fork observer, or the unrelated
getpriority hook. MilkSU remains the sole provider of root credentials and
SELinux policy.
