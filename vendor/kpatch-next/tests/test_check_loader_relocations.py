import unittest

from check_loader_relocations import unsafe_relocations


class LoaderRelocationsTest(unittest.TestCase):
    def relocation(self, section, kind):
        return (
            f"Relocation section '{section}' at offset 0x10 contains 1 entry:\n"
            f"00000000 00000000 {kind} .rodata.str1.8 + 58\n"
        )

    def test_absolute_rodata_addresses_are_rejected(self):
        for section in (".rela.rodata", ".rela.rodata.str1.8"):
            for kind in ("R_AARCH64_ABS64", "R_AARCH64_ABS32", "R_AARCH64_ABS16"):
                with self.subTest(section=section, kind=kind):
                    self.assertEqual(
                        len(unsafe_relocations(self.relocation(section, kind))), 1
                    )

    def test_debug_addresses_do_not_require_runtime_fixups(self):
        self.assertEqual(
            unsafe_relocations(self.relocation(".rela.debug_info", "R_AARCH64_ABS64")),
            [],
        )

    def test_relative_text_addresses_are_accepted(self):
        self.assertEqual(
            unsafe_relocations(
                self.relocation(".rela.text", "R_AARCH64_ADR_PREL_PG_HI21")
            ),
            [],
        )


if __name__ == "__main__":
    unittest.main()
