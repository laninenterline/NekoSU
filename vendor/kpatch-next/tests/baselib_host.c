#define _GNU_SOURCE
#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include "../kernel/include/baselib.h"

void *milksu_host_global_memset(void *destination, int byte, size_t length);

struct guarded_buffer {
    unsigned char *mapping;
    unsigned char *end;
    size_t page;
};

static struct guarded_buffer guarded_buffer(void)
{
    long page = sysconf(_SC_PAGESIZE);
    assert(page >= 512);
    unsigned char *mapping = mmap(NULL, (size_t)page * 3, PROT_NONE,
                                 MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(mapping != MAP_FAILED);
    assert(!mprotect(mapping + page, (size_t)page, PROT_READ | PROT_WRITE));
    return (struct guarded_buffer){ mapping, mapping + page * 2, (size_t)page };
}

static void release_buffer(struct guarded_buffer *buffer)
{
    assert(!munmap(buffer->mapping, buffer->page * 3));
}

static int sign_of(int value)
{
    return (value > 0) - (value < 0);
}

static size_t test_length(unsigned int index, size_t page)
{
    const size_t lengths[] = { 0, 1, 2, 3, 7, 15, 16, 31, 63, 127, 255 };
    return index < sizeof(lengths) / sizeof(lengths[0]) ? lengths[index] : page - 1;
}

static void memcmp_boundaries(void)
{
    struct guarded_buffer left = guarded_buffer(), right = guarded_buffer();
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, left.page);
        unsigned char *a = left.end - length, *b = right.end - length;
        memset(a, 0x80, length);
        memset(b, 0x80, length);
        assert(lib_memcmp(a, b, length) == 0);
        if (length) {
            a[length - 1] = 0xff;
            b[length - 1] = 0x7f;
            assert(sign_of(lib_memcmp(a, b, length)) == sign_of(memcmp(a, b, length)));
            assert(sign_of(lib_memcmp(b, a, length)) == sign_of(memcmp(b, a, length)));
        }
    }
    release_buffer(&left);
    release_buffer(&right);
}

static void memchr_boundaries(void)
{
    struct guarded_buffer buffer = guarded_buffer();
    const int needles[] = { 0, 1, 0xa5, 0xff, -1, 0x100, 0x1ff };
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, buffer.page);
        unsigned char *bytes = buffer.end - length;
        memset(bytes, 0xa5, length);
        if (length) bytes[length - 1] = 0xff;
        if (length > 1) bytes[0] = 0;
        for (size_t needle = 0; needle < sizeof(needles) / sizeof(needles[0]); needle++)
            assert(lib_memchr(bytes, needles[needle], length) == memchr(bytes, needles[needle], length));
    }
    release_buffer(&buffer);
}

static void memcpy_boundaries(void)
{
    struct guarded_buffer source = guarded_buffer(), destination = guarded_buffer();
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, source.page);
        unsigned char *from = source.end - length, *to = destination.end - length;
        for (size_t i = 0; i < length; i++) from[i] = (unsigned char)(i * 37);
        memset(to, 0x5a, length);
        to[-1] = 0x33;
        assert(lib_memcpy(to, from, length) == to);
        assert(!memcmp(to, from, length) && to[-1] == 0x33);
        for (size_t i = 0; i < length; i++) assert(from[i] == (unsigned char)(i * 37));
    }
    release_buffer(&source);
    release_buffer(&destination);
}

static void memset_boundaries(void)
{
    struct guarded_buffer buffer = guarded_buffer();
    const int values[] = { 0, 0x80, 0x1ff, -1 };
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, buffer.page);
        unsigned char *bytes = buffer.end - length;
        for (size_t value = 0; value < sizeof(values) / sizeof(values[0]); value++) {
            bytes[-1] = 0x33;
            assert(lib_memset(bytes, values[value], length) == bytes);
            assert(bytes[-1] == 0x33);
            for (size_t i = 0; i < length; i++) assert(bytes[i] == (unsigned char)values[value]);
        }
    }
    release_buffer(&buffer);
}

