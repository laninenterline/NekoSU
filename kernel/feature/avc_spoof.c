/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * AVC log spoof: replace the root (su) SELinux domain with a priv_app domain in
 * SELinux audit records, so root-hiding detectors cannot grep for
 * "avc: denied ... scontext=u:r:ksu:s0".
 *
 * Ported from KernelSU-Next extras.c (via MikSU kernel/feature/avc_spoof.c) to
 * the NekoSU LKM/kprobe architecture. A kprobe at slow_avc_audit() rewrites the
 * ssid/tsid argument registers in the pt_regs handed to the audit machinery;
 * vmlinux BTF tells us which registers those are, because the leading
 * `struct selinux_state *` argument was dropped in 6.4.
 */
#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/kprobes.h>
#include <linux/mutex.h>
#include <linux/security.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/version.h>

#include "feature/avc_spoof.h"
#include "infra/btf_parser.h"
#include "infra/symbol_resolver.h"
#include "policy/feature.h"
#include "selinux/selinux.h"
#include "compat/kernel_compat.h"

#include "ksu.h"
#include "klog.h" // IWYU pragma: keep

/*
 * Only arm64 LKM builds whose target kernel has kprobes and vmlinux BTF can use
 * this feature. Everywhere else the handler is never registered, so userspace
 * reports the feature as unsupported ("feature check avc_spoof").
 */
#if defined(__aarch64__) && defined(CONFIG_KPROBES) && defined(MODULE) && defined(CONFIG_DEBUG_INFO_BTF)
#define KSU_AVC_SPOOF_ARM64_KPROBE 1
#include <asm/insn.h>
#include <linux/byteorder/generic.h>
#endif

static DEFINE_MUTEX(avc_lock);
/*
 * Opt-in: disabled by default. The manager/ksud feature toggle (avc_spoof) and
 * the persisted feature config decide whether audit records are spoofed, so a
 * fresh installation does not silently hide SELinux denials.
 */
static bool avc_requested;
static bool avc_ready;
static bool avc_running;
static bool avc_registered;

static int avc_feature_get(u64 *value)
{
    if (!value)
        return -EINVAL;

    mutex_lock(&avc_lock);
    *value = avc_ready ? avc_running : avc_requested;
    mutex_unlock(&avc_lock);
    return 0;
}

#ifdef KSU_AVC_SPOOF_ARM64_KPROBE
static u32 root_sid;
static u32 app_sid;
static u32 susfs_avc_sid;
static unsigned int avc_source_reg = 0;
static unsigned int avc_target_reg = 1;

/*
 * SUSFS (built-in or guard LKM) may spoof the same audit record before this
 * kprobe runs. Its stock replacement context is u:r:priv_app:s0, which may
 * differ from ours (priv_app plus categories); upgrade it to the KernelSU
 * context so the KernelSU feature keeps precedence when both are enabled.
 */
static inline unsigned long avc_spoof_sid(unsigned long value)
{
    u32 sid = (u32)value;

    if (sid == root_sid || (susfs_avc_sid && sid == susfs_avc_sid))
        return app_sid;
    return value;
}

static int avc_pre_handler(struct kprobe *kp, struct pt_regs *regs)
{
    if (!READ_ONCE(avc_running))
        return 0;

    regs->regs[avc_source_reg] = avc_spoof_sid(regs->regs[avc_source_reg]);
    regs->regs[avc_target_reg] = avc_spoof_sid(regs->regs[avc_target_reg]);
    return 0;
}

static struct kprobe avc_probe = {
    .symbol_name = "slow_avc_audit",
    .pre_handler = avc_pre_handler,
};

static int avc_enable_locked(void)
{
    static const char replacement[] = "u:r:priv_app:s0:c512,c768";
    unsigned long address;
    u32 instructions[4];
    int i, ret;

    if (avc_running)
        return 0;

    /*
     * 6.4+ signature is slow_avc_audit(u32 ssid, u32 tsid, ...): the two SIDs
     * are the first two argument registers, so no vmlinux BTF lookup is needed.
     * This also avoids an indirect call to btf_find_by_name_kind(), which is
     * rejected by KCFI on Android kernels. KernelSU-Next uses the same fixed
     * register layout (PT_REGS_PARM2 for tsid).
     */
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 4, 0)
    avc_source_reg = 0;
    avc_target_reg = 1;
#else
    ret = ksu_btf_avc_sid_regs(&avc_source_reg, &avc_target_reg);
    if (ret)
        return ret;
