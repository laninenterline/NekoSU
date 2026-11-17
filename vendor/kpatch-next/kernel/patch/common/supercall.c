/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 */

#ifdef KP_SUPERCALL_STRING_HOST_TEST
#include "../../../tests/supercall_string_host_stubs.h"
#else
#include <ktypes.h>
#include <uapi/scdefs.h>
#include <hook.h>
#include <common.h>
#include <log.h>
#include <predata.h>
#include <pgtable.h>
#include <linux/syscall.h>
#include <uapi/asm-generic/errno.h>
#include <linux/uaccess.h>
#include <linux/cred.h>
#include <asm/current.h>
#include <linux/string.h>
#include <linux/pid.h>
#include <linux/sched.h>
#include <linux/security.h>
#include <syscall.h>
#include <accctl.h>
#include <module.h>
#include <kputils.h>
#include <linux/err.h>
#include <linux/slab.h>
#include <kputils.h>
#include <pidmem.h>
#include <predata.h>
#include <linux/random.h>
#include <kpextension.h>
#include <kstorage.h>
#include <rehook.h>

#include <linux/umh.h>
#include <kpm_trace.h>
#endif

#ifndef KP_SUPERCALL_STRING_HOST_TEST
static long call_bootlog()
{
    print_bootlog();
    return 0;
}

static long call_panic()
{
    unsigned long panic_addr = kallsyms_lookup_name("panic");
    ((void (*)(const char *fmt, ...))panic_addr)("!!!! kernel_patch panic !!!!");
    return 0;
}

static long call_klog(const char __user *arg1)
{
    char buf[1024];
    long len = compat_strncpy_from_user(buf, arg1, sizeof(buf));
    if (len <= 0) return -EINVAL;
    if (len > 0) logki("user log: %s", buf);
    return 0;
}

static long call_buildtime(char __user *out_buildtime, int u_len)
{
    const char *buildtime = get_build_time();
    int len = strlen(buildtime);
    if (len >= u_len) return -ENOMEM;
    int rc = compat_copy_to_user(out_buildtime, buildtime, len + 1);
    return rc;
}
#endif

/* Both nofault variants return a length INCLUDING NUL. Reserve one byte
 * beyond the public maximum so a truncated result is distinguishable from a
 * valid maximum-length string. Never fall back to faulting strncpy_from_user. */
static long kpm_copy_string_nofault(char *destination, const char __user *source,
                                    long capacity, int required)
{
    long copied;
    if (!destination || capacity < 2) return -EINVAL;
    destination[0] = 0;
    if (!source) return required ? -EFAULT : 0;
    if (kfunc(strncpy_from_user_nofault)) {
        copied = kfunc(strncpy_from_user_nofault)(destination, source, capacity);
    } else if (kfunc(strncpy_from_unsafe_user)) {
        copied = kfunc(strncpy_from_unsafe_user)(destination, source, capacity);
    } else {
        return -ENOSYS;
    }
    if (copied < 0) {
        destination[0] = 0;
        return copied;
    }
    if (copied >= capacity) {
        destination[0] = 0;
        return -ENAMETOOLONG;
    }
    if (!copied || destination[copied - 1]) {
        destination[0] = 0;
        return -EFAULT;
    }
    if (required && copied == 1) return -EINVAL;
    return copied - 1;
}

static __noinline long call_kpm_load(const char __user *arg1, const char *__user arg2, void *__user reserved)
{
    logki("KPM_SC_LOAD checkpoint=ENTRY\n");
    char path[1025], args[KPM_ARGS_LEN + 1];
    logki("KPM_SC_LOAD checkpoint=path-copy-start\n");
    long pathlen = kpm_copy_string_nofault(path, arg1, sizeof(path), 1);
    logki("KPM_SC_LOAD checkpoint=path-copy-returned rc=%ld\n", pathlen);
    if (pathlen < 0) return pathlen;
    logki("KPM_SC_LOAD checkpoint=args-copy-start\n");
    long arglen = kpm_copy_string_nofault(args, arg2, sizeof(args), 0);
    logki("KPM_SC_LOAD checkpoint=args-copy-returned rc=%ld\n", arglen);
    if (arglen < 0) return arglen;
    return load_module_path(path, arglen ? args : 0, reserved);
}

