#ifndef __KSU_H_HIDDEN_MANAGER
#define __KSU_H_HIDDEN_MANAGER

#include <linux/types.h>

#include "uapi/supercall.h"

/* Written by ksud (`ksud manager-identity set`), read by the kernel on every scan. */
#define KSU_MANAGER_IDENTITY_PATH "/data/adb/ksu/manager_identity"

#ifdef CONFIG_KSU_DISABLE_MANAGER
static inline bool ksu_hidden_manager_load_identity(void)
{
    return false;
}

static inline const struct ksu_manager_identity_record *ksu_hidden_manager_identity(void)
{
    return NULL;
}
#else
/**
 * Reload the hidden manager identity record from disk.
 *
 * Must run with a credential that can read /data/adb/ksu (track_throne already
 * overrides credentials with ksu_cred). Returns true when a valid record was
 * loaded; on failure the previous record is invalidated.
 */
bool ksu_hidden_manager_load_identity(void);

/* The currently loaded identity, or NULL when no valid record is present. */
const struct ksu_manager_identity_record *ksu_hidden_manager_identity(void);
#endif

#endif /* __KSU_H_HIDDEN_MANAGER */
