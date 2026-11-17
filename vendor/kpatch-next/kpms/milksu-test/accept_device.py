#!/usr/bin/env python3
"""TEST ONLY: bounded manual acceptance for the four MilkSU KPM fixtures."""

import argparse
import hashlib
import json
import shlex
import subprocess
import sys
import time
import uuid
from pathlib import Path


FIXTURES = (
    ("echo", "milksu-test-echo.kpm", "milksu-test-echo"),
    ("no_ctl0", "milksu-test-no_ctl0.kpm", "milksu-test-no-ctl0"),
    ("init_error", "milksu-test-init_error.kpm", "milksu-test-init-error"),
    ("exit_error", "milksu-test-exit_error.kpm", "milksu-test-exit-error"),
)
STATE_ROOT = "/data/adb/ksu/MilkSU/kpm"


class AcceptanceFailure(Exception):
    pass


class Device:
    def __init__(self, adb, serial, ksud, timeout, report):
        self.adb = adb
        self.serial = serial
        self.ksud = ksud
        self.timeout = timeout
        self.report = report

    def command(self, arguments):
        command = [self.adb, "-s", self.serial, *arguments]
        try:
            result = subprocess.run(
                command, capture_output=True, text=True, encoding="utf-8",
                errors="replace", timeout=self.timeout, check=False,
            )
        except subprocess.TimeoutExpired as error:
            self.report["commands"].append({
                "arguments": arguments, "timed_out": True,
            })
            raise AcceptanceFailure(
                "ADB command did not return before the timeout; "
                "remote completion is unknown and is NOT a pass"
            ) from error
        self.report["commands"].append({
            "arguments": arguments, "exit_code": result.returncode,
            "stdout": result.stdout[:16384], "stderr": result.stderr[:16384],
        })
        return result

    def root(self, arguments):
        # ADB transmits a remote shell command. Quote both shell boundaries;
        # no user-supplied argument is interpolated into shell program text.
        return self.command(["shell", shlex.join(["su", "-c", shlex.join(arguments)])])

    def kpm(self, *arguments, ok=True, code=None):
        result = self.root([self.ksud, "kpm", *arguments])
        try:
            value = json.loads(result.stdout)
        except (json.JSONDecodeError, TypeError) as error:
            raise AcceptanceFailure(
                f"Native KPM JSON envelope missing: {result.stderr.strip()}"
            ) from error
        require(isinstance(value, dict), "KPM envelope must be an object")
        require(value.get("ok") is ok, f"Unexpected KPM result: {value}")
        require(
            (result.returncode == 0) == ok,
            "Process status and JSON ok flag disagree",
        )
        if ok:
            require(value.get("error") is None, "Successful command has an error")
        else:
            require(isinstance(value.get("error"), dict), "Failure has no error object")
            if code:
                require(value["error"].get("code") == code, f"Expected error {code}: {value}")
        return value

    def stored(self, module_id):
        for attempt in range(3):
            result = self.root(["test", "-f", f"{STATE_ROOT}/{module_id}.kpm"])
            disconnected = any(message in result.stderr for message in (
                "device offline", "device not found", "not found", "no devices",
                "device unauthorized", "closed",
            ))
            if not disconnected:
                break
            if attempt == 2:
                raise AcceptanceFailure("ADB transport unavailable during stored-file verification")
            time.sleep(1)
        require(result.returncode == 0, "Stored fixture was unexpectedly removed")

    def removed(self, module_id):
        result = self.root(["test", "!", "-e", f"{STATE_ROOT}/{module_id}.kpm"])
        require(result.returncode == 0, "Stored fixture was not removed")


def require(condition, message):
    if not condition:
        raise AcceptanceFailure(message)


def check(report, name, condition):
    require(condition, name)
    report["checks"].append({"name": name, "passed": True})


def probe(device):
    status = device.kpm("status")["data"]
    require(isinstance(status, dict), "status data must be an object")
    return status


