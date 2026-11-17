// SPDX-License-Identifier: GPL-2.0
/*
 * Per-caller spoof of the Settings.Global "adb_enabled" value.
 *
 * Apps ask the settings provider through a binder transaction whose parcel
 * contains the UTF-16 string "adb_enabled". We rewrite that string, in the
 * caller's own user-space parcel buffer, to the same-length key "adb_spoofed".
 * The provider then returns the value of that fake key (seeded to 0) while the
 * real adb_enabled stays 1 and adb keeps working.
 */
#include <linux/kprobes.h>
#include <linux/sched.h>
#include <linux/cred.h>
#include <linux/uaccess.h>
#include <linux/slab.h>
#include <linux/string.h>

#include "compat/kernel_compat.h"
#include "klog.h" // IWYU pragma: keep

#ifndef CONFIG_ARM64

int __init ksu_usb_debug_spoof_init(void) { return 0; }
void __exit ksu_usb_debug_spoof_exit(void) {}

#else

#define BINDER_WRITE_READ 0xc0306201
#define BC_TRANSACTION 0x40406300
#define BC_REPLY 0x40406301
#define BC_TRANSACTION_SEC_CTX 0x40406302

struct bwr_min {
    u64 write_size;
    u64 write_consumed;
    u64 write_buffer;
    u64 read_size;
    u64 read_consumed;
    u64 read_buffer;
};

static struct kprobe usb_spoof_probe;
static bool usb_spoof_registered;

/* UTF-16-LE "adb_enabled" -> same-length "adb_spoofed". */
static const char from_utf16[] = {
    'a', 0, 'd', 0, 'b', 0, '_', 0, 'e', 0, 'n', 0,
    'a', 0, 'b', 0, 'l', 0, 'e', 0, 'd', 0
};
static const char to_utf16[] = {
    'a', 0, 'd', 0, 'b', 0, '_', 0, 's', 0, 'p', 0,
    'o', 0, 'o', 0, 'f', 0, 'e', 0, 'd', 0
};

static bool patch_buffer(unsigned char *data, size_t size)
{
    size_t i;
    bool found = false;

    for (i = 0; i + sizeof(from_utf16) <= size; i++) {
        if (memcmp(data + i, from_utf16, sizeof(from_utf16)) == 0) {
            memcpy(data + i, to_utf16, sizeof(to_utf16));
            found = true;
        }
    }
    return found;
}

static bool try_transaction(unsigned char *buf, size_t size, size_t txn_off)
{
    const unsigned char *txn;
    u64 data_size, data_ptr;
    unsigned char *parcel;
    bool patched = false;

    if (txn_off + 56 > size)
        return false;
    txn = buf + txn_off;
    data_size = *(const u64 *)(txn + 32);
    data_ptr = *(const u64 *)(txn + 48);
    if (!data_ptr || data_size < sizeof(from_utf16) || data_size > 256 * 1024)
        return false;
    parcel = kmalloc(data_size, GFP_ATOMIC);
    if (!parcel)
        return false;
    if (copy_from_user(parcel, (void __user *)data_ptr, data_size) == 0) {
        patched = patch_buffer(parcel, data_size);
        if (patched &&
            copy_to_user((void __user *)data_ptr, parcel, data_size) != 0) {
            pr_warn("usb_debug_spoof: copy_to_user parcel failed\n");
            patched = false;
        }
    }
    kfree(parcel);
    return patched;
}

static int usb_spoof_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct pt_regs *sys;
    int cmd;
    unsigned long arg;
    struct bwr_min bwr;
    unsigned char *buf;
    size_t size, off;
    bool found = false;

    (void)p;
    if (ksu_get_uid_t(current_euid()) < 10000)
        return 0;
    sys = (struct pt_regs *)regs->regs[0];
    if (!sys)
        return 0;
    cmd = (int)sys->regs[1];
    arg = sys->regs[2];
    if (cmd != BINDER_WRITE_READ)
        return 0;
    if (copy_from_user(&bwr, (void __user *)arg, sizeof(bwr)) != 0)
        return 0;
    size = (size_t)bwr.write_size;
    if (size < 8 || size > 256 * 1024)
        return 0;
    buf = kmalloc(size, GFP_ATOMIC);
    if (!buf)
        return 0;
    if (copy_from_user(buf, (void __user *)bwr.write_buffer, size) != 0)
        goto out;

    /*
     * libbinder packs: u32 cmd, then binder_transaction_data. Depending on
     * the writer the struct is 4- or 8-byte aligned, so try both.
     */
    for (off = 4; off <= 8; off += 4) {
        u32 cmd_val;

        if (off > size)
            break;
        cmd_val = *(u32 *)(buf + off - 4);
        if (cmd_val != BC_TRANSACTION && cmd_val != BC_REPLY &&
            cmd_val != BC_TRANSACTION_SEC_CTX)
            continue;
        if (try_transaction(buf, size, off)) {
            found = true;
            break;
        }
    }
    if (!found)
        found = patch_buffer(buf, size);
    if (found) {
        pr_info_ratelimited("usb_debug_spoof: redirected adb_enabled request (pid %d)\n",
                            current->pid);
        if (copy_to_user((void __user *)bwr.write_buffer, buf, size) != 0)
            pr_warn("usb_debug_spoof: copy_to_user stream failed\n");
    }
out:
    kfree(buf);
    return 0;
}

int __init ksu_usb_debug_spoof_init(void)
{
    int ret;

    usb_spoof_probe.symbol_name = "__arm64_sys_ioctl";
    usb_spoof_probe.pre_handler = usb_spoof_pre;
    ret = register_kprobe(&usb_spoof_probe);
    if (ret) {
        pr_warn("usb_debug_spoof: register_kprobe failed: %d\n", ret);
        return ret;
    }
    usb_spoof_registered = true;
    pr_info("usb_debug_spoof: active (adb_enabled queries redirected for apps)\n");
    return 0;
}

void __exit ksu_usb_debug_spoof_exit(void)
{
    if (usb_spoof_registered) {
        unregister_kprobe(&usb_spoof_probe);
        usb_spoof_registered = false;
    }
}

#endif /* CONFIG_ARM64 */
