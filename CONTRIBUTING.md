# Contributing to NekoSU

Thanks for your interest! This project is a temporary-root-only KernelSU manager fork.

## Reporting bugs

- Export the log from the manager's `Settings` page first.
- Open an issue and describe the bug reproducibly.
- Attach the exported log file.

## Pull requests

- Keep changes focused; describe what and why.
- Build the manager (`./gradlew :app:assembleDebug`) before submitting.
- Do not commit generated artifacts (`libksud.so`, `*.ko`, `out/`, `.cxx/`, `build/`).
- Keep license headers and upstream attribution intact.
