#!/usr/bin/env python3
"""Check Git publication candidates for local artifacts and private material.

Adapted from MilkSU tools/audit-public-source.py for NekoSU.

Candidates are every path git would publish: tracked files plus untracked files
that are not ignored (`git ls-files -c -o --exclude-standard`). The NekoSU repo
intentionally tracks the prebuilt LKM set under `userspace/ksud/bin/` because the
manager embeds it, so those files are allowlisted as publishable artifacts while
their hashes are still recorded. Binary files are hashed but not scanned for
textual secret patterns; every textual candidate is scanned.

Exit code 0 means the working tree is safe to publish, 1 means at least one
private artifact, secret or local path was found.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

# Directories that must never be part of the public source tree.
PRIVATE_PARTS = {
    ".git",
    ".gradle",
    ".kotlin",
    ".cxx",
    "build",
    "out",
    "node_modules",
    "private",
    "credentials",
    "secrets",
    "device-backup",
    "__pycache__",
}

# Extensions of local-only build or signing products.
PRIVATE_SUFFIXES = {
    ".apk", ".aab", ".apks", ".img", ".ko", ".p12", ".pfx", ".jks", ".keystore",
    ".pem", ".key", ".pcap", ".pcapng", ".hprof",
}

# Files that are published on purpose: the manager embeds these LKMs.
ALLOWLISTED_ARTIFACT_PREFIXES = (
    "userspace/ksud/bin/",
)

# Files with intentionally tracked symlinks.
ALLOWLISTED_SYMLINKS = {
    "kernel/include/uapi",
    "manager/app/src/main/cpp/uapi",
}

LOCAL_ONLY_NAMES = {"temp.gz", "local.properties", "keystore-password"}

SECRET = re.compile(
    rb"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----|"
    rb"gh[pousr]_[A-Za-z0-9_]{30,}|"
    rb"github_pat_[A-Za-z0-9_]{40,}|"
    rb"glpat-[A-Za-z0-9_\-]{20,}|"
    rb"AKIA[0-9A-Z]{16}|"
    rb"xox[baprs]-[A-Za-z0-9\-]{10,}"
)

# Split literals so this tool does not flag its own source when it scans the repo.
LOCAL_PATH = re.compile(
    rb"(?:[CD]:[/\\]Users[/\\]|/mnt/[a-z]/Users/)|"
    rb"/data/data/com\.termux/files/" rb"home|"
    rb"/root/Neko" rb"SU(?:/|\b)"
)


def is_binary(data: bytes) -> bool:
    return b"\x00" in data[:8192]


def allowlisted_artifact(path: PurePosixPath) -> bool:
    return path.as_posix().startswith(ALLOWLISTED_ARTIFACT_PREFIXES)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=None, help="repository root (default: parent of tools/)")
    parser.add_argument("--output", default=None, help="write the JSON report to this path")
    parser.add_argument("--json", action="store_true", help="print the full JSON report")
    args = parser.parse_args()

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[1]

    try:
        listed = subprocess.check_output(
            ["git", "-C", str(root), "ls-files", "-c", "-o", "--exclude-standard", "-z"],
            stderr=subprocess.PIPE,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"error: cannot list git candidates: {error}", file=sys.stderr)
        return 2

    candidates = sorted({entry.decode("utf-8", "surrogateescape")
                         for entry in listed.split(b"\0") if entry})

    issues: list[dict] = []
    warnings: list[dict] = []
    files: list[dict] = []
    skipped_content_scan: list[str] = []

    for name in candidates:
        relative = PurePosixPath(name)
        path = root / name

        if name.startswith("/") or ".." in relative.parts:
            issues.append({"path": name, "reason": "escapes-repository"})
            continue
        if not path.is_file() and not path.is_symlink():
            continue

        if path.is_symlink():
            if name not in ALLOWLISTED_SYMLINKS:
                issues.append({"path": name, "reason": "unexpected-symlink"})
                continue
            target = (path.parent / path.readlink()).resolve()
            try:
                target.relative_to(root.resolve())
            except ValueError:
                issues.append({"path": name, "reason": "symlink-escapes-repository"})
                continue
            files.append({"path": name, "sha256": None, "kind": "symlink", "target": str(path.readlink())})
            continue

        private_parts = {part.lower() for part in relative.parts}
        private_suffix = relative.suffix.lower() in PRIVATE_SUFFIXES
        private_name = (
            relative.name.lower() in LOCAL_ONLY_NAMES
            or relative.name.lower().startswith(".env")
        )

        if private_parts & PRIVATE_PARTS:
            issues.append({"path": name, "reason": "local-or-private-artifact"})
            continue

        if private_suffix or private_name:
            entry = {"path": name, "reason": "local-or-private-artifact"}
            if allowlisted_artifact(relative):
                warnings.append({**entry, "reason": "allowlisted-publishable-artifact",
                                 "note": "tracked on purpose; the manager embeds this LKM"})
            else:
                issues.append(entry)
                continue

        data = path.read_bytes()
        digest = hashlib.sha256(data).hexdigest()

        if is_binary(data):
            skipped_content_scan.append(name)
        else:
            if SECRET.search(data):
                issues.append({"path": name, "reason": "private-key-or-access-token"})
            if LOCAL_PATH.search(data):
                issues.append({"path": name, "reason": "local-user-path"})

        files.append({"path": name, "sha256": digest, "kind": "file", "bytes": len(data)})

    report = {
        "ok": not issues,
        "root": str(root),
        "files_checked": len(files),
        "binary_files_not_content_scanned": len(skipped_content_scan),
        "allowlisted": sorted(w["path"] for w in warnings),
        "issues": issues,
        "warnings": warnings,
        "files": files,
    }

    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    if args.json:
        print(json.dumps(report, indent=2))
    else:
        summary = {key: report[key] for key in
                   ("ok", "files_checked", "binary_files_not_content_scanned", "issues", "warnings")}
        print(json.dumps(summary, indent=2))

    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
