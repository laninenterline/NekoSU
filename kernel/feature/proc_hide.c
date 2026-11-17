// SPDX-License-Identifier: GPL-2.0
/*
 * Hide NekoSU/root helper processes from cgroup pid listings read by normal
 * apps. The detector enumerates cgroup.procs/tasks and clusters by PPID: our
 * daemons (ksud/sulogd/su) look like abnormal root processes there.
 *
 * The pid numbers are only filtered in the *printed* text of the cgroup show
 * functions; the cgroup and the processes are untouched.
 */
#include <linux/kprobes.h>
#include <linux/seq_file.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/pid.h>
#include <linux/cred.h>
#include <linux/string.h>
#include <linux/kernel.h>

#include "infra/symbol_resolver.h"
#include "compat/kernel_compat.h"
#include "klog.h" // IWYU pragma: keep

#ifndef CONFIG_ARM64

int __init ksu_proc_hide_init(void) { return 0; }
void __exit ksu_proc_hide_exit(void) {}

#else


static unsigned long addr_cgroup_procs_show;
static unsigned long addr_cgroup_pidlist_show;
static unsigned long addr_cgroup_threads_show;

typedef struct pid *(*find_vpid_fn)(int nr);
typedef struct task_struct *(*pid_task_fn)(struct pid *pid, enum pid_type type);
static find_vpid_fn fn_find_vpid;
static pid_task_fn fn_pid_task;

static struct kprobe proc_hide_probe;
static bool proc_hide_registered;
static const char empty_fmt[] = "";

static bool pid_is_hidden(int nr)
{
    struct task_struct *task = NULL;
    const char *comm;

    if (!fn_find_vpid || !fn_pid_task)
        return false;
    /* PIDTYPE_PID == 0 */
    task = fn_pid_task(fn_find_vpid(nr), 0);
    if (!task)
        return false;
    comm = task->comm;
    if (!strncmp(comm, "ksud", 4) || !strncmp(comm, "libksud", 7) ||
        !strncmp(comm, "sulogd", 6) || !strcmp(comm, "su") ||
        !strcmp(comm, "ksu") || !strncmp(comm, "adbd", 4) ||
        !strncmp(comm, "adbroot", 7))
        return true;
    /* The sulogd binary is invoked as `ksud ... sulogd`; older builds show comm "exe". */
    if (!strcmp(comm, "exe") && ksu_get_uid_t(task->cred->uid) == 0)
        return true;
    return false;
}

static bool caller_is_cgroup_show(unsigned long lr)
{
    unsigned long addrs[3] = {
        addr_cgroup_procs_show,
        addr_cgroup_pidlist_show,
        addr_cgroup_threads_show,
    };
    int i;

    for (i = 0; i < 3; i++) {
        if (addrs[i] && lr >= addrs[i] && lr < addrs[i] + 0x400)
            return true;
    }
    return false;
}

static int proc_hide_pre(struct kprobe *p, struct pt_regs *regs)
{
    char fmt[8];
    int pid;
    unsigned long lr = regs->regs[30];

    (void)p;
    /* Only hide from normal apps; root/ksu domains keep the real list. */
    if (ksu_get_uid_t(current_euid()) < 10000)
        return 0;
    if (copy_from_kernel_nofault(fmt, (void *)regs->regs[1], sizeof(fmt)) != 0)
        return 0;
    if (strncmp(fmt, "%d\n", 3) != 0)
        return 0;
    if (!caller_is_cgroup_show(lr))
        return 0;
    pid = (int)regs->regs[2];
    if (pid > 0 && pid_is_hidden(pid)) {
        pr_info_ratelimited("proc_hide: hid pid %d from reader %d\n", pid, current->pid);
        regs->regs[1] = (unsigned long)empty_fmt;
    }
    return 0;
}

int __init ksu_proc_hide_init(void)
{
    int ret;

    addr_cgroup_procs_show = find_kernel_symbol_exact("cgroup_procs_show");
    addr_cgroup_pidlist_show = find_kernel_symbol_exact("cgroup_pidlist_show");
    addr_cgroup_threads_show = find_kernel_symbol_exact("cgroup_threads_show");
    fn_find_vpid = (find_vpid_fn)find_kernel_symbol_exact("find_vpid");
    fn_pid_task = (pid_task_fn)find_kernel_symbol_exact("pid_task");
    if (!fn_find_vpid || !fn_pid_task)
        pr_warn("proc_hide: find_vpid/pid_task unavailable, feature inactive\n");

    proc_hide_probe.symbol_name = "seq_printf";
    proc_hide_probe.pre_handler = proc_hide_pre;
    ret = register_kprobe(&proc_hide_probe);
    if (ret) {
        pr_warn("proc_hide: register_kprobe(seq_printf) failed: %d\n", ret);
        return ret;
    }
    proc_hide_registered = true;
    pr_info("proc_hide: active (cgroup pid lists filtered for app readers)\n");
    return 0;
}

void __exit ksu_proc_hide_exit(void)
{
    if (proc_hide_registered) {
        unregister_kprobe(&proc_hide_probe);
        proc_hide_registered = false;
    }
}

#endif /* CONFIG_ARM64 */
