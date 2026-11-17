#include <linux/err.h>
#include <linux/fs.h>
#include <linux/gfp.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/version.h>
#include <linux/sched.h>
#include <linux/pid.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#include <linux/sched/task.h>
#else
#include <linux/sched.h>
#endif
#endif
#ifdef CONFIG_KSU_DEBUG
#include <linux/moduleparam.h>
#endif
#include <crypto/hash.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
#include <crypto/sha2.h>
#else
#include <crypto/sha.h>
#endif

#include "manager/throne_tracker.h"
#include "compat/kernel_compat.h"
#include "dynamic_manager.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"
#include "ksu.h"

// Dynamic manager signatures. The kernel accepts several dynamically added
// managers at once (KernelSU forks, third-party managers, ...).
#define MAX_DYNAMIC_MANAGERS 8
static struct dynamic_manager_config dynamic_managers[MAX_DYNAMIC_MANAGERS];
static int dynamic_manager_count;

bool ksu_is_dynamic_manager_enabled(void)
{
    return dynamic_manager_count > 0;
}

bool ksu_match_dynamic_manager_sign(unsigned size, const char *hash)
{
    int i;

    for (i = 0; i < dynamic_manager_count; i++) {
        if (dynamic_managers[i].size == size &&
            strcmp(dynamic_managers[i].hash, hash) == 0)
            return true;
    }
    return false;
}

int ksu_handle_dynamic_manager(struct ksu_dynamic_manager_cmd *cmd)
{
    unsigned long flags;
    int ret = 0;
    int i;

    if (!cmd) {
        return -EINVAL;
    }

    switch (cmd->operation) {
    case DYNAMIC_MANAGER_OP_SET_SYNCHRONOUS:
    case DYNAMIC_MANAGER_OP_SET:
        if (cmd->size < 0x100 || cmd->size > 0x1000) {
            pr_err("invalid size: 0x%x\n", cmd->size);
            return -EINVAL;
        }

        // Validate hash format
        for (i = 0; i < 64; i++) {
            char c = cmd->hash[i];
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                pr_err("invalid hash character at position %d: %c\n", i, c);
                return -EINVAL;
            }
        }

        {
            char hash[65];
            bool duplicate = false;

            memcpy(hash, cmd->hash, 64);
            hash[64] = '\0';
            for (i = 0; i < dynamic_manager_count; i++) {
                if (dynamic_managers[i].size == cmd->size &&
                    strcmp(dynamic_managers[i].hash, hash) == 0) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                if (dynamic_manager_count >= MAX_DYNAMIC_MANAGERS) {
                    memmove(&dynamic_managers[0], &dynamic_managers[1],
                            sizeof(dynamic_managers[0]) * (MAX_DYNAMIC_MANAGERS - 1));
                    dynamic_manager_count = MAX_DYNAMIC_MANAGERS - 1;
                }
                dynamic_managers[dynamic_manager_count].size = cmd->size;
                memcpy(dynamic_managers[dynamic_manager_count].hash, hash, sizeof(hash));
                dynamic_managers[dynamic_manager_count].is_set = 1;
                dynamic_manager_count++;
            }
        }

        if (cmd->operation == DYNAMIC_MANAGER_OP_SET_SYNCHRONOUS)
            track_throne(TRACK_THRONE_FORCE_SYNCHRONOUS);
        else
            track_throne(0);
        pr_info("dynamic manager added: size=0x%x, hash=%.16s (count=%d)\n", cmd->size, cmd->hash, dynamic_manager_count);
        break;

    case DYNAMIC_MANAGER_OP_GET:
        if (dynamic_manager_count > 0) {
            cmd->size = dynamic_managers[dynamic_manager_count - 1].size;
            memcpy(cmd->hash, dynamic_managers[dynamic_manager_count - 1].hash, 64);
            ret = 0;
        } else {
            ret = -ENODATA;
        }
        break;
    case DYNAMIC_MANAGER_OP_WIPE:
        dynamic_manager_count = 0;
        ret = 0;
        ksu_unregister_manager_by_signature_index(KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER);
        pr_info("dynamic manager kernel settings reset");
        break;

    default:
        pr_err("Invalid dynamic manager operation: %d\n", cmd->operation);
        return -EINVAL;
    }

    return ret;
}
