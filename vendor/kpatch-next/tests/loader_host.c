#define KP_LOADER_HOST_TEST
#include "../kernel/patch/module/module.c"
#include <pthread.h>

static int init_calls, exit_calls, control_calls;
static long init_result, exit_result;
static pthread_mutex_t test_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t test_condition = PTHREAD_COND_INITIALIZER;
static int block_control, control_entered, release_control;

static long test_init(const char *args, const char *event, void *reserved)
{
    init_calls++;
    assert(get_module_nums() == -EBUSY);
    assert(cache_sync_calls == (unsigned int)init_calls);
    assert(cache_sync_start == (unsigned long)last_exec_allocation);
    assert(cache_sync_end - cache_sync_start == last_exec_allocation_size);
    return init_result;
}

static long test_exit(void *reserved)
{
    exit_calls++;
    assert(get_module_nums() == -EBUSY);
    return exit_result;
}

static long test_control(const char *args, char *output, int length)
{
    control_calls++;
    assert(get_module_nums() == -EBUSY);
    if (block_control) {
        pthread_mutex_lock(&test_mutex);
        control_entered = 1;
        pthread_cond_broadcast(&test_condition);
        while (!release_control) pthread_cond_wait(&test_condition, &test_mutex);
        pthread_mutex_unlock(&test_mutex);
    }
    snprintf(output, length, "%s", args);
    return 0;
}

static long test_control1(void *a1, void *a2, void *a3)
{
    return 0;
}

static int host_callback_slot_in_allocation(const struct module *mod, const void *slot)
{
    if (!mod || !mod->start || !slot) return 0;
    unsigned long start = (unsigned long)mod->start;
    unsigned long address = (unsigned long)slot;
    return !(address & (sizeof(void *) - 1)) && address >= start &&
           address - start <= mod->size && mod->size - (address - start) >= sizeof(void *);
}

/* ARM64 module bytes are not executed by this host harness. Accept only the
 * fixture's native callback for each ABI role, in a real in-allocation slot. */
static int loader_host_callbacks_valid(const struct module *mod)
{
    if (!mod) return 0;
    if (!host_callback_slot_in_allocation(mod, mod->init) ||
        !host_callback_slot_in_allocation(mod, mod->exit)) return 0;
    if (*mod->init != test_init || *mod->exit != test_exit) return 0;
    if (mod->ctl0 && (!host_callback_slot_in_allocation(mod, mod->ctl0) ||
                     (*mod->ctl0 && *mod->ctl0 != test_control))) return 0;
    if (mod->ctl1 && (!host_callback_slot_in_allocation(mod, mod->ctl1) ||
                     (*mod->ctl1 && *mod->ctl1 != test_control1))) return 0;
    return 1;
}

static void reboot_loader(void)
{
    while (modules.list.next != &modules.list) {
        struct module *mod = container_of(modules.list.next, struct module, list);
        modules.list.next = mod->list.next;
        free(mod->args);
        struct control_argument *argument = mod->ctl_history;
        while (argument) {
            struct control_argument *next = argument->next;
            free(argument);
            argument = next;
        }
        free(mod->start);
        free(mod);
    }
    module_init();
    init_calls = exit_calls = control_calls = 0;
    exec_allocations = exec_frees = 0;
    cache_sync_calls = 0;
    cache_sync_start = cache_sync_end = 0;
    last_exec_allocation = NULL;
    last_exec_allocation_size = 0;
    init_result = exit_result = 0;
    block_control = control_entered = release_control = 0;
}