/* Load the exact bytes verified by userspace without a second pathname open.
 * The synchronous loader owns all retained data before this copy is freed. */
static __noinline long call_kpm_load_buffer(const void __user *image, unsigned long length,
                                           const char __user *user_args, void __user *reserved)
{
    char args[KPM_ARGS_LEN + 1];
    void *copy;
    long rc;

    if (!image) return -EFAULT;
    if (!length || length > (32ul << 20)) return -E2BIG;
    if (reserved) return -EINVAL;
    if (!kfunc(vmemdup_user)) return -ENOSYS;
    rc = kpm_copy_string_nofault(args, user_args, sizeof(args), 0);
    if (rc < 0) return rc;
    logki("KPM_LOAD_BUFFER checkpoint=copy-start\n");
    copy = kfunc(vmemdup_user)(image, length);
    if (IS_ERR(copy)) return PTR_ERR(copy);
    if (!copy) return -ENOMEM;
    logki("KPM_LOAD_BUFFER checkpoint=copy-returned\n");
    rc = load_module(copy, length, args[0] ? args : 0, "load-file", 0);
    kvfree(copy);
    return rc;
}

static long call_kpm_control(const char __user *arg1, const char *__user arg2, void *__user out_msg, int outlen)
{
    char name[KPM_NAME_LEN + 1], args[KPM_ARGS_LEN + 1];
    long namelen = kpm_copy_string_nofault(name, arg1, sizeof(name), 1);
    if (namelen < 0) return namelen;
    long arglen = kpm_copy_string_nofault(args, arg2, sizeof(args), 0);
    if (arglen < 0) return arglen;
    return module_control0(name, args, out_msg, outlen);
}

static long call_kpm_unload(const char *__user arg1, void *__user reserved)
{
    char name[KPM_NAME_LEN + 1];
    long len = kpm_copy_string_nofault(name, arg1, sizeof(name), 1);
    if (len < 0) return len;
    return unload_module(name, reserved);
}

#ifndef KP_SUPERCALL_STRING_HOST_TEST
static long call_kpm_nums()
{
    return get_module_nums();
}

static long call_kpm_list(char *__user names, int len)
{
    if (len <= 0) return -EINVAL;
    char buf[4096] = {0};
    int sz = list_modules(buf, sizeof(buf));
    if (sz < 0) return sz;
    if (sz + 1 > len) return -ENOBUFS;
    int copied = compat_copy_to_user(names, buf, sz + 1);
    return copied == sz + 1 ? sz + 1 : -EFAULT;
}
#endif

static long call_kpm_info(const char *__user uname, char *__user out_info, int out_len)
{
    if (out_len <= 0) return -EINVAL;
    char name[KPM_NAME_LEN + 1];
    char buf[4096] = {0};
    long len = kpm_copy_string_nofault(name, uname, sizeof(name), 1);
    if (len < 0) return len;
    int sz = get_module_info(name, buf, sizeof(buf));
    if (sz < 0) return sz;
    if (sz + 1 > out_len) return -ENOBUFS;
    int copied = compat_copy_to_user(out_info, buf, sz + 1);
    return copied == sz + 1 ? sz + 1 : -EFAULT;
}

#ifndef KP_SUPERCALL_STRING_HOST_TEST
static long call_kstorage_read(int gid, long did, void *out_data, int offset, int dlen)
{
    return read_kstorage(gid, did, out_data, offset, dlen, true);
}

static long call_kstorage_write(int gid, long did, void *data, int offset, int dlen)
{
    return write_kstorage(gid, did, data, offset, dlen, true);
}

static long call_list_kstorage_ids(int gid, long *ids, int ids_len)
{
    return list_kstorage_ids(gid, ids, ids_len, false);
}

static long call_kstorage_remove(int gid, long did)
{
    return remove_kstorage(gid, did);
}

