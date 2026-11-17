/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * TEST ONLY. Based on kpms/demo-hello/hello.c, Copyright (C) 2023 bmax121.
 * These fixtures only log, echo to their caller, and return a fixed status.
 */
#ifndef MILKSU_KPM_ACCEPTANCE_FIXTURE_H
#define MILKSU_KPM_ACCEPTANCE_FIXTURE_H

#ifdef MILKSU_FIXTURE_HOST_TEST
#include "fixture_host.h"
#else
#include <compiler.h>
#include <common.h>
#include <kpmodule.h>
#include <kputils.h>
#include <linux/printk.h>
#include <uapi/asm-generic/errno.h>
#endif

KPM_NAME(TEST_MODULE_NAME);
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("MilkSU acceptance tests");
KPM_DESCRIPTION("TEST ONLY: logs and bounded echo; no hooks or persistent state");

static long fixture_init(const char *args, const char *event, void *reserved)
{
    (void)reserved;
    pr_info("MILKSU_KPM_TEST init name=%s event=%s args=%s rc=%d\n",
            TEST_MODULE_NAME, event ? event : "", args ? args : "", TEST_INIT_RESULT);
    return TEST_INIT_RESULT;
}

#if TEST_HAS_CONTROL
static long fixture_control(const char *args, char *__user output, int outlen)
{
    const char prefix[] = "echo: ";
    char echo[256];
    unsigned int length = 0;

    if (!args || !output || outlen <= 0) return -EINVAL;
    for (unsigned int i = 0; i < sizeof(prefix) - 1; i++) echo[length++] = prefix[i];
    for (unsigned int i = 0; args[i]; i++) {
        if (length >= sizeof(echo) - 1) return -E2BIG;
        echo[length++] = args[i];
    }
    echo[length++] = '\0';
    if ((unsigned int)outlen < length) return -ENOBUFS;
    int copied = compat_copy_to_user(output, echo, length);
    if (copied != (int)length) return -EFAULT;
    pr_info("MILKSU_KPM_TEST ctl0 name=%s bytes=%u\n", TEST_MODULE_NAME, length);
    return 0;
}
KPM_CTL0(fixture_control);
#endif

static long fixture_exit(void *reserved)
{
    (void)reserved;
    pr_info("MILKSU_KPM_TEST exit name=%s rc=%d\n", TEST_MODULE_NAME, TEST_EXIT_RESULT);
    return TEST_EXIT_RESULT;
}

KPM_INIT(fixture_init);
KPM_EXIT(fixture_exit);

#endif