static void strlen_boundaries(void)
{
    struct guarded_buffer buffer = guarded_buffer();
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, buffer.page);
        char *text = (char *)buffer.end - length - 1;
        memset(text, 0x80, length);
        text[length] = 0;
        assert(lib_strlen(text) == length && lib_strlen(text) == strlen(text));
        text[length / 2] = 0;
        assert(lib_strlen(text) == length / 2);
    }
    release_buffer(&buffer);
}

static void strchr_boundaries(void)
{
    struct guarded_buffer buffer = guarded_buffer();
    const int needles[] = { 0, 1, 'a', 0x80, 0xff, -1, 0x1ff };
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, buffer.page);
        char *text = (char *)buffer.end - length - 1;
        memset(text, 'a', length);
        if (length) text[0] = (char)0x80;
        if (length > 1) text[length - 1] = (char)0xff;
        text[length] = 0;
        for (size_t needle = 0; needle < sizeof(needles) / sizeof(needles[0]); needle++)
            assert(lib_strchr(text, needles[needle]) == strchr(text, needles[needle]));
        assert(lib_strchr(text, 0) == text + length);
    }
    release_buffer(&buffer);
}

static void strcmp_boundaries(void)
{
    struct guarded_buffer left = guarded_buffer(), right = guarded_buffer();
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, left.page);
        char *a = (char *)left.end - length - 1, *b = (char *)right.end - length - 1;
        memset(a, 0x80, length);
        memset(b, 0x80, length);
        a[length] = b[length] = 0;
        assert(lib_strcmp(a, b) == 0);
        if (length) {
            a[length - 1] = (char)0xff;
            b[length - 1] = 0x7f;
            assert(sign_of(lib_strcmp(a, b)) == sign_of(strcmp(a, b)));
            assert(sign_of(lib_strcmp(b, a)) == sign_of(strcmp(b, a)));
            b[length - 1] = 0;
            assert(lib_strcmp(a, b) > 0);
        }
    }
    release_buffer(&left);
    release_buffer(&right);
}

static void strncmp_boundaries(void)
{
    struct guarded_buffer left = guarded_buffer(), right = guarded_buffer();
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, left.page);
        char *a = (char *)left.end - length, *b = (char *)right.end - length;
        memset(a, 0x80, length);
        memset(b, 0x80, length);
        assert(lib_strncmp(a, b, length) == 0);
        if (length) {
            a[length - 1] = (char)0xff;
            b[length - 1] = 0x7f;
            assert(sign_of(lib_strncmp(a, b, length)) == sign_of(strncmp(a, b, length)));
            a[length - 1] = b[length - 1] = 0;
            assert(lib_strncmp(a, b, length + 8) == 0);
        }
    }
    release_buffer(&left);
    release_buffer(&right);
}

static void strcpy_boundaries(void)
{
    struct guarded_buffer source = guarded_buffer(), destination = guarded_buffer();
    for (unsigned int index = 0; index < 12; index++) {
        size_t length = test_length(index, source.page);
        char *from = (char *)source.end - length - 1, *to = (char *)destination.end - length - 1;
        memset(from, 0x80, length);
        from[length] = 0;
        if (length + 1 < destination.page) to[-1] = 0x33;
        assert(lib_strcpy(to, from) == to && !memcmp(to, from, length + 1));
        if (length + 1 < destination.page) assert(to[-1] == 0x33);
        if (length) {
            from[length / 2] = 0;
            memset(to, 0x5a, length + 1);
            assert(lib_strcpy(to, from) == to);
            assert(!memcmp(to, from, length / 2 + 1) && to[length / 2 + 1] == 0x5a);
        }
    }
    release_buffer(&source);
    release_buffer(&destination);
}

int main(void)
{
    assert(lib_memset != memset && milksu_host_global_memset != memset);
    assert(lib_memcpy != memcpy);
    memcmp_boundaries();
    memchr_boundaries();
    memcpy_boundaries();
    memset_boundaries();
    strlen_boundaries();
    strchr_boundaries();
    strcmp_boundaries();
    strncmp_boundaries();
    strcpy_boundaries();
    puts("production baselib: PASS (9 groups; 9 loader helpers)");
    return 0;
}
