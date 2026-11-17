#ifndef MILKSU_LOADER_HOST_STUBS_H
#define MILKSU_LOADER_HOST_STUBS_H

#include <assert.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#define __user
#define unlikely(x) (x)
#define logkd(...) ((void)0)
#define logki(...) ((void)0)
#define logke(...) ((void)0)
#define logkfd(...) ((void)0)
#define logkfi(...) ((void)0)
#define logkfe(...) ((void)0)
#define __ASM_GENERIC_MODULE_H
typedef Elf64_Ehdr Elf_Ehdr;
typedef Elf64_Shdr Elf_Shdr;
typedef Elf64_Sym Elf_Sym;

struct list_head {
    struct list_head *next, *prev;
};
#define INIT_LIST_HEAD(head) ((head)->next = (head)->prev = (head))
#define container_of(ptr, type, member) ((type *)((char *)(ptr) - offsetof(type, member)))
#define list_for_each_entry(pos, head, member) \
    for (pos = container_of((head)->next, typeof(*pos), member); \
         &pos->member != (head); pos = container_of(pos->member.next, typeof(*pos), member))
static void list_add_tail(struct list_head *entry, struct list_head *head)
{
    entry->prev = head->prev;
    entry->next = head;
    head->prev->next = entry;
    head->prev = entry;
}
static void list_del(struct list_head *entry)
{
    entry->next->prev = entry->prev;
    entry->prev->next = entry->next;
}

#include "../kernel/include/kpmodule.h"
#include "../kernel/include/baselib.h"
#include "../kernel/patch/include/module.h"

static unsigned long page_size = 4096;
static int exec_allocations, exec_frees;
static unsigned int cache_sync_calls;
static unsigned long cache_sync_start, cache_sync_end;
static void *last_exec_allocation;
static size_t last_exec_allocation_size;
static int loader_host_callbacks_valid(const struct module *mod);
static void *vmalloc(size_t size) { return malloc(size); }
static void kvfree(void *pointer) { free(pointer); }
static void *kp_malloc_exec(size_t size)
{
    void *pointer = malloc(size);
    if (pointer) {
        exec_allocations++;
        last_exec_allocation = pointer;
        last_exec_allocation_size = size;
    }
    return pointer;
}
static void kp_free_exec(void *pointer)
{
    if (pointer) exec_frees++;
    free(pointer);
}
static void flush_icache_range(unsigned long start, unsigned long end)
{
    assert(start == (unsigned long)last_exec_allocation);
    assert(end >= start);
    assert(end - start == last_exec_allocation_size);
    cache_sync_calls++;
    cache_sync_start = start;
    cache_sync_end = end;
}
static unsigned long symbol_lookup_name(const char *name) { return 0; }
static int apply_relocate(Elf_Shdr *sections, const char *strings, unsigned int symbols,
                          unsigned int relocation, struct module *module)
{
    return -ENOEXEC;
}
static int apply_relocate_add(Elf_Shdr *sections, const char *strings, unsigned int symbols,
                              unsigned int relocation, struct module *module)
{
    return 0;
}
#define IS_ERR(pointer) ((uintptr_t)(pointer) >= (uintptr_t)-4095)
#define PTR_ERR(pointer) ((long)(pointer))

struct file { FILE *stream; };
static struct file *filp_open(const char *path, int flags, int mode)
{
    FILE *stream = fopen(path, "rb");
    if (!stream) return (struct file *)(long)-ENOENT;
    struct file *file = malloc(sizeof(*file));
    assert(file);
    file->stream = stream;
    return file;
}
static void filp_close(struct file *file, void *unused)
{
    fclose(file->stream);
    free(file);
}
static loff_t vfs_llseek(struct file *file, loff_t offset, int whence)
{
    if (fseek(file->stream, offset, whence)) return -EIO;
    return ftell(file->stream);
}
static long kernel_read(struct file *file, void *buffer, size_t size, loff_t *position)
{
    size_t count = fread(buffer, 1, size, file->stream);
    *position += count;
    return count;
}

/* Exercise the same freestanding helpers as the production loader. */
#define memchr lib_memchr
#define memcmp lib_memcmp
#define memcpy lib_memcpy
#define memset lib_memset
#define strcpy lib_strcpy
#define strcmp lib_strcmp
#define strncmp lib_strncmp
#define strlen lib_strlen
#define strchr lib_strchr
#endif
