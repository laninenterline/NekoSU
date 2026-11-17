// SPDX-License-Identifier: GPL-2.0
/*
 * Stealth mode: when enabled, GET_INFO no longer reports the MANAGER flag to
 * userspace, so the manager UI degrades into "not installed". Real permission
 * checks are unchanged (is_manager() stays true), which is what lets the
 * manager itself turn the switch back off (secret dial code).
 *
 * State is persisted in <KSU working dir>/stealth ('0'/'1'), survives manager
 * reinstall and is reloaded on post-fs-data.
 */
#include <linux/types.h>
#include <linux/err.h>
#include <linux/errno.h>
#include <linux/fs.h>
#include <linux/file.h>
#include <linux/fcntl.h>
#include <linux/cred.h>
#include <linux/printk.h>
#include <linux/string.h>

#include "manager/stealth.h"
#include "ksu.h"
#include "klog.h" // IWYU pragma: keep

#define KSU_STEALTH_PATH "/data/adb/ksu/stealth"

static bool ksu_stealth_enabled;
static bool ksu_stealth_loaded;

static int ksu_stealth_write(const char *data, size_t len)
{
    struct file *fp;
    const struct cred *saved;
    loff_t off = 0;

    saved = override_creds(ksu_cred);
    fp = filp_open(KSU_STEALTH_PATH, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (IS_ERR(fp)) {
        revert_creds(saved);
        return PTR_ERR(fp);
    }

    if (kernel_write(fp, data, len, &off) != (ssize_t)len)
        pr_warn("stealth: short write to %s\n", KSU_STEALTH_PATH);

    filp_close(fp, NULL);
    revert_creds(saved);
    return 0;
}

void ksu_stealth_load(void)
{
    struct file *fp;
    char c = '0';
    loff_t pos = 0;

    ksu_stealth_loaded = true;

    fp = filp_open(KSU_STEALTH_PATH, O_RDONLY, 0);
    if (IS_ERR(fp)) {
        ksu_stealth_enabled = false;
        return;
    }

    ksu_stealth_enabled = (kernel_read(fp, &c, 1, &pos) == 1 && c == '1');
    filp_close(fp, NULL);

    pr_info("stealth: loaded, %s\n", ksu_stealth_enabled ? "enabled" : "disabled");
}

bool ksu_stealth_is_enabled(void)
{
    if (!ksu_stealth_loaded)
        ksu_stealth_load();

    return ksu_stealth_enabled;
}

int ksu_stealth_set(bool enabled)
{
    char c = enabled ? '1' : '0';
    int ret;

    ret = ksu_stealth_write(&c, 1);
    if (ret)
        return ret;

    ksu_stealth_enabled = enabled;
    ksu_stealth_loaded = true;
    pr_info("stealth: set to %d\n", enabled ? 1 : 0);
    return 0;
}
