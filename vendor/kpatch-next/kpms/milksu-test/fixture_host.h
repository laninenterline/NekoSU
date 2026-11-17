/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Host-only fixture test shim. Never compiled into ARM64 KPM outputs. */
#ifndef MILKSU_FIXTURE_HOST_H
#define MILKSU_FIXTURE_HOST_H

#include <assert.h>
#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#define __user
#define KPM_NAME(value)
#define KPM_VERSION(value)
#define KPM_LICENSE(value)
#define KPM_AUTHOR(value)
#define KPM_DESCRIPTION(value)
#define KPM_INIT(callback)
#define KPM_EXIT(callback)
#define KPM_CTL0(callback)

static unsigned int log_count;
static char last_log[2048];

static void fixture_host_log(const char *format, ...)
{
    va_list args;
    va_start(args, format);
    vsnprintf(last_log, sizeof(last_log), format, args);
    va_end(args);
    log_count++;
}
#define pr_info(...) fixture_host_log(__VA_ARGS__)

static inline int compat_copy_to_user(void *destination, const void *source, int length)
{
    memcpy(destination, source, length);
    return length;
}
#endif
