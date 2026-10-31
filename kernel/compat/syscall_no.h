/** Auto generated file. DO NOT EDIT **/

#ifndef KSU_NR_COMPAT_H
#define KSU_NR_COMPAT_H

#include <asm/unistd.h>

#include <asm/syscall.h>
#include <asm/unistd_compat_32.h>

#define ksu_get_syscall_no(name) (is_compat_task() ? __NR_compat32_##name : __NR_##name)
#define ksu_get_compat_syscall_no(name) __NR_compat32_##name

#endif
