#!/usr/bin/env python3
"""Exercise real kptools -f with bounded gzip and IKCONFIG marker fixtures."""

import argparse
import gzip
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


START = b"IKCFG_ST"
END = b"IKCFG_ED"
CONFIG = b"CONFIG_KALLSYMS=y\nCONFIG_KALLSYMS_ALL=y\n# CONFIG_KPM is not set\n"
LIMIT = 8 * 1024 * 1024
TOOL = None


class IkconfigTests(unittest.TestCase):
    def invoke(self, image, *, output=None, cwd=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "kernel"
            path.write_bytes(image)
            return subprocess.run(
                [str(TOOL), "-i", str(path), "-f"],
                stdout=output if output is not None else subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=cwd or directory,
                timeout=20,
                check=False,
            )

    def image(self, config=CONFIG):
        return b"kernel-prefix" + START + gzip.compress(config, mtime=0) + END + b"suffix"

    def fails(self, image):
        result = self.invoke(image)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(result.stderr)
        self.assertLessEqual(len(result.stdout), LIMIT)
        return result

    def test_valid_gzip_config_exact_output(self):
        result = self.invoke(self.image())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, CONFIG)

    def test_no_temporary_file_in_read_only_working_directory(self):
        if os.name != "posix":
            self.skipTest("read-only /proc check requires Linux")
        result = self.invoke(self.image(), cwd="/proc")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, CONFIG)

    def test_end_marker_before_start_is_not_used(self):
        result = self.invoke(END + self.image())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, CONFIG)

    def test_missing_start_or_end(self):
        self.fails(b"prefix" + gzip.compress(CONFIG) + END)
        self.fails(START + gzip.compress(CONFIG))

    def test_reversed_markers_do_not_underflow(self):
        self.fails(END + b"prefix" + START + b"trailing")

    def test_empty_marker_range(self):
        self.fails(START + END)

    def test_bad_gzip_header(self):
        self.fails(START + b"not-gzip" + END)

    def test_truncated_body_and_trailer(self):
        compressed = gzip.compress(CONFIG, mtime=0)
        for missing in (1, 4, 8, len(compressed) - 5):
            with self.subTest(missing=missing):
                self.fails(START + compressed[:-missing] + END)

    def test_checksum_error_propagates_to_process_exit(self):
        compressed = bytearray(gzip.compress(CONFIG, mtime=0))
        compressed[-8] ^= 1
        self.fails(START + compressed + END)

    def test_exact_output_limit(self):
        config = b"#" * LIMIT
        result = self.invoke(self.image(config))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(result.stdout), LIMIT)
        self.assertEqual(result.stdout, config)

    def test_oversized_output_is_rejected(self):
        result = self.fails(self.image(b"#" * (LIMIT + 1)))
        self.assertIn(b"8 MiB", result.stderr)

    def test_trailing_data_is_rejected(self):
        self.fails(START + gzip.compress(CONFIG, mtime=0) + b"junk" + END)

    def test_stdout_error_is_not_success(self):
        if os.name != "posix":
            self.skipTest("/dev/full requires Linux")
        with open("/dev/full", "wb") as output:
            result = self.invoke(self.image(), output=output)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(
            b"write" in result.stderr.lower() or b"flush" in result.stderr.lower(),
            result.stderr,
        )

    def test_tiny_image(self):
        self.fails(b"")
        self.fails(b"IKCFG_ST")


def main():
    global TOOL
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kptools", type=Path, required=True)
    args = parser.parse_args()
    TOOL = args.kptools.resolve(strict=True)
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(IkconfigTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    print(json.dumps({
        "tests": result.testsRun,
        "failures": len(result.failures),
        "errors": len(result.errors),
        "skipped": len(result.skipped),
        "passed": result.testsRun - len(result.failures) - len(result.errors) - len(result.skipped),
        "kptools": str(TOOL),
    }))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
