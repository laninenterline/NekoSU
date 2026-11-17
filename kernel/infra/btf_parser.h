#ifndef __KSU_BTF_PARSER_H
#define __KSU_BTF_PARSER_H

#include <linux/types.h>

#if defined(__aarch64__) && defined(CONFIG_KPROBES) && defined(MODULE) && defined(CONFIG_DEBUG_INFO_BTF)
/*
 * Resolve the BTF helpers through kallsyms instead of linking them directly:
 * vmlinux does not export every BTF helper to modules, and a single unresolved
 * symbol would prevent the whole kernelsu.ko from loading.
 */
int ksu_btf_parser_init(void);

/*
 * Resolve the pt_regs indices that hold the `ssid` and `tsid` arguments of
 * slow_avc_audit() from vmlinux BTF. The leading `struct selinux_state *`
 * argument was dropped in 6.4, so the two indices depend on the kernel
 * version; BTF is the only reliable way to tell them apart.
 */
int ksu_btf_avc_sid_regs(unsigned int *source, unsigned int *target);
#endif

#endif /* __KSU_BTF_PARSER_H */
