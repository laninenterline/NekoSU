#ifndef _MILKSU_KPM_TRACE_H_
#define _MILKSU_KPM_TRACE_H_

#ifdef MILKSU_KPM_TRACE
#include <kallsyms.h>
#include <log.h>

/* Leave time for the device's USB log stream before the next loader stage. */
static inline void milksu_kpm_trace_pause(void)
{
    void (*pause)(unsigned int) =
        (void (*)(unsigned int))kallsyms_lookup_name("msleep");
    if (pause) pause(250);
}

#undef logki
#define logki(fmt, ...) do { \
    printk("\0013[+] KP I " fmt, ##__VA_ARGS__); \
    milksu_kpm_trace_pause(); \
} while (0)
#endif

#endif
