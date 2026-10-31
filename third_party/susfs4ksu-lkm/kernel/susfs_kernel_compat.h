#ifndef SUSFS_KERNEL_COMPAT_H
#define SUSFS_KERNEL_COMPAT_H

#include <linux/version.h>
#include <linux/uaccess.h>
#include <linux/task_work.h>

/*
 * Compatibility shims for kernels older than the versions SUSFS was written
 * against.  Included for every translation unit through ccflags-y.
 */

/* proc_ops appeared in 5.6; older kernels use struct file_operations. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 6, 0)
#define proc_ops file_operations
#define proc_read read
#define proc_write write
#define proc_open open
#define proc_release release
#define proc_lseek llseek
#define proc_ioctl unlocked_ioctl
#define proc_compat_ioctl compat_ioctl
#define proc_poll poll
#endif

/* copy_{to,from}_kernel_nofault() appeared in 5.8 (was probe_kernel_*). */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 8, 0)
static inline long susfs_copy_to_kernel_nofault(void *dst, const void *src, size_t size)
{
    return probe_kernel_write(dst, src, size);
}
static inline long susfs_copy_from_kernel_nofault(void *dst, const void *src, size_t size)
{
    return probe_kernel_read(dst, src, size);
}
#define copy_to_kernel_nofault susfs_copy_to_kernel_nofault
#define copy_from_kernel_nofault susfs_copy_from_kernel_nofault
/* task_work_add() took a bool before 5.8; TWA_RESUME is that "notify" flag. */
#define TWA_RESUME true
#endif

/* struct statx::stx_mnt_id exists since 5.12. */
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 12, 0)
#define SUSFS_HAVE_STATX_MNT_ID 1
#endif


/* Older kernels (5.4) lack MNT_CURSOR and ns_lock. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 9, 0)
#ifndef MNT_CURSOR
#define MNT_CURSOR 0
#endif
#define SUSFS_NO_NS_LOCK 1
#endif

/* proc_mounts cursor mounts did not exist before 5.9; the walk is only
 * rcu-protected there, so treat it as best-effort on 5.4. */


/* 5.4 does not provide the pr_*_ratelimited helpers used by the module. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0)
#undef pr_info_ratelimited
#define pr_info_ratelimited(fmt, ...) pr_info(fmt, ##__VA_ARGS__)
#undef pr_warn_ratelimited
#define pr_warn_ratelimited(fmt, ...) pr_warn(fmt, ##__VA_ARGS__)
#undef pr_err_ratelimited
#define pr_err_ratelimited(fmt, ...) pr_err(fmt, ##__VA_ARGS__)
#endif

/* LOOKUP_NO_* flags appeared in 5.12; on 5.4 resolve normally. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 12, 0)
#ifndef LOOKUP_NO_XDEV
#define LOOKUP_NO_XDEV 0
#endif
#ifndef LOOKUP_NO_MAGICLINKS
#define LOOKUP_NO_MAGICLINKS 0
#endif
#ifndef LOOKUP_NO_SYMLINKS
#define LOOKUP_NO_SYMLINKS 0
#endif
#endif

#ifndef LOOKUP_BENEATH
#define LOOKUP_BENEATH 0
#endif
#ifndef LOOKUP_IN_ROOT
#define LOOKUP_IN_ROOT 0
#endif

#endif /* SUSFS_KERNEL_COMPAT_H */