def run_acceptance(device, modules, report):
    status = probe(device)
    report["engine_status"] = status
    require(
        status.get("supported") is True and status.get("active") is True
        and status.get("configured_enabled") is True
        and status.get("provider") == "milksu" and status.get("can_unload") is True,
        "An active, configured, supported MilkSU engine is required; "
        "with real unload support; a foreign engine or pending reboot is not accepted",
    )
    existing = device.kpm("list")["data"]
    require(isinstance(existing, list), "list data must be an array")
    names = {item[2] for item in FIXTURES}
    require(
        not any(item.get("name") in names for item in existing),
        "Acceptance fixtures already exist; the main agent must clean them after reboot",
    )

    files = {}
    for key, filename, name in FIXTURES:
        source = modules / filename
        require(source.is_file(), f"Missing fixture: {source}")
        data = source.read_bytes()
        require(data[:6] == b"\x7fELF\x02\x01", f"Not an ELF64 LE fixture: {source}")
        files[key] = {
            "source": source, "filename": filename, "name": name,
            "sha256": hashlib.sha256(data).hexdigest(),
        }

    remote = f"/data/local/tmp/milksu-kpm-acceptance-{uuid.uuid4().hex[:12]}"
    report["remote_directory"] = remote
    require(device.command(["shell", "mkdir", "-p", remote]).returncode == 0,
            "Cannot create acceptance scratch directory")
    ids = {}
    report["fixture_ids"] = ids
    for key, _, _ in FIXTURES:
        entry = files[key]
        target = f"{remote}/{entry['filename']}"
        require(device.command(["push", str(entry["source"]), target]).returncode == 0,
                f"Cannot push {key}")
        info = device.kpm("import", target)["data"]
        ids[key] = info["id"]
        check(report, f"{key}: imported digest and name",
              info["id"] == entry["sha256"] and info["name"] == entry["name"])
        check(report, f"{key}: import only, autoload off",
              info["loaded"] is False and info["autoload"] is False)
        device.stored(ids[key])

    marker = f"{remote}/unexpected-shell-expansion"
    literal = f"literal 'single' \"double\" ; $(touch {marker})\nline2"
    device.kpm("load", ids["echo"], "--args", literal)
    info = device.kpm("info", ids["echo"])["data"]
    check(report, "echo: init arguments preserved literally",
          info["args"] == literal and info["loaded"] is True and info["autoload"] is False)
    output = device.kpm("control", ids["echo"], "--args", literal)["data"]["output"]
    check(report, "echo: ctl0 preserves literal arguments", output == f"echo: {literal}")
    marker_result = device.root(["test", "-e", marker])
    check(report, "parameters: no shell expansion occurred", marker_result.returncode == 1)

    device.kpm("load", ids["no_ctl0"], "--args", "no-control")
    info = device.kpm("info", ids["no_ctl0"])["data"]
    check(report, "no_ctl0: real kernel reports no control",
          info["loaded"] is True and info["has_control"] is False)
    device.kpm("control", ids["no_ctl0"], "--args", "test", ok=False, code="no_control")
    check(report, "no_ctl0: rejected without crash", True)

    device.kpm("load", ids["init_error"], "--args", "expect-init-error",
               ok=False, code="kernel_error")
    info = device.kpm("info", ids["init_error"])["data"]
    check(report, "init_error: successful exit cleans up failed init and reports error",
          info["loaded"] is False and info["pending_unload"] is False
          and bool(info["last_error"]) and info["autoload"] is False)
    device.stored(ids["init_error"])
    check(report, "init_error: stored file retained", True)

    device.kpm("load", ids["exit_error"], "--args", "expect-exit-error")
    device.kpm("unload", ids["exit_error"], ok=False, code="kernel_error")
    info = device.kpm("info", ids["exit_error"])["data"]
    check(report, "exit_error: failed exit stays resident and reports error",
          info["loaded"] is True and info["pending_unload"] is True
          and bool(info["last_error"]) and info["autoload"] is False)
    device.stored(ids["exit_error"])
    check(report, "exit_error: stored file retained", True)

    for key in ("echo", "no_ctl0"):
        device.kpm("unload", ids[key])
        info = device.kpm("info", ids[key])["data"]
        check(report, f"{key}: completed exit removes the runtime instance immediately",
              info["loaded"] is False and info["pending_unload"] is False
              and info["autoload"] is False)
        device.stored(ids[key])
        device.kpm("load", ids[key], "--args", "reload-after-unload")
        check(report, f"{key}: unloaded module can reload in the same boot",
              device.kpm("info", ids[key])["data"]["loaded"] is True)
        device.kpm("remove", ids[key])
        device.removed(ids[key])
        check(report, f"{key}: remove unloads and deletes its stored file", True)

    device.kpm("remove", ids["init_error"])
    device.removed(ids["init_error"])
    check(report, "init_error: nonresident failed module can be removed immediately", True)
    device.kpm("remove", ids["exit_error"], ok=False, code="kernel_error")
    device.stored(ids["exit_error"])
    check(report, "exit_error: pending failed exit preserves its file", True)
    final = device.kpm("list")["data"]
    fixtures = [item for item in final if item["id"] in ids.values()]
    check(report, "only the failed-exit fixture remains resident and pending",
          len(fixtures) == 1 and fixtures[0]["id"] == ids["exit_error"] and all(
              item["loaded"] is True and item["pending_unload"] is True
              and item["autoload"] is False for item in fixtures
          ))
    check(report, "pre-existing modules remain unchanged",
          [item for item in final if item["id"] not in ids.values()] == existing)
    report["final_modules"] = fixtures
    report["cleanup"] = (
        "The failed-exit fixture requires a reboot to end residency. After "
        "reboot, remove only its listed fixture ID; the other fixtures were removed."
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--ksud", required=True, help="Absolute path to the remote MilkSU ksud")
    parser.add_argument("--modules-dir", type=Path, default=Path("out/test-modules"))
    parser.add_argument("--timeout", type=float, default=20)
    parser.add_argument("--execute", action="store_true", help="Import and manually load TEST ONLY fixtures")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    require(args.ksud.startswith("/"), "--ksud must be an absolute Android path")
    require(0 < args.timeout <= 60, "--timeout must be between 0 and 60 seconds")
    report = {
        "test_only": True, "serial": args.serial, "ksud": args.ksud,
        "mode": "execute" if args.execute else "probe_only",
        "commands": [], "checks": [],
    }
    device = Device(args.adb, args.serial, args.ksud, args.timeout, report)
    try:
        if args.execute:
            run_acceptance(device, args.modules_dir, report)
        else:
            report["engine_status"] = probe(device)
        report["result"] = "passed" if args.execute else "probe_only"
        status = 0
    except (AcceptanceFailure, OSError, KeyError, TypeError) as error:
        report["result"] = "failed" if report.get("fixture_ids") else "blocked"
        report["error"] = str(error)
        status = 1 if report["result"] == "failed" else 2
    report["passed_checks"] = len(report["checks"])
    encoded = json.dumps(report, ensure_ascii=True, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(encoded, encoding="utf-8")
    sys.stdout.write(encoded)
    return status


if __name__ == "__main__":
    sys.exit(main())
