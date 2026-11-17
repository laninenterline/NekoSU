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


/* 4.19: static_assert() does not exist yet, and regs_set_return_value() was
 * added in 5.3 (arm64 keeps the return value in regs[0]). */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 0, 0)
/* static_assert is a keyword here, #ifndef cannot detect it. */
#undef static_assert
#define static_assert(cond, msg) _Static_assert(cond, msg)
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 3, 0)
#ifndef regs_set_return_value
#define regs_set_return_value(regs, val) do { (regs)->regs[0] = (unsigned long)(val); } while (0)
#endif
#endif


/* strscpy_pad() appeared in 4.20. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 20, 0)
static inline ssize_t susfs_strscpy_pad(char *dst, const char *src, size_t count)
{
    size_t len = strlen(src);
    if (count == 0)
        return -E2BIG;
    if (len >= count) {
        memcpy(dst, src, count - 1);
        dst[count - 1] = '\0';
        return -E2BIG;
    }
    memcpy(dst, src, len);
    memset(dst + len, 0, count - len);
    return (ssize_t)len;
}
#define strscpy_pad susfs_strscpy_pad
#endif


/* MODULE_IMPORT_NS() appeared in 5.4. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 4, 0)
#ifndef MODULE_IMPORT_NS
#define MODULE_IMPORT_NS(ns)
#endif
#endif


/* SUSFS calls syscall_get_arguments(task, regs, args); 4.x has the 5-arg form. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 1, 0)
#include <linux/sched.h>
#include <asm/syscall.h>
static inline void susfs_syscall_get_arguments(struct task_struct *task, struct pt_regs *regs,
                                               unsigned long *args)
{
    syscall_get_arguments(task, regs, 0, 6, args);
}
#define syscall_get_arguments susfs_syscall_get_arguments
#endif


/* regs_get_kernel_argument() is not available on 4.x arm64; syscall args live in regs[0..5]. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 0, 0)
#ifndef regs_get_kernel_argument
#define regs_get_kernel_argument(regs, n) ((unsigned long)((regs)->regs[(n)]))
#endif
#endif


/* __pte_to_phys() is not available on old arm64 kernels. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 0, 0) && defined(__aarch64__)
#ifndef __pte_to_phys
#define __pte_to_phys(pte) (pte_val(pte) & ((1ULL << PHYS_MASK_SHIFT) - 1))
#endif
#endif


/* 4.9/4.4 arm64 has no p4d level; treat it as the pgd level. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 11, 0) && defined(__aarch64__)
typedef pgd_t p4d_t;
#define p4d_offset(pgd, addr) (pgd)
#define p4d_none(pgd) pgd_none(pgd)
#define p4d_bad(pgd) pgd_bad(pgd)
#endif


/* kvmalloc()/kvfree() appeared in 4.12; only define them on truly old kernels
 * (a same-name macro would corrupt the kernel's own static inline definitions). */
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 12, 0)
#ifndef kvmalloc
#define kvmalloc(size, flags) kmalloc(size, flags)
#endif
#ifndef kvmalloc_array
#define kvmalloc_array(count, size, flags) kmalloc_array(count, size, flags)
#endif
#ifndef kvfree
#define kvfree(ptr) kfree(ptr)
#endif
#endif
#ifndef __NR_statx
#define __NR_statx 291
#endif


/* memalloc_nofs_save/restore appeared after 4.9. */
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 12, 0)
#ifndef memalloc_nofs_save
#define memalloc_nofs_save() (0)
#define memalloc_nofs_restore(flags) do { (void)(flags); } while (0)
#endif
#endif


/* old kernels have no __nocfi. */
#ifndef __nocfi
#define __nocfi
#endif

#endif /* SUSFS_KERNEL_COMPAT_H */
