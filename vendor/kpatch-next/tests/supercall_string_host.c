#define _GNU_SOURCE
#define KP_SUPERCALL_STRING_HOST_TEST
#include "../kernel/patch/common/supercall.c"

static void reset_test(void)
{
    assert(!active_image_copy);
    host_strncpy_from_user_nofault = host_nofault;
    host_strncpy_from_unsafe_user = host_unsafe;
    host_vmemdup_user = host_duplicate_user_image;
    nofault_calls = unsafe_calls = 0;
    load_calls = control_calls = unload_calls = info_calls = 0;
    image_copy_calls = image_allocations = image_copy_error_frees = image_frees = 0;
    buffer_load_calls = image_sequence = image_copy_step = image_load_step = image_free_step = 0;
    forced_errno = forced_zero = forced_unterminated = output_copy_failure = 0;
    image_copy_errno = image_copy_null = mutate_image_after_copy = 0;
    buffer_load_result = 0;
    captured_image_source = NULL;
    requested_image_length = captured_image_length = 0;
    memset(captured_image, 0, sizeof(captured_image));
    captured_name[0] = captured_args[0] = 0;
    captured_event[0] = 0;
    captured_args_null = 0;
    checkpoint_count = 0;
}

static void normal_copy_prefers_nofault_test(void)
{
    char buffer[16];
    assert(kpm_copy_string_nofault(buffer, "root", sizeof(buffer), 1) == 4);
    assert(!strcmp(buffer, "root"));
    assert(nofault_calls == 1 && unsafe_calls == 0);
}

static void null_and_bad_capacity_test(void)
{
    char buffer[8] = "stale";
    assert(kpm_copy_string_nofault(buffer, NULL, sizeof(buffer), 1) == -EFAULT);
    assert(buffer[0] == 0 && nofault_calls == 0);
    strcpy(buffer, "stale");
    assert(kpm_copy_string_nofault(buffer, NULL, sizeof(buffer), 0) == 0);
    assert(buffer[0] == 0 && nofault_calls == 0);
    assert(kpm_copy_string_nofault(NULL, "x", 8, 1) == -EINVAL);
    assert(kpm_copy_string_nofault(buffer, "x", 1, 1) == -EINVAL);
    assert(kpm_copy_string_nofault(buffer, "x", 0, 1) == -EINVAL);
    assert(kpm_copy_string_nofault(buffer, "x", -1, 1) == -EINVAL);
}

static void empty_string_test(void)
{
    char buffer[8];
    assert(kpm_copy_string_nofault(buffer, "", sizeof(buffer), 1) == -EINVAL);
    assert(kpm_copy_string_nofault(buffer, "", sizeof(buffer), 0) == 0);
    assert(buffer[0] == 0);
    assert(call_kpm_load("", NULL, NULL) == -EINVAL);
    assert(load_calls == 0);
}

static void exact_public_limit_test(void)
{
    char source[1024], destination[1025];
    memset(source, 'a', sizeof(source) - 1);
    source[sizeof(source) - 1] = 0;
    assert(kpm_copy_string_nofault(destination, source, sizeof(destination), 1) == 1023);
    assert(!strcmp(source, destination));
    assert(call_kpm_load(source, source, NULL) == 0);
    assert(load_calls == 1 && strlen(captured_name) == 1023 && strlen(captured_args) == 1023);

    char name[KPM_NAME_LEN];
    memset(name, 'n', sizeof(name) - 1);
    name[sizeof(name) - 1] = 0;
    assert(call_kpm_unload(name, NULL) == -EINPROGRESS);
    assert(unload_calls == 1 && !strcmp(captured_name, name));
}

