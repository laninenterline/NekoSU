#ifndef __KSU_H_AVC_SPOOF
#define __KSU_H_AVC_SPOOF

#include <linux/types.h>

/* AVC log spoof: rewrite the root (su) domain in SELinux audit records. */
void ksu_avc_spoof_init(void);
void ksu_avc_spoof_exit(void);
void ksu_avc_spoof_late_init(void);

int ksu_avc_spoof_get(bool *enabled);
int ksu_avc_spoof_set(bool enabled);

#endif /* __KSU_H_AVC_SPOOF */
