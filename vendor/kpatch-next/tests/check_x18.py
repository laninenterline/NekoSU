#!/usr/bin/env python3
"""Read-only audit of x18 use in an unstripped AArch64 payload."""

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path


INSTRUCTION = re.compile(r"^\s*([0-9a-fA-F]+):\s+[0-9a-fA-F]{8}\s+(.+)$")
REGISTER = re.compile(r"\b[wx]18\b")


def instructions_using_x18(disassembly):
    result = []
    for line in disassembly.splitlines():
        match = INSTRUCTION.match(line)
        if match and REGISTER.search(match[2].split("//", 1)[0]):
            result.append({"address": "0x" + match[1], "instruction": match[2].strip()})
    return result


def classify(records, locations):
    violations = []
    assembly = []
    for record, (function, source) in zip(records, locations, strict=True):
        entry = {**record, "function": function, "source": source}
        filename = source.split(":", 1)[0].lower()
        if filename.endswith((".s", ".asm")):
            assembly.append(entry)
        else:
            # Missing debug information is not evidence that a use is safe.
            violations.append(entry)
    return violations, assembly


def audit(path, prefix):
    before = path.read_bytes()
    disassembly = subprocess.run(
        [prefix + "objdump", "-d", str(path)], check=True,
        capture_output=True, text=True, timeout=30,
    ).stdout
    records = instructions_using_x18(disassembly)
    locations = []
    if records:
        decoded = subprocess.run(
            [prefix + "addr2line", "-f", "-e", str(path),
             *[record["address"] for record in records]],
            check=True, capture_output=True, text=True, timeout=30,
        ).stdout.splitlines()
        if len(decoded) != len(records) * 2:
            raise RuntimeError("Unexpected addr2line output; cannot classify x18 uses")
        locations = list(zip(decoded[::2], decoded[1::2], strict=True))
    if before != path.read_bytes():
        raise RuntimeError("ELF changed while being inspected; rerun on a stable snapshot")
    violations, assembly = classify(records, locations)
    return {
        "elf": str(path), "sha256": hashlib.sha256(before).hexdigest(),
        "bytes": len(before), "c_or_unclassified_x18_uses": violations,
        "assembly_x18_uses": assembly, "passed": not violations,
        "limitation": "Read-only static ABI audit; not a runtime or crash-cause proof",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--elf", type=Path, required=True)
    parser.add_argument("--tool-prefix", default="aarch64-linux-gnu-")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = audit(args.elf.resolve(strict=True), args.tool_prefix)
    encoded = json.dumps(result, indent=2) + "\n"
    if args.report:
        args.report.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