static void truncation_rejected_test(void)
{
    char source[1025], destination[1025];
    memset(source, 'a', sizeof(source) - 1);
    source[sizeof(source) - 1] = 0;
    assert(kpm_copy_string_nofault(destination, source, sizeof(destination), 0) == -ENAMETOOLONG);
    assert(destination[0] == 0);
    assert(call_kpm_load(source, "", NULL) == -ENAMETOOLONG);
    assert(load_calls == 0);
    checkpoint_count = 0;
    assert(call_kpm_load("/valid.kpm", source, NULL) == -ENAMETOOLONG);
    assert(load_calls == 0);

    char name[KPM_NAME_LEN + 1], output[64];
    memset(name, 'n', sizeof(name) - 1);
    name[sizeof(name) - 1] = 0;
    assert(call_kpm_unload(name, NULL) == -ENAMETOOLONG);
    assert(call_kpm_control(name, "", output, sizeof(output)) == -ENAMETOOLONG);
    assert(call_kpm_info(name, output, sizeof(output)) == -ENAMETOOLONG);
    assert(!unload_calls && !control_calls && !info_calls);
}

static void negative_errno_propagation_test(void)
{
    char destination[32];
    forced_errno = -EACCES;
    assert(kpm_copy_string_nofault(destination, "x", sizeof(destination), 1) == -EACCES);
    assert(destination[0] == 0);
    assert(call_kpm_load("/x.kpm", NULL, NULL) == -EACCES);
    assert(!load_calls && !unsafe_calls);
}

static void unavailable_symbol_test(void)
{
    char destination[32];
    host_strncpy_from_user_nofault = NULL;
    assert(kpm_copy_string_nofault(destination, "legacy", sizeof(destination), 1) == 6);
    assert(!strcmp(destination, "legacy"));
    assert(unsafe_calls == 1 && nofault_calls == 0);
    host_strncpy_from_unsafe_user = NULL;
    assert(kpm_copy_string_nofault(destination, "x", sizeof(destination), 1) == -ENOSYS);
    assert(destination[0] == 0);
    assert(call_kpm_load("/x.kpm", NULL, NULL) == -ENOSYS);
    assert(!load_calls);
}

static void malformed_copy_result_test(void)
{
    char destination[32];
    forced_zero = 1;
    assert(kpm_copy_string_nofault(destination, "x", sizeof(destination), 1) == -EFAULT);
    assert(destination[0] == 0);
    forced_zero = 0;
    forced_unterminated = 1;
    assert(kpm_copy_string_nofault(destination, "x", sizeof(destination), 1) == -EFAULT);
    assert(destination[0] == 0);
}

