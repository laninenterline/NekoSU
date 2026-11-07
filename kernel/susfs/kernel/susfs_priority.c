// SPDX-License-Identifier: GPL-2.0
/*
 * susfs_priority.c - getpriority/setpriority + ioprio uid filter (official SUSFS v2.3.0
 * feature CMD_SUSFS_SET_PRIORITY_UID_FILTER, ported to the LKM).
 *
 * Official implementation lives in kernel/sys.c and block/ioprio.c as in-tree checks; an
 * LKM cannot patch those functions, so the same predicate is applied by kprobe at the
 * syscall wrappers' entry: a filtered target makes the call a no-op returning 0.
 */
#include <linux/version.h>
#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/kthread.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/namei.h>
#include <linux/uaccess.h>
#include <linux/vmalloc.h>
#include <linux/bitmap.h>
#include <linux/rcupdate.h>
#include <linux/mutex.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/pid.h>
#include <linux/syscalls.h>

#include "susfs_abi.h"
#include "susfs_log.h"
#include "susfs.h"
#include "symbol_resolver.h"
#include "susfs_kprobe.h"

#define SUSFS_PRIORITY_UID_FILTER_MIN_UID 10000
#define SUSFS_PRIORITY_UID_FILTER_MAX_UID 19999
#define SUSFS_PRIORITY_UID_FILTER_UID_NUM \
	(SUSFS_PRIORITY_UID_FILTER_MAX_UID - SUSFS_PRIORITY_UID_FILTER_MIN_UID + 1)
#define SUSFS_PRIORITY_UID_FILTER_BITMAP_LEN BITS_TO_LONGS(SUSFS_PRIORITY_UID_FILTER_UID_NUM)

#define PRIO_PROCESS 0
#define PRIO_USER 1

struct susfs_priority_uid_filter_bitmap {
	struct rcu_head rcu;
	unsigned long bits[SUSFS_PRIORITY_UID_FILTER_BITMAP_LEN];
};

static struct susfs_priority_uid_filter_bitmap __rcu *priority_uid_filter_bitmap;
static DEFINE_MUTEX(susfs_mutex_lock_priority_uid_filter);
static char *susfs_priority_app_strings;

static struct kprobe kp_setpriority = {
	.symbol_name = "__arm64_sys_setpriority",
};
static struct kprobe kp_ioprio_get = {
	.symbol_name = "__arm64_sys_ioprio_get",
};
static struct kprobe kp_ioprio_set = {
	.symbol_name = "__arm64_sys_ioprio_set",
};

static struct task_struct *(*susfs_find_task_by_vpid)(pid_t nr);

static bool susfs_is_valid_app_name(const char *app_name)
{
	const char *p = app_name;
	size_t len = strlen(app_name);

	if (len == 0 || len > 255)
		return false;
	if (!strcmp(app_name, ".") || !strcmp(app_name, ".."))
		return false;
	for (; *p != '\0'; p++) {
		if ((*p >= 'a' && *p <= 'z') || (*p >= 'A' && *p <= 'Z') ||
		    (*p >= '0' && *p <= '9') || *p == '.' || *p == '_')
			continue;
		return false;
	}
	return true;
}

/* <package> <uid> <gid> ... per line in /data/system/packages.list */
static int susfs_resolve_priority_app_uid(const char *app_name, uid_t *uid)
{
	struct file *fp;
	char *buf = NULL;
	char *line, *tmp, *p;
	char *pkg_name, *uid_str;
	loff_t pos = 0;
	size_t size;
	unsigned long val;
	int ret = -ENOENT;

	if (!app_name || !*app_name || !uid)
		return -EINVAL;
	if (!susfs_is_valid_app_name(app_name))
		return -EINVAL;

	fp = filp_open("/data/system/packages.list", O_RDONLY | O_NOATIME, 0);
	if (IS_ERR(fp))
		return PTR_ERR(fp);

	size = i_size_read(file_inode(fp));
	if (size <= 0 || size > (4 * 1024 * 1024)) {
		filp_close(fp, NULL);
		return -EFBIG;
	}

	buf = vmalloc(size + 1);
	if (!buf) {
		filp_close(fp, NULL);
		return -ENOMEM;
	}
	buf[size] = '\0';

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0)
	ret = kernel_read(fp, buf, size, &pos);
#else
	ret = kernel_read(fp, pos, buf, size);
