# Changelog

## Unreleased

### 5.4 kernel support (best-effort prebuilt)
- Built `android11-5.4_kernelsu.ko` from AOSP `android11-5.4` (GKI 5.4.302) with the release manager cert
- Manager KMI matcher supports `android11-5.4` (release tag or SDK 30 fallback)
- ksud falls back to `android11-5.4` when a 5.4 kernel release string lacks the `android11` tag
- No SUSFS LKM for 5.4 yet (susfs4ksu-lkm needs 5.6+/5.8+ APIs); kernelsu.ko loads without it

### 5.x kernel support
- Added GKI 5.10 / 5.15 LKM variants: `android12-5.10`, `android13-5.10`, `android13-5.15`, `android14-5.15`
- Manager KMI matcher now supports 8 KMIs and disambiguates 5.10/5.15 by the `androidN` release tag (SDK-level fallback)
- CI `build-lkm` / `release` default KMI list includes all 8 KMIs
- Verified local LKM builds (kernelsu + SUSFS guard):
  - android12-5.10 (5.10.252), android13-5.10 (5.10.245)
  - android13-5.15 (5.15.202), android14-5.15 (5.15.202)

## v0.1.0-canary.8 (unreleased)

### Features
- Kernel module blacklist UI: edit/apply the preset modules acknowledged without loading
- KMI / load diagnostics page: device kernel, embedded KMI match, root/LKM/late-load/SUSFS status, advice and reboot-to-reload
- Dedicated jailbreak (Magica) log viewer inside the manager
- Crash logger: uncaught exceptions recorded locally and included in the problem-log export
- Manual load guide: wireless debugging / adb workflow with copyable commands
- Module repository: tag filter chips and installed badge
- SUSFS quick presets page
- Multiple-manager page: manager UIDs, signature slots and dynamic-signature status
- Settings search, settings backup/restore, app icon switcher

### Kernel
- Adopted upstream file-wrapper reference balancing and 6.18 sepolicy config guard
- Removed noisy logs from critical paths
- Fixed compat syscall header generation for old-style kernels (6.1/6.6)
- 6.12 modpost empty version table fix
- Verified LKM builds for 6.1 / 6.12 / 6.18

## v0.1.0-canary.7

- Jailbreak compatibility guard (preflight + reboot protection)
- Magica failure log and problem-log export
- Dependency sources moved to KernelSU upstream; BakaSU is now a reference only