static void *fixture(int with_control, int *length)
{
    const char names[] = "\0.shstrtab\0.strtab\0.symtab\0.kpm.info\0.kpm.init\0.kpm.exit\0.kpm.ctl0\0";
    const char info[] = "name=host-fixture\0version=1.0\0author=MilkSU\0description=Loader test\0";
    unsigned char *bytes = calloc(1, 4096);
    assert(bytes);
    Elf_Ehdr *header = (void *)bytes;
    memcpy(header->e_ident, ELFMAG, SELFMAG);
    header->e_ident[EI_CLASS] = ELFCLASS64;
    header->e_ident[EI_DATA] = ELFDATA2LSB;
    header->e_ident[EI_VERSION] = EV_CURRENT;
    header->e_type = ET_REL;
    header->e_machine = EM_AARCH64;
    header->e_version = EV_CURRENT;
    header->e_ehsize = sizeof(*header);
    header->e_shoff = sizeof(*header);
    header->e_shentsize = sizeof(Elf_Shdr);
    header->e_shnum = with_control ? 8 : 7;
    header->e_shstrndx = 1;
    Elf_Shdr *sections = (void *)(bytes + header->e_shoff);
    const void *data[] = { names, "\0", (Elf_Sym[1]){{0}}, info, &test_init, &test_exit, &test_control };
    unsigned long sizes[] = { sizeof(names), 1, sizeof(Elf_Sym), sizeof(info), 8, 8, 8 };
    unsigned long offset = 1024;
    unsigned long name_offset = 1;
    for (int index = 1; index < header->e_shnum; index++) {
        Elf_Shdr *section = &sections[index];
        section->sh_name = name_offset;
        name_offset += strlen(names + name_offset) + 1;
        section->sh_type = index <= 2 ? SHT_STRTAB : index == 3 ? SHT_SYMTAB : SHT_PROGBITS;
        section->sh_flags = index >= 4 ? SHF_ALLOC : 0;
        section->sh_offset = offset;
        section->sh_size = sizes[index - 1];
        section->sh_addralign = 8;
        if (index == 3) {
            section->sh_link = 2;
            section->sh_entsize = sizeof(Elf_Sym);
        }
        if (index == 5) {
            mod_initcall_t callback = test_init;
            memcpy(bytes + offset, &callback, 8);
        } else if (index == 6) {
            mod_exitcall_t callback = test_exit;
            memcpy(bytes + offset, &callback, 8);
        } else if (index == 7) {
            mod_ctl0call_t callback = test_control;
            memcpy(bytes + offset, &callback, 8);
        } else {
            memcpy(bytes + offset, data[index - 1], sizes[index - 1]);
        }
        offset += (sizes[index - 1] + 7) & ~7ul;
    }
    *length = offset;
    return bytes;
}

static void bad_elf_tests(void)
{
    int length;
    void *bytes = fixture(0, &length);
    assert(load_module(bytes, 10, "", "test", 0) == -ENOEXEC);
    Elf_Ehdr *header = bytes;
    header->e_shstrndx = 500;
    assert(load_module(bytes, length, "", "test", 0) == -ENOEXEC);
    header->e_shstrndx = 1;
    Elf_Shdr *sections = bytes + header->e_shoff;
    sections[4].sh_offset = ~0ul;
    assert(load_module(bytes, length, "", "test", 0) == -ENOEXEC);
    assert(get_module_nums() == 0 && init_calls == 0 && exec_allocations == 0);
    free(bytes);
}

static void callback_text_boundary_test(void)
{
    struct module mod = { .start = (void *)0x10000ul, .text_size = 0x1000, .size = 0x3000 };
    struct {
        unsigned long target;
        int expected;
    } cases[] = {
        { 0, 0 }, { 0xfffc, 0 }, { 0xffff, 0 },
        { 0x10000, 1 }, { 0x10001, 0 }, { 0x10002, 0 }, { 0x10003, 0 },
        { 0x10004, 1 }, { 0x10080, 1 }, { 0x10ffc, 1 },
        { 0x10ffd, 0 }, { 0x10ffe, 0 }, { 0x10fff, 0 },
        { 0x11000, 0 }, { 0x11004, 0 }, { 0x12000, 0 }, { ~0ul, 0 }
    };
    for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); i++)
        assert(module_callback_in_text(&mod, cases[i].target) == cases[i].expected);
    mod.text_size = 0;
    assert(!module_callback_in_text(&mod, 0x10000));
    mod.text_size = 4;
    assert(module_callback_in_text(&mod, 0x10000));
    assert(!module_callback_in_text(&mod, 0x10004));
    mod.start = (void *)(~0ul - 0xfff);
    mod.text_size = 0x1000;
    assert(module_callback_in_text(&mod, ~0ul - 3));
    assert(!module_callback_in_text(&mod, 4));
}