#endif
	filp_close(fp, NULL);
	if (ret != (int)size) {
		vfree(buf);
		return -EIO;
	}

	line = buf;
	while (line && *line) {
		p = strchr(line, '\n');
		if (p) {
			*p = '\0';
			tmp = p + 1;
		} else {
			tmp = NULL;
		}
		if (*line == '\0' || *line == '\r') {
			line = tmp;
			continue;
		}
		pkg_name = strsep(&line, " \t");
		if (!pkg_name || *pkg_name == '\0') {
			line = tmp;
			continue;
		}
		uid_str = strsep(&line, " \t");
		if (!uid_str || *uid_str == '\0') {
			line = tmp;
			continue;
		}
		if (!strcmp(pkg_name, app_name)) {
			if (kstrtoul(uid_str, 10, &val)) {
				vfree(buf);
				return -EINVAL;
			}
			*uid = (uid_t)(val % 100000);
			vfree(buf);
			if (*uid < SUSFS_PRIORITY_UID_FILTER_MIN_UID ||
			    *uid > SUSFS_PRIORITY_UID_FILTER_MAX_UID)
				return -EINVAL;
			return 0;
		}
		line = tmp;
	}
	vfree(buf);
	return -ENOENT;
}

static int susfs_parse_priority_app_names(char *app_str, unsigned long *bits,
					  bool skip_failed)
{
	char *token = NULL;
	char *saveptr = app_str;
	uid_t uid = 0;
	int ret;

	while ((token = strsep(&saveptr, ",")) != NULL) {
		token = strim(token);
		if (*token == '\0')
			continue;
		if (!susfs_is_valid_app_name(token)) {
			pr_err("priority filter: invalid app name '%s'\n", token);
			return -EINVAL;
		}
		ret = susfs_resolve_priority_app_uid(token, &uid);
		if (ret) {
			if (skip_failed)
				continue;
			return ret;
		}
		if (uid < SUSFS_PRIORITY_UID_FILTER_MIN_UID ||
		    uid > SUSFS_PRIORITY_UID_FILTER_MAX_UID) {
			if (!skip_failed)
				return -EINVAL;
			continue;
		}
		set_bit(uid - SUSFS_PRIORITY_UID_FILTER_MIN_UID, bits);
		SUSFS_LOGI("priority filter: %s uid %u added\n", token, uid);
	}
	return 0;
}

bool susfs_is_priority_uid_filtered(uid_t uid)
{
	struct susfs_priority_uid_filter_bitmap *bitmap;
	uid_t app_id = uid % 100000;

	if (app_id < SUSFS_PRIORITY_UID_FILTER_MIN_UID ||
	    app_id > SUSFS_PRIORITY_UID_FILTER_MAX_UID)
		return false;
	bitmap = rcu_dereference(priority_uid_filter_bitmap);
	if (likely(bitmap))
		return test_bit(app_id - SUSFS_PRIORITY_UID_FILTER_MIN_UID,
				bitmap->bits);
	return false;
}

bool susfs_priority_active(void)
{
	return rcu_access_pointer(priority_uid_filter_bitmap) != NULL;
}

/* supercall: CMD_SUSFS_SET_PRIORITY_UID_FILTER */
void susfs_set_priority_uid_filter(void __user **user_info)
{
	struct st_susfs_priority_uid_filter *info = NULL;
	struct susfs_priority_uid_filter_bitmap *new_bitmap = NULL;
	struct susfs_priority_uid_filter_bitmap *old_bitmap = NULL;
	char *new_app_strings = NULL;
	size_t app_str_len;
	int err = 0;

	info = kvmalloc(sizeof(*info), GFP_KERNEL | __GFP_ZERO);
	if (!info) {
		err = -ENOMEM;
		goto out;
	}
	if (copy_from_user(info, (void __user *)*user_info, sizeof(*info))) {
		err = -EFAULT;
		goto out;
	}
	if (!memchr(info->app_strings, '\0', sizeof(info->app_strings))) {
		err = -EINVAL;
		goto out;
	}
	if (*info->app_strings == '\0') {
		err = -EINVAL;
		goto out;
	}
	app_str_len = strlen(info->app_strings);

	new_app_strings = kvmalloc(app_str_len + 1, GFP_KERNEL | __GFP_ZERO);
	if (!new_app_strings) {
		err = -ENOMEM;
		goto out;
	}
	memcpy(new_app_strings, info->app_strings, app_str_len + 1);

	new_bitmap = kzalloc(sizeof(*new_bitmap), GFP_KERNEL);

	if (!new_bitmap) {
		err = -ENOMEM;
		goto out;
	}

	err = susfs_parse_priority_app_names(info->app_strings, new_bitmap->bits, true);
	if (err)
		goto out;

	mutex_lock(&susfs_mutex_lock_priority_uid_filter);
	old_bitmap = rcu_dereference_protected(priority_uid_filter_bitmap,
					       lockdep_is_held(&susfs_mutex_lock_priority_uid_filter));
	rcu_assign_pointer(priority_uid_filter_bitmap, new_bitmap);
	new_bitmap = NULL;
	kvfree(susfs_priority_app_strings);
	susfs_priority_app_strings = new_app_strings;
	new_app_strings = NULL;
	mutex_unlock(&susfs_mutex_lock_priority_uid_filter);
	synchronize_rcu();
	kfree_rcu(old_bitmap, rcu);
	SUSFS_LOGI("priority uid filter set\n");
	err = 0;
out:
	if (new_app_strings)
		kvfree(new_app_strings);
	if (new_bitmap)
		kfree(new_bitmap);
	if (info) {
		info->err = err;
		if (copy_to_user(&((struct st_susfs_priority_uid_filter __user *)*user_info)->err,
				 &info->err, sizeof(info->err)))
			pr_warn("priority filter: copy_to_user failed\n");
		kvfree(info);
	}
}

