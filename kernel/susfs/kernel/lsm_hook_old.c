// SPDX-License-Identifier: GPL-2.0-only
/* 4.x (< 4.18) LSM hook insertion: security_hook_list is a struct list_head. */
#include <linux/version.h>

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 18, 0)

#include <linux/errno.h>
#include <linux/mutex.h>
#include <linux/rcupdate.h>
#include <linux/delay.h>
#include <linux/list.h>
#include "lsm_hook.h"
#include "patch_memory.h"
#include "symbol_resolver.h"
#include "susfs_log.h"

#define KSU_LSM_MAX_TRACKED 16

static DEFINE_MUTEX(ksu_lsm_old_lock);
static struct ksu_lsm_hook *ksu_lsm_old_entries[KSU_LSM_MAX_TRACKED];
static int ksu_lsm_old_count;

int ksu_lsm_hook(struct ksu_lsm_hook *hook)
{
    struct list_head *head;
    struct security_hook_list *node;
    struct security_hook_list *first;
    void *new_next, *new_prev;
    unsigned long heads_addr;
    int ret = 0;

    if (!hook || !hook->replacement)
        return -EINVAL;

    mutex_lock(&ksu_lsm_old_lock);
    if (hook->entry) {
        ret = -EALREADY;
        goto out;
    }
    if (ksu_lsm_old_count >= KSU_LSM_MAX_TRACKED) {
        ret = -ENOSPC;
        goto out;
    }

    heads_addr = find_kernel_symbol_exact("security_hook_heads");
    if (!heads_addr) {
        pr_err("lsm_hook_old: security_hook_heads not found\n");
        ret = -ENOENT;
        goto out;
    }

    head = (struct list_head *)(heads_addr + hook->head_offset);
    if (!head->next || !head->prev) {
        ret = -EINVAL;
        goto out;
    }

    node = &hook->list;
    memset(node, 0, sizeof(*node));
    *(void **)((char *)node + hook->hook_offset) = hook->replacement;
/* struct security_hook_list.lsm exists from 4.12 (LSM label stacking). */
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 12, 0)
    node->lsm = "susfs";
#endif

    first = list_entry(head->next, struct security_hook_list, list);
    node->list.next = &first->list;
    node->list.prev = head;

    /* publish: head->next = node */
    new_next = &node->list;
    ret = ksu_patch_text(&head->next, &new_next, sizeof(new_next),
                         KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (ret)
        goto out;
    /* displaced first node's back pointer = node */
    new_prev = &node->list;
    ret = ksu_patch_text(&first->list.prev, &new_prev, sizeof(new_prev),
                         KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (ret) {
        struct list_head *old_first = &first->list;

        ksu_patch_text(&head->next, &old_first, sizeof(old_first),
                       KSU_PATCH_TEXT_FLUSH_DCACHE);
        goto out;
    }

    hook->entry = node;
    ksu_lsm_old_entries[ksu_lsm_old_count++] = hook;
    SUSFS_LOGI("lsm_hook_old: inserted %s at head %px\n",
               hook->head_name ?: "unknown", head);
out:
    mutex_unlock(&ksu_lsm_old_lock);
    return ret;
}

void ksu_lsm_unhook(struct ksu_lsm_hook *hook)
{
    struct security_hook_list *node;
    struct list_head *prev, *next;
    void *value;
    int i;

    if (!hook)
        return;
    mutex_lock(&ksu_lsm_old_lock);
    if (!hook->entry) {
        mutex_unlock(&ksu_lsm_old_lock);
        return;
    }
    node = &hook->list;
    prev = node->list.prev;
    next = node->list.next;

    value = next;
    ksu_patch_text(&prev->next, &value, sizeof(value), KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (next) {
        value = prev;
        ksu_patch_text(&next->prev, &value, sizeof(value), KSU_PATCH_TEXT_FLUSH_DCACHE);
    }
    node->list.next = NULL;
    node->list.prev = NULL;
    hook->entry = NULL;
    for (i = 0; i < ksu_lsm_old_count; i++) {
        if (ksu_lsm_old_entries[i] == hook) {
            ksu_lsm_old_entries[i] = ksu_lsm_old_entries[--ksu_lsm_old_count];
            break;
        }
    }
    SUSFS_LOGI("lsm_hook_old: removed %s\n", hook->head_name ?: "unknown");
    mutex_unlock(&ksu_lsm_old_lock);
}

int ksu_register_lsm_hook(struct ksu_lsm_hook *hook) { return ksu_lsm_hook(hook); }
void ksu_unregister_lsm_hook(struct ksu_lsm_hook *hook) { ksu_lsm_unhook(hook); }

bool ksu_lsm_hook_ready(void)
{
    return find_kernel_symbol_exact("security_hook_heads") != 0;
}

void ksu_lsm_hook_init(void)
{
    SUSFS_LOGI("lsm_hook_old: init (4.x list_head mode)\n");
}

void ksu_lsm_hook_exit(void)
{
    int i;

    mutex_lock(&ksu_lsm_old_lock);
    for (i = ksu_lsm_old_count - 1; i >= 0; i--)
        ksu_lsm_unhook(ksu_lsm_old_entries[i]);
    mutex_unlock(&ksu_lsm_old_lock);
}

void susfs_wait_for_readers(void)
{
    synchronize_rcu();
    msleep(50);
}

#endif /* < 4.18 */