static void callback_adapter_rejection_test(void)
{
    struct {
        mod_initcall_t init;
        mod_exitcall_t exit;
        mod_ctl0call_t ctl0;
        mod_ctl1call_t ctl1;
    } slots = { test_init, test_exit, test_control, test_control1 };
    struct module mod = {
        .start = &slots, .size = sizeof(slots),
        .init = &slots.init, .exit = &slots.exit,
        .ctl0 = &slots.ctl0, .ctl1 = &slots.ctl1
    };
    assert(module_callbacks_valid(&mod));
    slots.init = NULL;
    assert(!module_callbacks_valid(&mod));
    slots.init = test_init;
    slots.exit = NULL;
    assert(!module_callbacks_valid(&mod));
    slots.exit = test_exit;
    slots.ctl0 = (mod_ctl0call_t)(uintptr_t)0x1000;
    assert(!module_callbacks_valid(&mod));
    slots.ctl0 = test_control;
    slots.ctl1 = (mod_ctl1call_t)(uintptr_t)0x2000;
    assert(!module_callbacks_valid(&mod));
    slots.ctl1 = test_control1;
    mod.init = (mod_initcall_t *)((char *)&slots + 1);
    assert(!module_callbacks_valid(&mod));
    mod.init = &slots.init;
    mod.exit = (mod_exitcall_t *)((char *)&slots + sizeof(slots));
    assert(!module_callbacks_valid(&mod));
    mod.exit = &slots.exit;
    slots.ctl0 = NULL;
    slots.ctl1 = NULL;
    assert(module_callbacks_valid(&mod));
    mod.ctl0 = NULL;
    mod.ctl1 = NULL;
    assert(module_callbacks_valid(&mod));

    int length;
    void *bytes = fixture(1, &length);
    Elf_Ehdr *header = bytes;
    Elf_Shdr *sections = bytes + header->e_shoff;
    mod_initcall_t unknown = (mod_initcall_t)(uintptr_t)0x3000;
    memcpy(bytes + sections[5].sh_offset, &unknown, sizeof(unknown));
    assert(load_module(bytes, length, "", "test", 0) == -ENOEXEC);
    assert(init_calls == 0 && exit_calls == 0 && cache_sync_calls == 0);
    assert(exec_allocations == 1 && exec_frees == 1 && get_module_nums() == 0);
    free(bytes);
}

static void cache_covers_whole_allocation_test(void)
{
    int length;
    void *bytes = fixture(0, &length);
    assert(load_module(bytes, length, "", "test", 0) == 0);
    free(bytes);
    struct module *mod = find_module_locked("host-fixture");
    assert(mod);
    assert(cache_sync_calls == 1 && init_calls == 1);
    assert(cache_sync_start == (unsigned long)mod->start);
    assert(cache_sync_end == (unsigned long)mod->start + mod->size);
    assert(mod->size > mod->text_size);
}

static void input_snapshot_ownership_test(void)
{
    int length;
    void *snapshot = fixture(1, &length);
    char args[] = "'single' \"double\" ; $(NOT_EXECUTED)\nline2";
    const char *expected = "'single' \"double\" ; $(NOT_EXECUTED)\nline2";
    assert(load_module(snapshot, length, args, "load-file", NULL) == 0);
    struct module *mod = find_module_locked("host-fixture");
    assert(mod && mod->args && mod->args != args);
    assert(!strcmp(mod->args, expected));
    memset(snapshot, 0xa5, (size_t)length);
    free(snapshot);
    memset(args, 0x5a, sizeof(args));
    assert(!strcmp(mod->args, expected));
    char output[4096];
    assert(get_module_info("host-fixture", output, sizeof(output)) > 0);
    assert(strstr(output, "description=Loader test\n"));
    assert(module_control0("host-fixture", "still-live", output, sizeof(output)) == 0);
    assert(!strcmp(output, "still-live"));
    assert(unload_module("host-fixture", NULL) == 0);
    assert(init_calls == 1 && exit_calls == 1 && exec_frees == 1);
    assert(get_module_nums() == 0);
}

static void failed_init_snapshot_ownership_test(void)
{
    int length;
    void *snapshot = fixture(1, &length);
    char args[] = "retained-on-init-error";
    init_result = -EINVAL;
    exit_result = -EIO;
    assert(load_module(snapshot, length, args, "load-file", NULL) == -EINVAL);
    memset(snapshot, 0xa5, (size_t)length);
    free(snapshot);
    memset(args, 0x5a, sizeof(args));
    struct module *mod = find_module_locked("host-fixture");
    assert(mod && mod->args && !strcmp(mod->args, "retained-on-init-error"));
    char output[4096];
    assert(get_module_info("host-fixture", output, sizeof(output)) > 0);
    assert(strstr(output, "state=failed\n") && strstr(output, "pending_unload=1\n"));
    assert(strstr(output, "last_error=-5\n"));
    assert(init_calls == 1 && exit_calls == 1 && exec_frees == 0);
    assert(get_module_nums() == 1 && cache_sync_calls == 1);
}

static void no_control_and_resident_exit_test(void)
{
    int length;
    void *bytes = fixture(0, &length);
    assert(load_module(bytes, length, "line1\nname=spoof", "test", 0) == 0);
    free(bytes);
    char output[4096];
    assert(module_control0("host-fixture", "test", output, sizeof(output)) == -ENOSYS);
    assert(get_module_info("missing", output, sizeof(output)) == -ENOENT);
    assert(get_module_nums() == 1);
    assert(get_module_info("host-fixture", output, sizeof(output)) > 0);
    assert(strstr(output, "has_control=0\n"));
    assert(strstr(output, "args_hex=6c696e65310a6e616d653d73706f6f66\n"));
    assert(unload_module("host-fixture", 0) == 0);
    assert(unload_module("host-fixture", 0) == -ENOENT);
    assert(exit_calls == 1 && exec_frees == 1 && get_module_nums() == 0);
    assert(get_module_info("host-fixture", output, sizeof(output)) == -ENOENT);
    bytes = fixture(0, &length);
    assert(load_module(bytes, length, "", "test", 0) == 0);
    free(bytes);
    assert(unload_module("host-fixture", 0) == 0);
    assert(exec_allocations == 2 && exec_frees == 2);
}