static long supercall(long cmd, long arg1, long arg2, long arg3, long arg4)
{
    switch (cmd) {
    case SUPERCALL_HELLO:
        logki(SUPERCALL_HELLO_ECHO "\n");
        return SUPERCALL_HELLO_MAGIC;
    case SUPERCALL_KLOG:
        return call_klog((const char *__user)arg1);
    case SUPERCALL_KERNELPATCH_VER:
        return kpver;
    case SUPERCALL_KERNEL_VER:
        return kver;
    case SUPERCALL_BUILD_TIME:
        return call_buildtime((char *__user)arg1, (int)arg2);
    }

    switch (cmd) {
    case SUPERCALL_KSTORAGE_READ:
        return call_kstorage_read((int)arg1, (long)arg2, (void *)arg3, (int)((long)arg4 >> 32), (long)arg4 << 32 >> 32);
    case SUPERCALL_KSTORAGE_WRITE:
        return call_kstorage_write((int)arg1, (long)arg2, (void *)arg3, (int)((long)arg4 >> 32),
                                   (long)arg4 << 32 >> 32);
    case SUPERCALL_KSTORAGE_LIST_IDS:
        return call_list_kstorage_ids((int)arg1, (long *)arg2, (int)arg3);
    case SUPERCALL_KSTORAGE_REMOVE:
        return call_kstorage_remove((int)arg1, (long)arg2);
    default:
        break;
    }

    switch (cmd) {
    case SUPERCALL_BOOTLOG:
        return call_bootlog();
    case SUPERCALL_PANIC:
        return call_panic();
    default:
        break;
    }

    switch (cmd) {
    case SUPERCALL_REHOOK_SYSCALL:
        {
            int enable = (int)arg1;
            if (enable)
                return rehook_init();
            else
                return rehook_exit();
        }
    case SUPERCALL_REHOOK_STATUS:
        return rehook_status();
    }

    switch (cmd) {
    case SUPERCALL_KPM_LOAD:
        logki("KPM_SC_LOAD checkpoint=dispatch\n");
        return call_kpm_load((const char *__user)arg1, (const char *__user)arg2, (void *__user)arg3);
    case SUPERCALL_KPM_LOAD_BUFFER:
        return call_kpm_load_buffer((const void *__user)arg1, (unsigned long)arg2,
                                    (const char *__user)arg3, (void *__user)arg4);
    case SUPERCALL_KPM_UNLOAD:
        return call_kpm_unload((const char *__user)arg1, (void *__user)arg2);
    case SUPERCALL_KPM_CONTROL:
        return call_kpm_control((const char *__user)arg1, (const char *__user)arg2, (char *__user)arg3, (int)arg4);
    case SUPERCALL_KPM_NUMS:
        return call_kpm_nums();
    case SUPERCALL_KPM_LIST:
        return call_kpm_list((char *__user)arg1, (int)arg2);
    case SUPERCALL_KPM_INFO:
        return call_kpm_info((const char *__user)arg1, (char *__user)arg2, (int)arg3);
    case SUPERCALL_KPM_CAPABILITIES:
        return KPM_LOADER_CAPABILITIES;
    }

    switch (cmd) {
    default:
        break;
    }

    return -ENOSYS;
}

void supercall_before(hook_fargs6_t *args, void *udata)
{
    if (current_uid() != 0u) // only allow root 
        return;
    long ver_xx_cmd = (long)syscall_argn(args, 1);

    // todo: from 0.10.5
    // uint32_t ver = (ver_xx_cmd & 0xFFFFFFFF00000000ul) >> 32;
    // long xx = (ver_xx_cmd & 0xFFFF0000) >> 16;

    long cmd = ver_xx_cmd & 0xFFFF;
    if (cmd < SUPERCALL_HELLO || cmd > SUPERCALL_MAX) return;

    long a1 = (long)syscall_argn(args, 2);
    long a2 = (long)syscall_argn(args, 3);
    long a3 = (long)syscall_argn(args, 4);
    long a4 = (long)syscall_argn(args, 5);

    args->skip_origin = 1;
    args->ret = supercall(cmd, a1, a2, a3, a4);
}

int supercall_install()
{
    int rc = 0;

    hook_err_t err = hook_syscalln(__NR_supercall, 6, supercall_before, 0, 0);
    if (err) {
        log_boot("install supercall hook error: %d\n", err);
        rc = err;
        goto out;
    }
out:
    return rc;
}
#endif
