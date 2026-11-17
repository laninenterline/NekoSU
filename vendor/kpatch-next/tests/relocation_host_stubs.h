#ifndef MILKSU_RELOCATION_HOST_STUBS_H
#define MILKSU_RELOCATION_HOST_STUBS_H

#include <elf.h>
#include <errno.h>
#include <stdbool.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>

typedef uint64_t u64;
typedef int64_t s64;
typedef uint32_t u32;
typedef int32_t s32;
typedef int16_t s16;
#define BIT(bit) (UINT64_C(1) << (bit))
#define R_ARM_NONE 0
#undef R_AARCH64_NONE
#define R_AARCH64_NONE 256
/* Match the vendor's variadic logger without adding libc format attributes. */
static void pr_err(const char *format, ...)
{
    va_list args;
    va_start(args, format);
    vfprintf(stderr, format, args);
    va_end(args);
}
struct module;

enum aarch64_insn_imm_type {
    AARCH64_INSN_IMM_ADR, AARCH64_INSN_IMM_26, AARCH64_INSN_IMM_19,
    AARCH64_INSN_IMM_16, AARCH64_INSN_IMM_14, AARCH64_INSN_IMM_12,
    AARCH64_INSN_IMM_9, AARCH64_INSN_IMM_7, AARCH64_INSN_IMM_6,
    AARCH64_INSN_IMM_S, AARCH64_INSN_IMM_R, AARCH64_INSN_IMM_MAX
};

/* Independent encoder used only to test the production relocation arithmetic. */
static u32 aarch64_insn_encode_immediate(enum aarch64_insn_imm_type type, u32 insn, u64 imm)
{
    u32 mask;
    unsigned int shift;
    switch (type) {
    case AARCH64_INSN_IMM_ADR:
        insn &= ~((3u << 29) | (0x7ffffu << 5));
        return insn | ((imm & 3) << 29) | (((imm >> 2) & 0x7ffff) << 5);
    case AARCH64_INSN_IMM_26: mask = (1u << 26) - 1; shift = 0; break;
    case AARCH64_INSN_IMM_19: mask = (1u << 19) - 1; shift = 5; break;
    case AARCH64_INSN_IMM_16: mask = (1u << 16) - 1; shift = 5; break;
    case AARCH64_INSN_IMM_14: mask = (1u << 14) - 1; shift = 5; break;
    case AARCH64_INSN_IMM_12: mask = (1u << 12) - 1; shift = 10; break;
    default: return 0;
    }
    return (insn & ~(mask << shift)) | ((imm & mask) << shift);
}

#endif