static void exit_failure_never_frees_test(void)
{
    int length;
    void *bytes = fixture(1, &length);
    assert(load_module(bytes, length, "", "test", 0) == 0);
    free(bytes);
    char output[4096];
    assert(module_control0("host-fixture", "$(literal)", output, sizeof(output)) == 0);
    assert(!strcmp(output, "$(literal)") && control_calls == 1);
    exit_result = -EIO;
    assert(unload_module("host-fixture", 0) == -EIO);
    assert(exec_frees == 0 && exit_calls == 1 && get_module_nums() == 1);
    assert(module_control0("host-fixture", "again", output, sizeof(output)) == -EBUSY);
    assert(get_module_info("host-fixture", output, sizeof(output)) > 0);
    assert(strstr(output, "last_error=-5\n"));
}

static void failed_init_and_bounded_output_test(void)
{
    int length;
    void *bytes = fixture(1, &length);
    init_result = -EPERM;
    exit_result = -EIO;
    assert(load_module(bytes, length, "", "test", 0) == -EPERM);
    free(bytes);
    assert(exec_frees == 0 && exit_calls == 1 && get_module_nums() == 1);
    char output[4096];
    assert(get_module_info("host-fixture", output, sizeof(output)) > 0);
    assert(strstr(output, "state=failed\n") && strstr(output, "pending_unload=1\n"));
    assert(get_module_info("host-fixture", output, 8) == -ENOBUFS);
    char tiny[8] = "1234567";
    assert(list_modules(tiny, 3) == -ENOBUFS);
    assert(tiny[3] == '4');
    assert(get_module_nums() == 1);
}

static void *control_thread(void *unused)
{
    char output[64];
    assert(module_control0("host-fixture", "blocking", output, sizeof(output)) == 0);
    return 0;
}

static void concurrent_control_unload_test(void)
{
    int length;
    void *bytes = fixture(1, &length);
    assert(load_module(bytes, length, "", "test", 0) == 0);
    free(bytes);
    pthread_t worker;
    block_control = 1;
    assert(!pthread_create(&worker, 0, control_thread, 0));
    pthread_mutex_lock(&test_mutex);
    while (!control_entered) pthread_cond_wait(&test_condition, &test_mutex);
    assert(unload_module("host-fixture", 0) == -EBUSY);
    assert(get_module_nums() == -EBUSY);
    assert(exit_calls == 0 && exec_frees == 0);
    release_control = 1;
    pthread_cond_broadcast(&test_condition);
    pthread_mutex_unlock(&test_mutex);
    assert(!pthread_join(worker, 0));
    assert(unload_module("host-fixture", 0) == 0);
    assert(exit_calls == 1 && exec_frees == 1 && get_module_nums() == 0);
}

static void failed_init_cleanup_test(void)
{
    int length;
    void *bytes = fixture(1, &length);
    init_result = -EINVAL;
    assert(load_module(bytes, length, "", "test", 0) == -EINVAL);
    assert(init_calls == 1 && exit_calls == 1 && exec_frees == 1);
    assert(get_module_nums() == 0);
    init_result = 0;
    free(bytes);
    bytes = fixture(1, &length);
    assert(load_module(bytes, length, "", "test", 0) == 0);
    free(bytes);
    assert(unload_module("host-fixture", 0) == 0);
    assert(exec_allocations == 2 && exec_frees == 2);
}

int main(void)
{
    module_init();
    bad_elf_tests();
    reboot_loader();
    no_control_and_resident_exit_test();
    reboot_loader();
    exit_failure_never_frees_test();
    reboot_loader();
    failed_init_and_bounded_output_test();
    reboot_loader();
    concurrent_control_unload_test();
    reboot_loader();
    callback_text_boundary_test();
    reboot_loader();
    callback_adapter_rejection_test();
    reboot_loader();
    cache_covers_whole_allocation_test();
    reboot_loader();
    input_snapshot_ownership_test();
    reboot_loader();
    failed_init_snapshot_ownership_test();
    reboot_loader();
    failed_init_cleanup_test();
    reboot_loader();
    puts("KPM actual-loader regression tests: PASS (11 groups; 22 text-boundary cases)");
    return 0;
}
