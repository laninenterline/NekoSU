/* SPDX-License-Identifier: GPL-2.0-only */
/* Minimal vmlinux BTF reader for the AVC log spoof feature (ported from MikSU). */
#include "infra/btf_parser.h"

#if defined(__aarch64__) && defined(CONFIG_KPROBES) && defined(MODULE) && defined(CONFIG_DEBUG_INFO_BTF)
struct seq_file;
struct bpf_prog;

#include <linux/bpf.h>
#include <linux/btf.h>
#include <linux/err.h>
#include <linux/errno.h>
#include <linux/string.h>

#include "infra/symbol_resolver.h"
#include "klog.h" // IWYU pragma: keep

typedef struct btf *(*ksu_btf_get_vmlinux_t)(void);
typedef s32 (*ksu_btf_find_by_name_kind_t)(struct btf *btf, const char *name, u32 kind);
typedef const char *(*ksu_btf_name_by_offset_t)(const struct btf *btf, u32 offset);
typedef const struct btf_type *(*ksu_btf_skip_modifiers_t)(const struct btf *btf, u32 id, u32 *res_id);

static ksu_btf_get_vmlinux_t ksu_btf_get_vmlinux;
static ksu_btf_find_by_name_kind_t ksu_btf_find_by_name_kind;
static ksu_btf_name_by_offset_t ksu_btf_name_by_offset;
static ksu_btf_skip_modifiers_t ksu_btf_skip_modifiers;

int ksu_btf_parser_init(void)
{
    ksu_btf_get_vmlinux = (ksu_btf_get_vmlinux_t)find_kernel_symbol_exact("bpf_get_btf_vmlinux");
    ksu_btf_find_by_name_kind = (ksu_btf_find_by_name_kind_t)find_kernel_symbol_exact("btf_find_by_name_kind");
    ksu_btf_name_by_offset = (ksu_btf_name_by_offset_t)find_kernel_symbol_exact("btf_name_by_offset");
    ksu_btf_skip_modifiers = (ksu_btf_skip_modifiers_t)find_kernel_symbol_exact("btf_type_skip_modifiers");

    if (!ksu_btf_get_vmlinux || !ksu_btf_find_by_name_kind || !ksu_btf_name_by_offset || !ksu_btf_skip_modifiers) {
        pr_warn("avc_spoof: vmlinux BTF helpers are unavailable\n");
        ksu_btf_get_vmlinux = NULL;
        ksu_btf_find_by_name_kind = NULL;
        ksu_btf_name_by_offset = NULL;
        ksu_btf_skip_modifiers = NULL;
        return -EOPNOTSUPP;
    }

    return 0;
}

int ksu_btf_avc_sid_regs(unsigned int *source, unsigned int *target)
{
    struct btf *btf;
    const struct btf_type *function, *prototype, *type;
    const struct btf_param *params;
    const char *name;
    u32 count, i;
    s32 id;

    if (!source || !target)
        return -EINVAL;

    if (!ksu_btf_get_vmlinux || !ksu_btf_find_by_name_kind || !ksu_btf_name_by_offset || !ksu_btf_skip_modifiers)
        return -EOPNOTSUPP;

    btf = ksu_btf_get_vmlinux();
    if (IS_ERR_OR_NULL(btf))
        return btf ? PTR_ERR(btf) : -ENOENT;

    id = ksu_btf_find_by_name_kind(btf, "slow_avc_audit", BTF_KIND_FUNC);
    if (id < 0)
        return id;

    function = btf_type_by_id(btf, id);
    if (!function)
        return -EINVAL;

    prototype = btf_type_by_id(btf, function->type);
    if (!prototype || BTF_INFO_KIND(prototype->info) != BTF_KIND_FUNC_PROTO)
        return -EINVAL;

    count = BTF_INFO_VLEN(prototype->info);
    /* 8 without the leading selinux_state, 9 with it (kernel < 6.4). */
    if (count != 8 && count != 9)
        return -EOPNOTSUPP;

    *source = count == 9 ? 1 : 0;
    *target = *source + 1;
    params = (const struct btf_param *)(prototype + 1);

    if (*source) {
        type = ksu_btf_skip_modifiers(btf, params[0].type, NULL);
        if (!type || BTF_INFO_KIND(type->info) != BTF_KIND_PTR)
            return -EOPNOTSUPP;
        type = ksu_btf_skip_modifiers(btf, type->type, NULL);
        if (!type || BTF_INFO_KIND(type->info) != BTF_KIND_STRUCT)
            return -EOPNOTSUPP;
        name = ksu_btf_name_by_offset(btf, type->name_off);
        if (!name || strcmp(name, "selinux_state"))
            return -EOPNOTSUPP;
    }

    for (i = *source; i <= *target; i++) {
        name = ksu_btf_name_by_offset(btf, params[i].name_off);
        if (!name || strcmp(name, i == *source ? "ssid" : "tsid"))
            return -EOPNOTSUPP;
        type = ksu_btf_skip_modifiers(btf, params[i].type, NULL);
        if (!type || BTF_INFO_KIND(type->info) != BTF_KIND_INT || type->size != sizeof(u32))
            return -EOPNOTSUPP;
    }

    pr_info("avc_spoof: slow_avc_audit ssid=arg%u tsid=arg%u\n", *source, *target);
    return 0;
}
#endif /* MODULE && CONFIG_DEBUG_INFO_BTF */
