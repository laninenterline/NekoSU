// SPDX-License-Identifier: GPL-2.0
/*
 * Detectors (春秋 4.6.2 "免解设备") treat an app process whose getppid() is
 * larger than 5000 as reparented/injected: they expect the system zygote pid to
 * be small. A zygote that was restarted (soft reboot, `stop`/`start`) can easily
 * get a pid above 5000, which makes every app look suspicious.
 *
 * For non-root callers, clamp the reported parent pid to a plausible small
 * value when the real parent pid is above the threshold. The real process tree
 * is not modified.
 */
#include <linux/kprobes.h>
#include <linux/sched.h>
#include <linux/cred.h>
#include <linux/seq_file.h>
#include <linux/string.h>

#include "compat/kernel_compat.h"
#include "klog.h" // IWYU pragma: keep

#define CLAMP_THRESHOLD 5000
#define CLAMP_VALUE 500

#ifndef CONFIG_ARM64

int __init ksu_getppid_clamp_init(void) { return 0; }
void __exit ksu_getppid_clamp_exit(void) {}

#else

static struct kprobe getppid_probe;
static bool getppid_registered;
static struct kprobe status_probe;
static bool status_registered;
static struct kprobe putnum_probe;
static bool putnum_registered;

static int getppid_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct task_struct *parent;

    (void)p;
    if (ksu_get_uid_t(current_euid()) < 10000)
        return 0;
    parent = current->real_parent;
    if (parent && parent->pid > CLAMP_THRESHOLD) {
        regs->regs[0] = CLAMP_VALUE;
        regs->pc = regs->regs[30];
    }
    return 0;
}

/*
 * Detector probe processes read /proc/self/status instead of calling
 * getppid(); rewrite the PPid line and the Seccomp line for non-root readers
 * so the same clamp applies to both access paths.
 */
static int status_write_pre(struct kprobe *p, struct pt_regs *regs)
{
    char fmt[32];
    const char *f = (const char *)regs->regs[1];

    (void)p;
    if (ksu_get_uid_t(current_euid()) < 10000)
        return 0;
    if (copy_from_kernel_nofault(fmt, f, sizeof(fmt)) != 0)
        return 0;
    if (strncmp(fmt, "PPid:\t%d\n", 9) == 0 && fmt[9] == '\0') {
        if ((int)regs->regs[2] > CLAMP_THRESHOLD)
            regs->regs[2] = CLAMP_VALUE;
    } else if (strncmp(fmt, "Seccomp:\t%u\n", 12) == 0 && fmt[12] == '\0') {
        if ((unsigned int)regs->regs[2] == 0)
            regs->regs[2] = 2;
    }
    return 0;
}

/*
 * Modern kernels print /proc/<pid>/status via seq_put_decimal_ull() instead of
 * seq_printf(), e.g. seq_put_decimal_ull(m, "PPid:\t", ppid).
 */
static int putnum_pre(struct kprobe *p, struct pt_regs *regs)
{
    char delim[24];
    const char *d = (const char *)regs->regs[1];
    unsigned long long num = regs->regs[2];

    (void)p;
    if (ksu_get_uid_t(current_euid()) < 10000)
        return 0;
    if (copy_from_kernel_nofault(delim, d, sizeof(delim)) != 0)
        return 0;
    if (strncmp(delim, "PPid:\t", 6) == 0 && delim[6] == '\0') {
        if (num > CLAMP_THRESHOLD)
            regs->regs[2] = CLAMP_VALUE;
    } else if (strncmp(delim, "Seccomp:\t", 9) == 0 && delim[9] == '\0') {
        if (num == 0)
            regs->regs[2] = 2;
    }
    return 0;
}

int __init ksu_getppid_clamp_init(void)
{
    int ret;

    getppid_probe.symbol_name = "__arm64_sys_getppid";
    getppid_probe.pre_handler = getppid_pre;
    ret = register_kprobe(&getppid_probe);
    if (ret) {
        pr_warn("getppid_clamp: register_kprobe failed: %d\n", ret);
        return ret;
    }
    getppid_registered = true;

    status_probe.symbol_name = "seq_printf";
    status_probe.pre_handler = status_write_pre;
    if (register_kprobe(&status_probe))
        pr_warn("getppid_clamp: seq_printf probe failed\n");
    else
        status_registered = true;

    putnum_probe.symbol_name = "seq_put_decimal_ull";
    putnum_probe.pre_handler = putnum_pre;
    if (register_kprobe(&putnum_probe))
        pr_warn("getppid_clamp: seq_put_decimal_ull probe failed\n");
    else
        putnum_registered = true;

    pr_info("getppid_clamp: active (app getppid/status clamped when zygote pid > %d)\n",
            CLAMP_THRESHOLD);
    return 0;
}

void __exit ksu_getppid_clamp_exit(void)
{
    if (putnum_registered) {
        unregister_kprobe(&putnum_probe);
        putnum_registered = false;
    }
    if (status_registered) {
        unregister_kprobe(&status_probe);
        status_registered = false;
    }
    if (getppid_registered) {
        unregister_kprobe(&getppid_probe);
        getppid_registered = false;
    }
}

#endif
