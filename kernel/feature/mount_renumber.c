// SPDX-License-Identifier: GPL-2.0
/*
 * mountinfo_re.Numberer: make /proc/self/mountinfo look gap-free.
 *
 * Detectors read mnt_id / parent_id / shared / master ids and flag jumps
 * (1,2,3,6,7...) or missing peer-group ids as "hidden mounts". Those holes are
 * created by KernelSU module mounts being created and unmounted during boot, and
 * they cannot be removed from the real mount table.
 *
 * This feature does not touch the mount objects. It hooks seq_printf() and, for
 * the exact format strings used by fs/proc_namespace.c, replaces the numeric
 * arguments with per-process sequential ids before the line is written. Since
 * only the printed text changes, there is no mount lifetime or locking risk.
 */
#include <linux/kprobes.h>
#include <linux/seq_file.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/hash.h>
#include <linux/version.h>

#include "klog.h" // IWYU pragma: keep

#ifndef CONFIG_ARM64

int __init ksu_mount_renumber_init(void) { return 0; }
void __exit ksu_mount_renumber_exit(void) {}

#else


#define MR_MAX_PIDS 256
#define MR_MAP_MAX 64
#define MR_RESET_JIFFIES (HZ * 2)

struct mr_map {
    u32 from;
    u32 to;
};

struct mr_state {
    pid_t pid;
    u32 next_mnt;
    u32 next_grp;
    u32 last_mnt;
    unsigned long last_jiffies;
    int n_mnt;
    int n_grp;
    struct mr_map mnt[MR_MAP_MAX];
    struct mr_map grp[MR_MAP_MAX];
    struct mr_state *next;
};

static struct mr_state *mr_states;
static DEFINE_SPINLOCK(mr_lock);
static struct kprobe mr_probe;
static bool mr_registered;

static struct mr_state *mr_get_state(pid_t pid)
{
    struct mr_state *s;
    unsigned long now = jiffies;

    for (s = mr_states; s; s = s->next) {
        if (s->pid == pid) {
            if (now - s->last_jiffies > MR_RESET_JIFFIES) {
                s->next_mnt = 0;
                s->next_grp = 0;
                s->last_mnt = 0;
                s->n_mnt = 0;
                s->n_grp = 0;
            }
            return s;
        }
    }
    s = kzalloc(sizeof(*s), GFP_ATOMIC);
    if (!s)
        return NULL;
    s->pid = pid;
    s->next = mr_states;
    mr_states = s;
    return s;
}


static u32 mr_lookup(const struct mr_map *map, int count, u32 from)
{
    int i;

    for (i = 0; i < count; i++) {
        if (map[i].from == from)
            return map[i].to;
    }
    return 0;
}

static u32 mr_map_id(struct mr_map *map, int *count, u32 from, u32 *counter)
{
    int i;

    for (i = 0; i < *count; i++) {
        if (map[i].from == from)
            return map[i].to;
    }
    if (*count < MR_MAP_MAX) {
        map[*count].from = from;
        map[*count].to = ++(*counter);
        (*count)++;
        return map[*count - 1].to;
    }
    return ++(*counter);
}

static bool mr_fmt_is(const char __user *fmt, const char *want, int len)
{
    char buf[32];

    if (copy_from_kernel_nofault(buf, fmt, len + 1) != 0)
        return false;
    return strncmp(buf, want, len) == 0 && buf[len] == '\0';
}

static int mr_pre(struct kprobe *p, struct pt_regs *regs)
{
    void __user *fmt = (void __user *)regs->regs[1];
    struct mr_state *s;
    unsigned long flags;

    char format[32];
    bool mnt_line, grp_line;

    (void)p;
    /*
     * seq_printf() is called all over the kernel. Check the format first and
     * bail out early for everything except the three mountinfo id formats, so
     * unrelated procfs output does not allocate state or take the lock.
     */
    if (copy_from_kernel_nofault(format, fmt, sizeof(format)) != 0)
        return 0;
    mnt_line = strncmp(format, "%i %i %u:%u ", 12) == 0 && format[12] == '\0';
    /*
     * fs/proc_namespace.c:164-170 uses these exact strings (leading space, no
     * trailing space): " shared:%i", " master:%i", " propagate_from:%i".
     */
    grp_line = (strncmp(format, " shared:%i", 10) == 0 && format[10] == '\0') ||
               (strncmp(format, " master:%i", 10) == 0 && format[10] == '\0') ||
               (strncmp(format, " propagate_from:%i", 18) == 0 && format[18] == '\0');
    if (!mnt_line && !grp_line)
        return 0;

    spin_lock_irqsave(&mr_lock, flags);
    s = mr_get_state(current->pid);
    if (!s) {
        spin_unlock_irqrestore(&mr_lock, flags);
        return 0;
    }
    s->last_jiffies = jiffies;

    if (mnt_line) {
        u32 id = (u32)regs->regs[2];
        u32 parent = (u32)regs->regs[3];

        /*
         * Mountinfo is printed in mount-tree order, NOT sorted by id, so do
         * not reset on an id decrease: that would restart the counter in the
         * middle of one read and reintroduce gaps. Idle-time reset in
         * mr_get_state() is enough.
         */
        s->last_mnt = id;
        regs->regs[2] = mr_map_id(s->mnt, &s->n_mnt, id, &s->next_mnt);
        /*
         * Parent ids must reuse an already-assigned sequence number. If the
         * parent was not printed (or was hidden), fall back to 0 instead of
         * allocating a new id - allocating here burns sequence numbers and
         * reintroduces the very gaps this feature is meant to remove.
         */
        regs->regs[3] = parent ? mr_lookup(s->mnt, s->n_mnt, parent) : 0;
    } else {
        u32 gid = (u32)regs->regs[2];
        regs->regs[2] = mr_map_id(s->grp, &s->n_grp, gid, &s->next_grp);
    }
    spin_unlock_irqrestore(&mr_lock, flags);
    return 0;
}

int __init ksu_mount_renumber_init(void)
{
    int ret;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 7, 0)
    mr_probe.symbol_name = "seq_printf";
#else
    mr_probe.symbol_name = "seq_printf";
#endif
    mr_probe.pre_handler = mr_pre;
    ret = register_kprobe(&mr_probe);
    if (ret) {
        pr_warn("mount_renumber: register_kprobe(seq_printf) failed: %d\n", ret);
        return ret;
    }
    mr_registered = true;
    pr_info("mount_renumber: active (mountinfo ids renumbered for readers)\n");
    return 0;
}

void __exit ksu_mount_renumber_exit(void)
{
    struct mr_state *s, *tmp;

    if (mr_registered) {
        unregister_kprobe(&mr_probe);
        mr_registered = false;
    }
    spin_lock(&mr_lock);
    s = mr_states;
    mr_states = NULL;
    spin_unlock(&mr_lock);
    while (s) {
        tmp = s->next;
        kfree(s);
        s = tmp;
    }
}

#endif /* CONFIG_ARM64 */