static bool susfs_priority_target_filtered(int which, int who)
{
	uid_t uid;
	bool filtered = false;

	if (which == PRIO_USER) {
		if (!who)
			return false;
		return susfs_is_priority_uid_filtered((uid_t)who);
	}
	if (which != PRIO_PROCESS)
		return false;

	if (!who) {
		rcu_read_lock();
		filtered = susfs_is_priority_uid_filtered(current_uid().val);
		rcu_read_unlock();
		return filtered;
	}

	if (!susfs_find_task_by_vpid)
		return false;
	rcu_read_lock();
	{
		struct task_struct *task = susfs_find_task_by_vpid(who);

		if (task)
			filtered = susfs_is_priority_uid_filtered(task_uid(task).val);
	}
	rcu_read_unlock();
	return filtered;
}

/* Skip the syscall wrapper: return 0 from it without executing its body. */
static void susfs_priority_skip(struct pt_regs *regs)
{
	regs->regs[0] = 0;
	regs->pc = regs->regs[30];
}

static int susfs_setpriority_pre(struct kprobe *p, struct pt_regs *regs)
{
	struct pt_regs *user = (struct pt_regs *)regs->regs[0];
	int which, who;

	if (!user || current_uid().val < 10000)
		return 0;
	which = (int)user->regs[0];
	who = (int)user->regs[1];
	if (!susfs_priority_target_filtered(which, who))
		return 0;
	SUSFS_LOGI("priority filter: blocked setpriority which=%d who=%d\n", which, who);
	susfs_priority_skip(regs);
	return 1;
}

static int susfs_ioprio_pre(struct kprobe *p, struct pt_regs *regs)
{
	struct pt_regs *user = (struct pt_regs *)regs->regs[0];
	int which, who;

	if (!user || current_uid().val < 10000)
		return 0;
	which = (int)user->regs[0];
	who = (int)user->regs[1];
	if (!susfs_priority_target_filtered(which, who))
		return 0;
	SUSFS_LOGI("priority filter: blocked %s which=%d who=%d\n",
		   p->symbol_name, which, who);
	susfs_priority_skip(regs);
	return 1;
}

static int susfs_priority_register(struct kprobe *kp,
				   int (*pre)(struct kprobe *, struct pt_regs *))
{
	int ret;

	kp->pre_handler = pre;
	ret = register_kprobe(kp);
	if (ret)
		susfs_kp_forget_addr(kp);
	return ret;
}

static void susfs_priority_unregister(struct kprobe *kp)
{
	unregister_kprobe(kp);
	susfs_kp_forget_addr(kp);
}

int susfs_priority_init(void)
{
	int ret;

	susfs_find_task_by_vpid =
		(void *)find_kernel_symbol_exact("find_task_by_vpid");
	if (!susfs_find_task_by_vpid)
		SUSFS_LOGI("priority filter: find_task_by_vpid not found, pid lookups limited\n");

	ret = susfs_priority_register(&kp_setpriority, susfs_setpriority_pre);
	if (ret) {
		pr_warn("priority filter: setpriority hook failed %d\n", ret);
		return ret;
	}
	ret = susfs_priority_register(&kp_ioprio_get, susfs_ioprio_pre);
	if (ret) {
		pr_warn("priority filter: ioprio_get hook failed %d\n", ret);
		goto out_setpriority;
	}
	ret = susfs_priority_register(&kp_ioprio_set, susfs_ioprio_pre);
	if (ret) {
		pr_warn("priority filter: ioprio_set hook failed %d\n", ret);
		goto out_ioprio_get;
	}
	SUSFS_LOGI("priority uid filter armed\n");
	return 0;

out_ioprio_get:
	susfs_priority_unregister(&kp_ioprio_get);
out_setpriority:
	susfs_priority_unregister(&kp_setpriority);
	return ret;
}

void susfs_priority_exit(void)
{
	susfs_priority_unregister(&kp_ioprio_set);
	susfs_priority_unregister(&kp_ioprio_get);
	susfs_priority_unregister(&kp_setpriority);

	mutex_lock(&susfs_mutex_lock_priority_uid_filter);
	{
		struct susfs_priority_uid_filter_bitmap *old =
			rcu_dereference_protected(priority_uid_filter_bitmap,
						  lockdep_is_held(&susfs_mutex_lock_priority_uid_filter));

		rcu_assign_pointer(priority_uid_filter_bitmap, NULL);
		kvfree(susfs_priority_app_strings);
		susfs_priority_app_strings = NULL;
		(void)old;
	}
	mutex_unlock(&susfs_mutex_lock_priority_uid_filter);
}
