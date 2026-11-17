#!/usr/bin/env python3
"""Verify the identity, signer and embedded native inputs of a release APK.

Adapted from MilkSU tools/build-verify.py for NekoSU. Checks:
  * applicationId is com.nekosu (or --package)
  * versionCode/versionName match version.properties (a leading "v" on the APK
    versionName is tolerated, because Gradle derives versionName from the git tag)
  * the APK signature verifies and the signing certificate matches the release
    keystore (derived with keytool) or an explicitly expected SHA-256 digest
  * the embedded daemon/native libraries exist for every requested ABI and are
    64-bit ELF files of the ABI's machine type (arm64-v8a, x86_64)
  * every embedded lib/<abi>/libksud.so contains the same ksud build identity
    (its "<version> (uapi: N)" banner) as the pre-packaged libksud.so binary
    passed with --ksud ABI=PATH. AGP strips debug sections from packaged native
    libraries, so a whole-file hash of the built binary does not survive into
    the APK; the version banner is stable across stripping and catches stale
    daemons. --ksud-sha256 keeps the legacy exact-byte check for the arm64-v8a
    daemon.
  * only the requested ABIs are packaged, assets are present, and the manifest is
    neither debuggable nor testOnly

Exit code 0 means the APK may be published, 1 means at least one check failed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

# e_machine -> ABI: AArch64 (EM_AARCH64) and x86-64 (EM_X86_64). Android has no riscv64
# APK ABI yet, so only packaged Manager ABIs are listed here.
ABI_MACHINES = {
    "arm64-v8a": 183,
    "x86_64": 62,
}
SUPPORTED_ABIS = tuple(ABI_MACHINES)
DEFAULT_ABIS = ("arm64-v8a",)
DEFAULT_REQUIRED_ENTRIES = (
    "classes.dex",
)
KSUD_VERSION_PATTERN = re.compile(rb"([0-9][0-9A-Za-z._+-]{1,63}) \(uapi: ([0-9]+)\)")


def required_entries(abis: list[str]) -> list[str]:
    entries = list(DEFAULT_REQUIRED_ENTRIES)
    for abi in abis:
        entries.extend(
            [
                f"lib/{abi}/libkernelsu.so",
                f"lib/{abi}/libadbroot.so",
                f"lib/{abi}/libksud.so",
            ]
        )
    return entries


def parse_ksud_args(values: list[str] | None) -> dict[str, str]:
    """Parse repeatable --ksud ABI=PATH arguments (a bare PATH means arm64-v8a)."""
    result: dict[str, str] = {}
    for value in values or []:
        abi, sep, path = value.partition("=")
        if not sep:
            abi, path = "arm64-v8a", value
        abi = abi.strip()
        path = path.strip()
        if abi not in ABI_MACHINES:
            raise SystemExit(f"--ksud: unsupported ABI {abi!r} (supported: {', '.join(SUPPORTED_ABIS)})")
        if not path:
            raise SystemExit(f"--ksud: missing path for ABI {abi}")
        result[abi] = path
    return result


class Checker:
    def __init__(self) -> None:
        self.failures: list[str] = []
        self.notes: list[str] = []

    def check(self, condition: bool, message: str) -> bool:
        if not condition:
            self.failures.append(message)
        return condition

    def fail(self, message: str) -> None:
        self.failures.append(message)

    def note(self, message: str) -> None:
        self.notes.append(message)


def load_version(path: Path) -> tuple[str, int]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"{path}: malformed line: {raw_line!r}")
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    try:
        return values["NEKOSU_VERSION_NAME"], int(values["NEKOSU_VERSION_CODE"])
    except KeyError as error:
        raise SystemExit(f"{path}: missing {error}") from error


def find_tool(explicit: str | None, env_name: str, candidates: list[str],
              android_glob: str | None = None) -> str | None:
    for value in (explicit, os.environ.get(env_name)):
        if value:
            return value
    for candidate in candidates:
        found = shutil.which(candidate)
        if found:
            return found
    if android_glob:
        sdk_root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        if sdk_root:
            matches = sorted(Path(sdk_root).glob(android_glob))
            if matches:
                return str(matches[-1])
    return None


def normalize_cert(digest: str) -> str:
    return re.sub(r"[^0-9a-fA-F]", "", digest).lower()


def cert_from_keystore(keytool: str, keystore: Path, store_password: str,
                       alias: str) -> list[str]:
    result = subprocess.run(
        [keytool, "-list", "-v", "-keystore", str(keystore),
         "-storepass", store_password, "-alias", alias],
        capture_output=True, text=True, check=False, timeout=120,
    )
    if result.returncode != 0:
        raise SystemExit(f"keytool failed to read {keystore}: {result.stderr.strip()}")
    return [normalize_cert(match) for match in
            re.findall(r"SHA256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})", result.stdout)]


def parse_badging(badging: str, checker: Checker) -> tuple[str, int, str]:
    match = re.search(r"package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'",
                      badging)
    if not match:
        raise SystemExit("cannot parse aapt2 badging output for package identity")
    name, code_text, version_name = match.group(1), match.group(2), match.group(3)
    try:
        version_code = int(code_text, 0)
    except ValueError:
        raise SystemExit(f"unexpected APK versionCode: {code_text!r}") from None
    return name, version_code, version_name


def extract_ksud_version_markers(data: bytes) -> set[str]:
    """Return the ksud build banners embedded in a binary or packaged library."""
    return {match.group(0).decode("ascii", "replace")
            for match in KSUD_VERSION_PATTERN.finditer(data)}


def version_marker_matches(marker: str, expected_version: str) -> bool:
    version = marker.split(" (uapi: ", 1)[0]
    expected = expected_version.strip().lstrip("vV")
    return version == expected or version.startswith(expected + "-") or version.startswith(expected + "+")


def check_elf(entry: str, data: bytes, abi: str, checker: Checker) -> None:
    if not checker.check(len(data) > 64 and data[:4] == b"\x7fELF", f"{entry}: not an ELF file"):
        return
    checker.check(data[4] == 2, f"{entry}: not a 64-bit ELF")
    checker.check(data[5] == 1, f"{entry}: not a little-endian ELF")
    machine = int.from_bytes(data[18:20], "little")
    expected = ABI_MACHINES[abi]
    checker.check(
        machine == expected,
        f"{entry}: ELF machine {machine} does not match {abi} (expected {expected})",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", help="signed release APK to verify")
    parser.add_argument("--root", default=None, help="repository root (default: parent of tools/)")
    parser.add_argument("--version-file", default="version.properties",
                        help="release version contract file (default: version.properties)")
    parser.add_argument("--package", default=os.environ.get("NEKOSU_PACKAGE", "com.nekosu"),
                        help="expected applicationId")
    parser.add_argument("--expected-cert-sha256", default=os.environ.get("NEKOSU_EXPECTED_CERT_SHA256"),
                        help="expected signing certificate SHA-256 digest")
    parser.add_argument("--keystore", default=os.environ.get("NEKOSU_KEYSTORE"),
                        help="release keystore to derive the expected certificate from")
    parser.add_argument("--store-password", default=os.environ.get("NEKOSU_KEYSTORE_PASSWORD"),
                        help="release keystore password")
    parser.add_argument("--alias", default=os.environ.get("NEKOSU_KEY_ALIAS"),
                        help="release key alias")
    parser.add_argument("--abis", default=os.environ.get("NEKOSU_ABIS", ",".join(DEFAULT_ABIS)),
                        help="comma/space separated packaged ABIs to verify "
                             "(default: arm64-v8a; release publishes arm64-v8a + x86_64)")
    parser.add_argument("--ksud", action="append", default=None,
                        help="ABI=PATH of a pre-packaged ksud binary (jniLibs libksud.so or "
                             "cargo target); the matching lib/<abi>/libksud.so must carry the "
                             "same build banner. Repeat per ABI; a bare PATH means arm64-v8a")
    parser.add_argument("--ksud-sha256", default=os.environ.get("NEKOSU_KSUD_SHA256"),
                        help="legacy exact sha256 of the freshly built ksud binary embedded as libksud.so")
    parser.add_argument("--require-entry", action="append", default=None,
                        help="required APK entry (repeatable; defaults to the native daemon set)")
    parser.add_argument("--aapt2", default=None, help="path to aapt2")
    parser.add_argument("--apksigner", default=None, help="path to apksigner")
    parser.add_argument("--keytool", default=None, help="path to keytool")
    parser.add_argument("--output", default=None, help="write the JSON report to this path")
    args = parser.parse_args()

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[1]
    apk = Path(args.apk).resolve()
    checker = Checker()

    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")

    try:
        expected_name, expected_code = load_version((root / args.version_file).resolve())
    except OSError as error:
        raise SystemExit(f"cannot read version contract: {error}") from error

    actual_name: str | None = None
    actual_code: int | None = None
    actual_version: str | None = None
    apk_digests: set[str] = set()

    aapt2 = find_tool(args.aapt2, "AAPT2", ["aapt2", "aapt"],
                      "build-tools/*/aapt2")
    apksigner = find_tool(args.apksigner, "APKSIGNER", ["apksigner"],
                          "build-tools/*/apksigner")
    keytool = find_tool(args.keytool, "KEYTOOL", ["keytool"],
                        None) if args.keystore else None
    if not aapt2 or not apksigner:
        raise SystemExit("aapt2 and apksigner are required (install Android build-tools "
                         "or pass --aapt2/--apksigner)")

    # 1. Package identity and version contract.
    badging = subprocess.run([aapt2, "dump", "badging", str(apk)],
                             capture_output=True, text=True, check=False)
    if badging.returncode != 0:
        raise SystemExit(f"aapt2 failed: {badging.stderr.strip()}")
    actual_name, actual_code, actual_version = parse_badging(badging.stdout, checker)
    checker.check(actual_name == args.package,
                  f"applicationId mismatch: expected {args.package}, got {actual_name}")
    checker.check(actual_code == expected_code,
                  f"versionCode mismatch: expected {expected_code}, got {actual_code}")
    normalize_version = lambda value: value.strip().lstrip("vV")
    if normalize_version(actual_version) == normalize_version(expected_name):
        if actual_version != expected_name:
            checker.note(f"versionName from git tag {actual_version!r} normalises to "
                         f"{expected_name!r} from version.properties")
    else:
        checker.check(False,
                      f"versionName mismatch: expected {expected_name}, got {actual_version}")

    # 2. Signature and signer identity.
    signed = subprocess.run([apksigner, "verify", "--print-certs", str(apk)],
                            capture_output=True, text=True, check=False)
    if signed.returncode != 0:
        checker.fail(f"apksigner verify failed: {signed.stderr.strip() or signed.stdout.strip()}")
    apk_digests = {normalize_cert(match) for match in
                   re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", signed.stdout)}
    checker.check(bool(apk_digests), "no signing certificate found in the APK")

    expected_digests: set[str] = set()
    if args.expected_cert_sha256:
        expected_digests.add(normalize_cert(args.expected_cert_sha256))
    if args.keystore:
        if not (args.store_password and args.alias):
            raise SystemExit("--keystore requires --store-password and --alias "
                             "(or NEKOSU_KEYSTORE_PASSWORD/NEKOSU_KEY_ALIAS)")
        if not keytool:
            raise SystemExit("keytool is required to derive the release certificate")
        expected_digests.update(cert_from_keystore(keytool, Path(args.keystore),
                                                   args.store_password, args.alias))
    checker.check(bool(expected_digests),
                  "no expected signing certificate provided (use --keystore with alias/password "
                  "or --expected-cert-sha256)")
    if expected_digests:
        checker.check(apk_digests <= expected_digests,
                      "APK signing certificate does not match the release certificate: "
                      f"apk={sorted(apk_digests)} expected={sorted(expected_digests)}")

    # 3. Manifest hygiene.
    manifest = subprocess.run(
        [aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)],
        capture_output=True, text=True, check=False)
    if manifest.returncode != 0:
        checker.fail(f"cannot dump AndroidManifest.xml: {manifest.stderr.strip()}")
    else:
        true_attribute = r"[^\n]*=(?:true|\(type 0x12\)0xffffffff)(?:\s|$)"
        checker.check(
            re.search(r"android:testOnly" + true_attribute, manifest.stdout) is None,
            "release APK must install normally; testOnly is enabled")
        checker.check(
            re.search(r"android:debuggable" + true_attribute, manifest.stdout) is None,
            "release APK must not be debuggable")

    # 4. Archive contents: ABIs, native daemons, assets.
    abis = [abi.strip() for abi in re.split(r"[,\s]+", args.abis) if abi.strip()]
    checker.check(bool(abis), "--abis must name at least one ABI")
    unsupported = [abi for abi in abis if abi not in ABI_MACHINES]
    checker.check(not unsupported,
                  f"unsupported ABI(s): {unsupported}; supported: {', '.join(SUPPORTED_ABIS)}")
    abis = [abi for abi in abis if abi in ABI_MACHINES]
    required = tuple(args.require_entry) if args.require_entry else tuple(required_entries(abis))
    ksud_inputs: dict[str, str] = {}
    env_ksud = os.environ.get("NEKOSU_KSUD")
    if env_ksud:
        ksud_inputs = parse_ksud_args([env_ksud])
    if args.ksud:
        ksud_inputs.update(parse_ksud_args(args.ksud))
    built_ksuds: dict[str, dict[str, object]] = {}
    for abi, path in ksud_inputs.items():
        built_path = Path(path).resolve()
        entry: dict[str, object] = {"path": str(built_path)}
        built_ksuds[abi] = entry
        if not built_path.is_file():
            checker.fail(f"--ksud {abi}: binary not found: {built_path}")
            continue
        check_elf(f"built ksud ({abi})", built_path.read_bytes(), abi, checker)
        markers = extract_ksud_version_markers(built_path.read_bytes())
        entry["built_markers"] = sorted(markers)
        if not markers:
            checker.fail(f"--ksud {abi} {built_path}: cannot find a ksud build banner")
    try:
        with zipfile.ZipFile(apk) as archive:
            bad_entry = archive.testzip()
            checker.check(bad_entry is None, f"APK has a corrupt zip entry: {bad_entry}")
            names = set(archive.namelist())
            for entry in required:
                checker.check(entry in names, f"required APK entry is missing: {entry}")
            packaged_abis = {name.split("/")[1] for name in names
                             if name.startswith("lib/") and name.count("/") >= 2}
            stray_abis = sorted(packaged_abis - set(abis))
            checker.check(not stray_abis, f"unexpected ABI directories in APK: {stray_abis}")
            checker.check(any(name.startswith("assets/") for name in names),
                          "APK embeds no assets/ payload")
            if "assets/github-markdown.css" not in names:
                checker.note("assets/github-markdown.css is absent (documentation styling only)")
            for entry in sorted(required):
                if not entry.startswith("lib/") or entry not in names:
                    continue
                check_elf(entry, archive.read(entry), entry.split("/")[1], checker)
            for abi in abis:
                ksud_entry = f"lib/{abi}/libksud.so"
                abi_report = built_ksuds.setdefault(abi, {})
                if ksud_entry not in names:
                    continue
                packaged_ksud = archive.read(ksud_entry)
                packaged_markers = extract_ksud_version_markers(packaged_ksud)
                abi_report["packaged_markers"] = sorted(packaged_markers)
                if not packaged_markers:
                    checker.fail(f"{ksud_entry}: cannot find a ksud build banner")
                    continue
                if "built_markers" in abi_report:
                    built_markers = set(abi_report["built_markers"])
                    if packaged_markers != built_markers:
                        checker.fail(
                            f"embedded ksud ({abi}) is stale: APK banners "
                            f"{sorted(packaged_markers)} != built banners "
                            f"{sorted(built_markers)}")
                    else:
                        marker = sorted(packaged_markers)[0]
                        if not version_marker_matches(marker, expected_name):
                            checker.fail(
                                f"embedded ksud ({abi}) build version {marker!r} does not match "
                                f"release version {expected_name!r}")
            if args.ksud_sha256:
                ksud_entry = "lib/arm64-v8a/libksud.so"
                if ksud_entry in names:
                    actual_digest = hashlib.sha256(archive.read(ksud_entry)).hexdigest()
                    checker.check(actual_digest == args.ksud_sha256.lower(),
                                  f"embedded ksud is stale: APK {actual_digest}, built "
                                  f"{args.ksud_sha256.lower()}")
    except zipfile.BadZipFile as error:
        checker.fail(f"APK is not a valid zip file: {error}")

    report = {
        "ok": not checker.failures,
        "apk": str(apk),
        "package": actual_name,
        "version_code": actual_code,
        "version_name": actual_version,
        "cert_sha256": sorted(apk_digests),
        "expected_version": {"name": expected_name, "code": expected_code},
        "abis": abis,
        "ksud": built_ksuds,
        "failures": checker.failures,
        "notes": checker.notes,
    }
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
