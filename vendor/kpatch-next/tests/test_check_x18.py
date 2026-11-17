"""Host-only tests for the read-only x18 ABI auditor."""

import unittest

from check_x18 import classify, instructions_using_x18


class X18AuditTests(unittest.TestCase):
    def test_detects_both_register_widths(self):
        records = instructions_using_x18(
            " 11b14: b9802412 ldrsw x18, [x0, #36]\n"
            " 14020: 2a0d03f2 mov w18, w13\n"
        )
        self.assertEqual(len(records), 2)

    def test_ignores_other_registers_and_comments(self):
        self.assertEqual(instructions_using_x18(
            " 10000: aa0d03f2 mov x19, x13 // x18 is preserved\n"
        ), [])

    def test_plain_c_use_fails(self):
        records = [{"address": "0x11b14", "instruction": "ldrsw x18, [x0, #36]"}]
        violations, assembly = classify(records, [("relo_ldr", "/source/base/hook.c:199")])
        self.assertEqual(len(violations), 1)
        self.assertEqual(assembly, [])

    def test_assembly_save_restore_is_reported_separately(self):
        records = [{"address": "0xe288", "instruction": "stp x8, x18, [sp, #-16]!"}]
        violations, assembly = classify(records, [("setup", "/source/base/setup1.S:283")])
        self.assertEqual(violations, [])
        self.assertEqual(len(assembly), 1)

    def test_missing_debug_info_does_not_pass(self):
        records = [{"address": "0x1234", "instruction": "mov x18, x0"}]
        violations, assembly = classify(records, [("unknown", "??:0")])
        self.assertEqual(len(violations), 1)
        self.assertEqual(assembly, [])


if __name__ == "__main__":
    unittest.main()
