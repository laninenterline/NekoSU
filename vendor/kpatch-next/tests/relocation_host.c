#define KP_RELOCATION_HOST_TEST
#include "../kernel/patch/module/relo.c"
#include <assert.h>
#include <string.h>

static void absolute_data_test(void)
{
    uint64_t target = 0;
    uint64_t address = UINT64_C(0xffffffe812345678);
    assert(reloc_data(RELOC_OP_ABS, &target, address, 64) == 0);
    assert(target == address);
    assert(reloc_data(RELOC_OP_ABS, &target, 0x12345678, 32) == 0);
    assert(*(uint32_t *)&target == 0x12345678);
    assert(reloc_data(RELOC_OP_ABS, &target, 0x1234, 16) == 0);
    assert(*(uint16_t *)&target == 0x1234);
}

static void fixture_pointer_relocation_test(void)
{
    uint64_t pointers[3] = {0};
    Elf64_Shdr sections[4] = {0};
    Elf64_Sym symbols[3] = {0};
    Elf64_Rela relocations[3] = {0};
    sections[1].sh_addr = (uintptr_t)symbols;
    sections[2].sh_addr = (uintptr_t)pointers;
    sections[2].sh_flags = SHF_ALLOC;
    sections[3].sh_addr = (uintptr_t)relocations;
    sections[3].sh_info = 2;
    sections[3].sh_size = sizeof(relocations);
    symbols[1].st_value = UINT64_C(0xffffffe800001234);
    symbols[2].st_value = UINT64_C(0xffffffe800005678);
    for (int i = 0; i < 3; i++) {
        relocations[i].r_offset = i * 8;
        relocations[i].r_info = ELF64_R_INFO(i % 2 + 1, R_AARCH64_ABS64);
    }
    assert(apply_relocate_add(sections, "", 1, 3, 0) == 0);
    assert(pointers[0] == symbols[1].st_value);
    assert(pointers[1] == symbols[2].st_value);
    assert(pointers[2] == symbols[1].st_value);
}

static void branch_and_page_test(void)
{
    uint32_t call = 0x94000000;
    uint64_t place = (uintptr_t)&call;
    assert(reloc_insn_imm(RELOC_OP_PREL, &call, place + 64, 2, 26,
                          AARCH64_INSN_IMM_26) == 0);
    assert(call == 0x94000010);
    call = 0x94000000;
    assert(reloc_insn_imm(RELOC_OP_PREL, &call, place - 64, 2, 26,
                          AARCH64_INSN_IMM_26) == 0);
    assert(call == 0x97fffff0);
    uint32_t page = 0x90000000;
    place = (uintptr_t)&page;
    assert(reloc_insn_imm(RELOC_OP_PAGE, &page, (place & ~0xffful) + 0x3000,
                          12, 21, AARCH64_INSN_IMM_ADR) == 0);
    uint64_t decoded = ((page >> 29) & 3) | (((page >> 5) & 0x7ffff) << 2);
    assert(decoded == 3);
}

int main(void)
{
    absolute_data_test();
    fixture_pointer_relocation_test();
    branch_and_page_test();
    puts("production ARM64 relocator: PASS (3 groups)");
    return 0;
}
