# Kernel-side KPatch/KPM status

NekoSU's `kernel/` tree intentionally contains **no KPatch or KPM code**.

## Why

KPatch-Next is not a loadable kernel module and is not compiled into the kernel
tree. Its kernel-side runtime is the **kpimg** payload: a freestanding AArch64
binary that `kptools` appends to the kernel image and whose entry takes over
early boot (`vendor/kpatch-next/doc/en/guide.md`). kpimg then patches the running
kernel and exports the KPM supercall API (repurposed `truncate` syscall).

NekoSU's `kernel/` is a KernelSU-style GKI driver: it provides hooks, sepolicy
integration, manager communication, susfs, etc. It does not provide the KPatch
supercall ABI, and it does not resolve/relocate KPM modules. The same is true of
the MilkSU kernel sources; MilkSU's KPM support lives entirely in the user-space
boot-image patching in `userspace/ksud/src/kpm_image.rs` plus the external
KPatch-Next-Module project.

## Detection and reported status

`ksud kpm` probes the supercall endpoint (syscall 45, `0x1000` HELLO). On a
kernel without kpimg this fails harmlessly (the syscall is the normal `truncate`
and returns an error), and ksud reports:

- `runtime_exposed: false`
- `runtime_error: "kernel does not expose KPatch runtime"`
- `reason: "kernel does not expose KPatch runtime; KPM load/control/unload are unavailable…"`
- `load`/`control`/`unload`/`integration enable` fail with
  `engine_inactive` / `kernel_runtime_absent`.

Do **not** add a fake HELLO handler here: it would claim a runtime that does not
exist and turn clear errors into kernel crashes.

## If kernel-side support is ever added

Two viable designs exist, both outside this turn's scope:

1. **Boot-image kpimg injection** (upstream design): keep `kernel/` unchanged,
   build `kpimg`/`kptools`, and add the boot-image patch transaction to ksud
   (see `vendor/kpatch-next/NEKOSU_INTEGRATION.md`).
2. **Runtime loader module** (KPatch-Next-Module design): ship a per-KMI kernel
   module that maps kpimg and installs the supercall handler, then load it
   through NekoSU's existing module/LKM path. This needs prebuilt per-KMI
   artifacts and kernel compatibility validation.

Until one of them is implemented and device-verified, KPM load must keep
returning the explicit "kernel does not expose KPatch runtime" error.