#endif

    address = find_kernel_symbol_exact("slow_avc_audit");
    if (!address || copy_from_kernel_nofault(instructions, (void *)address, sizeof(instructions)))
        return -EOPNOTSUPP;

    /* KPatch installs a branch trampoline at the entry before the root LKM loads. */
    for (i = 0; i < (int)ARRAY_SIZE(instructions); i++) {
        u32 instruction = le32_to_cpu(instructions[i]);

        if (aarch64_insn_is_b(instruction) || aarch64_insn_is_br(instruction))
            return -EBUSY;
    }

    ret = security_secctx_to_secid(KERNEL_SU_CONTEXT, strlen(KERNEL_SU_CONTEXT), &root_sid);
    if (ret || !root_sid)
        return ret ?: -EOPNOTSUPP;

    ret = security_secctx_to_secid(replacement, strlen(replacement), &app_sid);
    if (ret || !app_sid)
        return ret ?: -EOPNOTSUPP;

    /* Optional: recognise the stock SUSFS AVC spoof context so a record that
     * SUSFS already rewrote is upgraded to the KernelSU context above. */
    if (security_secctx_to_secid("u:r:priv_app:s0", sizeof("u:r:priv_app:s0") - 1, &susfs_avc_sid))
        susfs_avc_sid = 0;

    ret = register_kprobe(&avc_probe);
    if (ret) {
        avc_probe.addr = NULL;
        avc_probe.flags &= ~KPROBE_FLAG_DISABLED;
        return ret;
    }

    WRITE_ONCE(avc_running, true);
    pr_info("avc_spoof: hook installed (slow_avc_audit)\n");
    return 0;
}

static void avc_disable_locked(void)
{
    if (!avc_running)
        return;

    WRITE_ONCE(avc_running, false);
    unregister_kprobe(&avc_probe);
    avc_probe.addr = NULL;
    avc_probe.flags &= ~KPROBE_FLAG_DISABLED;
    pr_info("avc_spoof: hook removed\n");
}
#else
static int avc_enable_locked(void)
{
    return -EOPNOTSUPP;
}

static void avc_disable_locked(void)
{
}
#endif /* KSU_AVC_SPOOF_ARM64_KPROBE */

static int avc_feature_set(u64 value)
{
    bool enabled = value != 0;
    int ret = 0;

    if (value > 1)
        return -EINVAL;

    mutex_lock(&avc_lock);
    if (avc_ready && enabled)
        ret = avc_enable_locked();
    else if (!enabled)
        avc_disable_locked();
    if (!ret)
        avc_requested = enabled;
    mutex_unlock(&avc_lock);
    return ret;
}

static const struct ksu_feature_handler avc_handler = {
    .feature_id = KSU_FEATURE_AVC_SPOOF,
    .name = "avc_spoof",
    .get_handler = avc_feature_get,
    .set_handler = avc_feature_set,
};

int ksu_avc_spoof_get(bool *enabled)
{
    u64 value = 0;
    int ret;

    if (!enabled)
        return -EINVAL;
    if (!avc_registered)
        return -EOPNOTSUPP;

    ret = avc_feature_get(&value);
    *enabled = value != 0;
    return ret;
}

int ksu_avc_spoof_set(bool enabled)
{
    if (!avc_registered)
        return -EOPNOTSUPP;

    return ksu_set_feature(KSU_FEATURE_AVC_SPOOF, enabled);
}

void __init ksu_avc_spoof_init(void)
{
#ifdef KSU_AVC_SPOOF_ARM64_KPROBE
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 4, 0)
    /* Fixed ABI: ssid/tsid are arg1/arg2; no BTF lookup at all. */
    avc_source_reg = 0;
    avc_target_reg = 1;
    pr_info("avc_spoof: slow_avc_audit uses fixed arg regs (6.4+ ABI)\n");
#else
    int ret = ksu_btf_parser_init();

    if (ret) {
        pr_warn("avc_spoof: vmlinux BTF helpers unavailable: %d\n", ret);
        return;
    }

    ret = ksu_btf_avc_sid_regs(&avc_source_reg, &avc_target_reg);

    if (ret) {
        pr_warn("avc_spoof: slow_avc_audit prototype not supported: %d\n", ret);
        return;
    }
#endif

    if (ksu_register_feature_handler(&avc_handler)) {
        pr_err("Failed to register avc_spoof feature handler\n");
        return;
    }

    avc_registered = true;
    pr_info("avc_spoof: feature registered (default %s)\n", avc_requested ? "enabled" : "disabled");
#else
    pr_info("avc_spoof: not supported by this kernel build\n");
#endif
}

void ksu_avc_spoof_late_init(void)
{
    int ret = 0;

    if (!avc_registered)
        return;

    mutex_lock(&avc_lock);
    avc_ready = true;
    if (avc_requested)
        ret = avc_enable_locked();
    else
        avc_disable_locked();
    mutex_unlock(&avc_lock);

    if (ret)
        pr_warn("avc_spoof: activation failed: %d\n", ret);
}

void __exit ksu_avc_spoof_exit(void)
{
    mutex_lock(&avc_lock);
    avc_disable_locked();
    avc_ready = false;
    mutex_unlock(&avc_lock);

    if (avc_registered) {
        ksu_unregister_feature_handler(KSU_FEATURE_AVC_SPOOF);
        avc_registered = false;
    }
}
