#ifndef MILKSU_SUPERCALL_STRING_HOST_STUBS_H
#define MILKSU_SUPERCALL_STRING_HOST_STUBS_H

#include <assert.h>
#include <errno.h>
#include <limits.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/uio.h>
#include <unistd.h>

#define __user
#define __noinline __attribute__((noinline))
#include "../kernel/include/kpmodule.h"

typedef long (*host_string_copy_fn)(char *, const char *, long);
typedef void *(*host_image_copy_fn)(const void *, size_t);
static host_string_copy_fn host_strncpy_from_user_nofault;
static host_string_copy_fn host_strncpy_from_unsafe_user;
static host_image_copy_fn host_vmemdup_user;
#define kfunc(name) host_##name
#define IS_ERR(pointer) ((uintptr_t)(pointer) >= (uintptr_t)-4095)
#define PTR_ERR(pointer) ((long)(intptr_t)(pointer))
#define ERR_PTR(error) ((void *)(intptr_t)(error))

static unsigned int nofault_calls, unsafe_calls;
static unsigned int load_calls, control_calls, unload_calls, info_calls;
static unsigned int image_copy_calls, image_allocations, image_copy_error_frees, image_frees;
static unsigned int buffer_load_calls, image_sequence, image_copy_step, image_load_step, image_free_step;
static int forced_errno, forced_zero, forced_unterminated, output_copy_failure;
static int image_copy_errno, image_copy_null, mutate_image_after_copy;
static long buffer_load_result;
static void *active_image_copy;
static const void *captured_image_source;
static size_t requested_image_length, captured_image_length;
static unsigned char captured_image[256];
static char captured_name[1100], captured_args[1100];
static char captured_event[32];
static int captured_args_null;
static char checkpoints[8][120];
static unsigned int checkpoint_count;

static void host_checkpoint(const char *format, ...)
{
    assert(checkpoint_count < sizeof(checkpoints) / sizeof(checkpoints[0]));
    va_list args;
    va_start(args, format);
    vsnprintf(checkpoints[checkpoint_count++], sizeof(checkpoints[0]), format, args);
    va_end(args);
}
#define logki(...) host_checkpoint(__VA_ARGS__)

/* Host analogue of no-fault string copying: process_vm_readv reads this
 * process one byte at a time and returns EFAULT instead of dereferencing an
 * inaccessible source. This intentionally does not use strlen on user input. */
static long host_read_user_string(char *destination, const char *source, long count)
{
    if (forced_errno) {
        destination[0] = 'p';
        return forced_errno;
    }
    if (forced_zero) return 0;
    if (forced_unterminated) {
        destination[0] = destination[1] = 'x';
        return 2;
    }
    for (long i = 0; i < count; i++) {
        unsigned char byte;
        struct iovec local = { .iov_base = &byte, .iov_len = 1 };
        struct iovec remote = {
            .iov_base = (void *)((uintptr_t)source + (uintptr_t)i),
            .iov_len = 1
        };
        if (process_vm_readv(getpid(), &local, 1, &remote, 1, 0) != 1) return -EFAULT;
        destination[i] = byte;
        if (!byte) return i + 1;
    }
    destination[count - 1] = 0;
    return count;
}

static long host_nofault(char *destination, const char *source, long count)
{
    nofault_calls++;
    return host_read_user_string(destination, source, count);
}

static long host_unsafe(char *destination, const char *source, long count)
{
    unsafe_calls++;
    return host_read_user_string(destination, source, count);
}

static void capture_arguments(const char *name, const char *args)
{
    snprintf(captured_name, sizeof(captured_name), "%s", name);
    snprintf(captured_args, sizeof(captured_args), "%s", args ? args : "");
    captured_args_null = args == NULL;
}

static long load_module_path(const char *path, const char *args, void *reserved)
{
    load_calls++;
    capture_arguments(path, args);
    return 0;
}

/* The host kernel performs this bounded copy, so inaccessible and partially
 * accessible mappings fail without dereferencing them in the test process. */
static void *host_duplicate_user_image(const void *source, size_t length)
{
    image_copy_calls++;
    captured_image_source = source;
    requested_image_length = length;
    image_copy_step = ++image_sequence;
    assert(!active_image_copy);
    if (image_copy_errno) return ERR_PTR(image_copy_errno);
    if (image_copy_null) return NULL;
    void *copy = malloc(length);
    assert(copy);
    image_allocations++;
    struct iovec local = { .iov_base = copy, .iov_len = length };
    struct iovec remote = { .iov_base = (void *)source, .iov_len = length };
    ssize_t copied = process_vm_readv(getpid(), &local, 1, &remote, 1, 0);
    if (copied < 0 || (size_t)copied != length) {
        free(copy);
        image_copy_error_frees++;
        return ERR_PTR(-EFAULT);
    }
    active_image_copy = copy;
    if (mutate_image_after_copy) memset((void *)source, 0x5a, length);
    return copy;
}

static long load_module(const void *image, int length, const char *args,
                        const char *event, void *reserved)
{
    buffer_load_calls++;
    image_load_step = ++image_sequence;
    assert(image == active_image_copy && image != captured_image_source);
    assert(length > 0 && (size_t)length == requested_image_length);
    assert((size_t)length <= sizeof(captured_image));
    assert(!reserved && event && !strcmp(event, "load-file"));
    captured_image_length = (size_t)length;
    memcpy(captured_image, image, captured_image_length);
    snprintf(captured_event, sizeof(captured_event), "%s", event);
    capture_arguments("buffer", args);
    return buffer_load_result;
}

static void kvfree(const void *pointer)
{
    assert(pointer && !IS_ERR(pointer) && pointer == active_image_copy);
    assert(image_load_step > image_copy_step);
    image_free_step = ++image_sequence;
    free(active_image_copy);
    active_image_copy = NULL;
    image_frees++;
}

static long module_control0(const char *name, const char *args, char *output, int length)
{
    control_calls++;
    capture_arguments(name, args);
    if (!output || length <= 0) return -EINVAL;
    return 0;
}

static long unload_module(const char *name, void *reserved)
{
    unload_calls++;
    capture_arguments(name, NULL);
    return -EINPROGRESS;
}

static int get_module_info(const char *name, char *output, int length)
{
    info_calls++;
    capture_arguments(name, NULL);
    return snprintf(output, length, "name=%s\n", name);
}

static int compat_copy_to_user(void *destination, const void *source, int length)
{
    if (output_copy_failure) return -EFAULT;
    memcpy(destination, source, length);
    return length;
}

#endif