static void page_boundary_and_inaccessible_source_test(void)
{
    long page = sysconf(_SC_PAGESIZE);
    assert(page > 0);
    char *mapping = mmap(NULL, page * 2, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(mapping != MAP_FAILED);
    assert(!mprotect(mapping + page, page, PROT_NONE));
    mapping[page - 2] = 'x';
    mapping[page - 1] = 0;
    char destination[64];
    assert(kpm_copy_string_nofault(destination, mapping + page - 2, sizeof(destination), 1) == 1);
    assert(!strcmp(destination, "x"));
    mapping[page - 1] = 'y';
    assert(kpm_copy_string_nofault(destination, mapping + page - 2, sizeof(destination), 1) == -EFAULT);
    assert(destination[0] == 0);
    assert(kpm_copy_string_nofault(destination, (const char *)1, sizeof(destination), 1) == -EFAULT);
    assert(destination[0] == 0);
    assert(!munmap(mapping, page * 2));
}

static void load_checkpoints_and_literal_arguments_test(void)
{
    const char *literal = "'single' \"double\" ; $(NOT_EXECUTED)\nline2";
    assert(call_kpm_load("/literal path.kpm", literal, NULL) == 0);
    assert(load_calls == 1 && !captured_args_null);
    assert(!strcmp(captured_name, "/literal path.kpm") && !strcmp(captured_args, literal));
    assert(checkpoint_count == 5);
    assert(strstr(checkpoints[0], "checkpoint=ENTRY"));
    assert(strstr(checkpoints[1], "path-copy-start"));
    assert(strstr(checkpoints[2], "path-copy-returned"));
    assert(strstr(checkpoints[3], "args-copy-start"));
    assert(strstr(checkpoints[4], "args-copy-returned"));
    checkpoint_count = 0;
    assert(call_kpm_load("/x.kpm", NULL, NULL) == 0);
    assert(captured_args_null && load_calls == 2);
}

static void wrappers_fault_before_dispatch_test(void)
{
    const char *invalid = (const char *)1;
    char output[64];
    assert(call_kpm_load(invalid, "", NULL) == -EFAULT);
    assert(call_kpm_control(invalid, "x", output, sizeof(output)) == -EFAULT);
    assert(call_kpm_unload(invalid, NULL) == -EFAULT);
    assert(call_kpm_info(invalid, output, sizeof(output)) == -EFAULT);
    assert(!load_calls && !control_calls && !unload_calls && !info_calls);
    checkpoint_count = 0;
    assert(call_kpm_load("/x.kpm", invalid, NULL) == -EFAULT);
    assert(call_kpm_control("x", invalid, output, sizeof(output)) == -EFAULT);
    assert(!load_calls && !control_calls);
}

static void wrapper_control_info_success_and_output_error_test(void)
{
    char output[64];
    assert(call_kpm_control("fixture", "", output, sizeof(output)) == 0);
    assert(control_calls == 1 && !strcmp(captured_args, ""));
    assert(call_kpm_info("fixture", output, sizeof(output)) > 0);
    assert(!strcmp(output, "name=fixture\n"));
    assert(call_kpm_info("fixture", output, 2) == -ENOBUFS);
    assert(call_kpm_info("fixture", output, 0) == -EINVAL);
    output_copy_failure = 1;
    assert(call_kpm_info("fixture", output, sizeof(output)) == -EFAULT);
}

static void buffer_invalid_length_test(void)
{
    const unsigned char image[] = { 0x7f, 'E', 'L', 'F', 0 };
    assert(call_kpm_load_buffer(image, 0, NULL, NULL) == -E2BIG);
    assert(call_kpm_load_buffer(image, (32ul << 20) + 1, NULL, NULL) == -E2BIG);
    assert(call_kpm_load_buffer(image, ULONG_MAX, NULL, NULL) == -E2BIG);
    assert(!image_copy_calls && !nofault_calls && !buffer_load_calls && !load_calls);
    assert(!image_allocations && !image_frees && !checkpoint_count);
}

static void buffer_null_and_reserved_test(void)
{
    const unsigned char image[] = { 0 };
    assert(call_kpm_load_buffer(NULL, sizeof(image), NULL, NULL) == -EFAULT);
    assert(call_kpm_load_buffer(image, sizeof(image), NULL, (void *)1) == -EINVAL);
    assert(!image_copy_calls && !nofault_calls && !buffer_load_calls && !load_calls);
    assert(!image_allocations && !image_frees && !checkpoint_count);
}

static void buffer_missing_symbol_test(void)
{
    const unsigned char image[] = { 0 };
    host_vmemdup_user = NULL;
    assert(call_kpm_load_buffer(image, sizeof(image), "args", NULL) == -ENOSYS);
    assert(!image_copy_calls && !nofault_calls && !buffer_load_calls && !load_calls);
    assert(!image_allocations && !image_frees && !checkpoint_count);
}

static void buffer_copy_errno_test(void)
{
    const unsigned char image[] = { 0 };
    const int errors[] = { -EFAULT, -ENOMEM, -EACCES };
    for (size_t i = 0; i < sizeof(errors) / sizeof(errors[0]); i++) {
        reset_test();
        image_copy_errno = errors[i];
        assert(call_kpm_load_buffer(image, sizeof(image), NULL, NULL) == errors[i]);
        assert(image_copy_calls == 1 && requested_image_length == sizeof(image));
        assert(!buffer_load_calls && !load_calls && !image_allocations && !image_frees);
        assert(checkpoint_count == 1 && strstr(checkpoints[0], "copy-start"));
    }
}

static void buffer_null_copy_result_test(void)
{
    const unsigned char image[] = { 0 };
    image_copy_null = 1;
    assert(call_kpm_load_buffer(image, sizeof(image), NULL, NULL) == -ENOMEM);
    assert(image_copy_calls == 1 && !buffer_load_calls && !image_frees);
}

static void buffer_exact_upper_limit_test(void)
{
    const unsigned char image[] = { 0 };
    image_copy_errno = -ENOMEM;
    assert(call_kpm_load_buffer(image, 32ul << 20, NULL, NULL) == -ENOMEM);
    assert(image_copy_calls == 1 && requested_image_length == (32ul << 20));
    assert(!buffer_load_calls && !image_allocations && !image_frees);
}

static void assert_buffer_success(const unsigned char *image, size_t length)
{
    assert(image_copy_calls == 1 && buffer_load_calls == 1 && !load_calls);
    assert(image_allocations == 1 && image_frees == 1 && !image_copy_error_frees);
    assert(!active_image_copy && captured_image_length == length);
    assert(!memcmp(captured_image, image, length));
    assert(!strcmp(captured_event, "load-file"));
    assert(image_copy_step == 1 && image_load_step == 2 && image_free_step == 3);
    assert(checkpoint_count == 2);
    assert(strstr(checkpoints[0], "copy-start") && strstr(checkpoints[1], "copy-returned"));
}

static void buffer_exact_binary_and_literal_args_test(void)
{
    const unsigned char image[] = { 0x7f, 'E', 'L', 'F', 0, 0xff, 0, 0x80, 1, 0, 2 };
    const char *literal = "'single' \"double\" ; $(NOT_EXECUTED)\nline2";
    assert(call_kpm_load_buffer(image, sizeof(image), literal, NULL) == 0);
    assert_buffer_success(image, sizeof(image));
    assert(!captured_args_null && !strcmp(captured_args, literal));
}

static void buffer_optional_args_test(void)
{
    const unsigned char image[] = { 0, 1, 0, 2 };
    const char *args[] = { NULL, "" };
    for (size_t i = 0; i < sizeof(args) / sizeof(args[0]); i++) {
        reset_test();
        assert(call_kpm_load_buffer(image, sizeof(image), args[i], NULL) == 0);
        assert_buffer_success(image, sizeof(image));
        assert(captured_args_null && !captured_args[0]);
    }
}

static void buffer_bad_args_before_allocation_test(void)
{
    const unsigned char image[] = { 0 };
    assert(call_kpm_load_buffer(image, sizeof(image), (const char *)1, NULL) == -EFAULT);
    assert(!image_copy_calls && !buffer_load_calls && !image_frees);
    reset_test();
    forced_errno = -EACCES;
    assert(call_kpm_load_buffer(image, sizeof(image), "args", NULL) == -EACCES);
    assert(!image_copy_calls && !buffer_load_calls && !image_frees);
    reset_test();
    char args[KPM_ARGS_LEN + 1];
    memset(args, 'x', sizeof(args) - 1);
    args[sizeof(args) - 1] = 0;
    assert(call_kpm_load_buffer(image, sizeof(image), args, NULL) == -ENAMETOOLONG);
    assert(!image_copy_calls && !buffer_load_calls && !image_frees);
    reset_test();
    host_strncpy_from_user_nofault = host_strncpy_from_unsafe_user = NULL;
    assert(call_kpm_load_buffer(image, sizeof(image), "args", NULL) == -ENOSYS);
    assert(!image_copy_calls && !buffer_load_calls && !image_frees);
}

static void buffer_exact_args_limit_test(void)
{
    const unsigned char image[] = { 0, 1, 0 };
    char args[KPM_ARGS_LEN];
    memset(args, 'a', sizeof(args) - 1);
    args[sizeof(args) - 1] = 0;
    assert(call_kpm_load_buffer(image, sizeof(image), args, NULL) == 0);
    assert_buffer_success(image, sizeof(image));
    assert(!captured_args_null && !strcmp(captured_args, args));
}

static void buffer_loader_failure_cleanup_test(void)
{
    const unsigned char image[] = { 0, 1, 0, 2 };
    const long results[] = { -ENOEXEC, -EPERM, -EIO, -EBUSY, -EINPROGRESS };
    for (size_t i = 0; i < sizeof(results) / sizeof(results[0]); i++) {
        reset_test();
        buffer_load_result = results[i];
        assert(call_kpm_load_buffer(image, sizeof(image), "", NULL) == results[i]);
        assert_buffer_success(image, sizeof(image));
    }
}

static void buffer_page_boundary_and_partial_copy_test(void)
{
    long page = sysconf(_SC_PAGESIZE);
    assert(page >= 32);
    unsigned char *mapping = mmap(NULL, (size_t)page * 3, PROT_READ | PROT_WRITE,
                                 MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(mapping != MAP_FAILED);
    assert(!mprotect(mapping + page * 2, (size_t)page, PROT_NONE));
    for (int i = 0; i < 32; i++) mapping[page - 16 + i] = i % 3 ? (unsigned char)i : 0;
    assert(call_kpm_load_buffer(mapping + page - 16, 32, NULL, NULL) == 0);
    assert_buffer_success(mapping + page - 16, 32);
    reset_test();
    assert(call_kpm_load_buffer(mapping + page * 2 - 16, 32, NULL, NULL) == -EFAULT);
    assert(image_copy_calls == 1 && image_allocations == 1 && image_copy_error_frees == 1);
    assert(!buffer_load_calls && !image_frees && !active_image_copy);
    reset_test();
    assert(call_kpm_load_buffer((const void *)1, 32, NULL, NULL) == -EFAULT);
    assert(image_copy_calls == 1 && image_allocations == 1 && image_copy_error_frees == 1);
    assert(!buffer_load_calls && !image_frees && !active_image_copy);
    assert(!munmap(mapping, (size_t)page * 3));
}

static void buffer_snapshot_survives_user_mutation_test(void)
{
    unsigned char image[] = { 0x7f, 'E', 'L', 'F', 0, 0xff, 0, 2 };
    unsigned char expected[sizeof(image)];
    memcpy(expected, image, sizeof(image));
    mutate_image_after_copy = 1;
    assert(call_kpm_load_buffer(image, sizeof(image), NULL, NULL) == 0);
    assert_buffer_success(expected, sizeof(expected));
    for (size_t i = 0; i < sizeof(image); i++) assert(image[i] == 0x5a);
}

int main(void)
{
    unsigned int groups = 0;
#define RUN(test) do { \
    reset_test(); test(); \
    assert(!active_image_copy); \
    assert(image_allocations == image_copy_error_frees + image_frees); \
    groups++; \
} while (0)
    RUN(normal_copy_prefers_nofault_test);
    RUN(null_and_bad_capacity_test);
    RUN(empty_string_test);
    RUN(exact_public_limit_test);
    RUN(truncation_rejected_test);
    RUN(negative_errno_propagation_test);
    RUN(unavailable_symbol_test);
    RUN(malformed_copy_result_test);
    RUN(page_boundary_and_inaccessible_source_test);
    RUN(load_checkpoints_and_literal_arguments_test);
    RUN(wrappers_fault_before_dispatch_test);
    RUN(wrapper_control_info_success_and_output_error_test);
    RUN(buffer_invalid_length_test);
    RUN(buffer_null_and_reserved_test);
    RUN(buffer_missing_symbol_test);
    RUN(buffer_copy_errno_test);
    RUN(buffer_null_copy_result_test);
    RUN(buffer_exact_upper_limit_test);
    RUN(buffer_exact_binary_and_literal_args_test);
    RUN(buffer_optional_args_test);
    RUN(buffer_bad_args_before_allocation_test);
    RUN(buffer_exact_args_limit_test);
    RUN(buffer_loader_failure_cleanup_test);
    RUN(buffer_page_boundary_and_partial_copy_test);
    RUN(buffer_snapshot_survives_user_mutation_test);
    printf("production KPM supercall tests: PASS (%u groups; 12 string, %u load-buffer)\n",
           groups, groups - 12);
    return 0;
}
