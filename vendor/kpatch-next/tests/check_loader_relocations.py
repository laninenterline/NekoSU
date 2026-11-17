#!/usr/bin/env python3
"""Reject absolute read-only data pointers in the movable, flat KPM loader."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


SECTION = re.compile(r"^Relocation section '([^']+)'")
ABSOLUTE = re.compile(r"\bR_AARCH64_ABS(?:64|32|16)\b")


def unsafe_relocations(text):
    section = ""
    unsafe = []
    for line in text.splitlines():
        match = SECTION.match(line)
        if match:
            section = match[1]
        if section.startswith((".rela.rodata", ".rel.rodata")) and ABSOLUTE.search(line):
            unsafe.append({"section": section, "relocation": line.strip()})
    return unsafe


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--object", type=Path, required=True)
    parser.add_argument("--readelf", default="aarch64-linux-gnu-readelf")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    data = args.object.read_bytes()
    output = subprocess.run(
        [args.readelf, "--wide", "--relocs", str(args.object)],
        check=True, capture_output=True, text=True, timeout=30,
    )
    failures = unsafe_relocations(output.stdout)
    report = {
        "object_sha256": hashlib.sha256(data).hexdigest(),
        "passed": not failures,
        "unadjusted_rodata_pointers": failures,
    }
    encoded = json.dumps(report, indent=2) + "\n"
    if args.report:
        args.report.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
