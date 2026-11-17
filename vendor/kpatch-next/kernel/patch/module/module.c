/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 */

#ifdef KP_LOADER_HOST_TEST
#include "../../../tests/loader_host_stubs.h"
#else
#include <uapi/asm-generic/errno.h>
#include <pgtable.h>
#include <kpmalloc.h>
#include <linux/err.h>
#include <linux/string.h>
#include <symbol.h>
#include <kallsyms.h>
#include <cache.h>
#include <common.h>
#include <linux/fs.h>
#include <uapi/linux/fs.h>
#include <hotpatch.h>
#include <linux/list.h>
#include <linux/kernel.h>
#include <linux/spinlock.h>
#include <linux/slab.h>
#include <linux/vmalloc.h>
#include <linux/rcupdate.h>
#include <linux/rculist.h>
#include <baselib.h>

#include "module.h"
#include "relo.h"

/* Parse untrusted ELF bytes without optional kernel string symbol bindings. */
#define memchr lib_memchr
#define memcmp lib_memcmp
#define memcpy lib_memcpy
#define memset lib_memset
#define strcpy lib_strcpy
#define strcmp lib_strcmp
#define strncmp lib_strncmp
#define strlen lib_strlen
#define strchr lib_strchr
#include <kpm_trace.h>
#endif

#define KPM_MAX_IMAGE (32ul << 20)
#define KPM_MAX_ALLOCATION (64ul << 20)
#define KPM_MAX_MODULES 64
#define KPM_MAX_CONTROLS 128

struct control_argument
{
    struct control_argument *next;
    char text[];
};

#define ALIGN_MASK(x, mask) (((x) + (mask)) & ~(mask))
#define ALIGN(x, a) ALIGN_MASK(x, (typeof(x))(a)-1)

#define align(X) ALIGN(X, page_size)

#define elf_check_arch(x) ((x)->e_machine == EM_AARCH64)

#define ARCH_SHF_SMALL 0

/* Update size with this section: return offset. */
static long get_offset(struct module *mod, unsigned int *size, Elf_Shdr *sechdr, unsigned int section)
{
    long ret = ALIGN(*size, sechdr->sh_addralign ?: 1);
    *size = ret + sechdr->sh_size;
    return ret;
}

static char *get_next_modinfo(const struct load_info *info, const char *tag, char *prev)
{
    unsigned int taglen = strlen(tag);
    Elf_Shdr *infosec = &info->sechdrs[info->index.info];
    unsigned long size = infosec->sh_size;
    char *modinfo = (char *)info->hdr + infosec->sh_offset;
    if (prev) {
        unsigned long used = prev - modinfo;
        if (used >= size) return 0;
        char *end = memchr(prev, 0, size - used);
        if (!end) return 0;
        used = end - modinfo + 1;
        modinfo += used;
        size -= used;
    }
    while (size) {
        char *end = memchr(modinfo, 0, size);
        if (!end) return 0;
        unsigned long length = end - modinfo;
        if (length > taglen && !memcmp(modinfo, tag, taglen) && modinfo[taglen] == '=')
            return modinfo + taglen + 1;
        length++;
        modinfo += length;
        size -= length;
    }
    return 0;
}

static char *get_modinfo(const struct load_info *info, const char *tag)
{
    return get_next_modinfo(info, tag, 0);
}

static int valid_info_text(const char *text)
{
    if (!text) return 1;
    for (const unsigned char *p = (const unsigned char *)text; *p; p++)
        if (*p < 32 || *p == 127) return 0;
    return 1;
}

static int find_sec(const struct load_info *info, const char *name)
{
    for (int i = 1; i < info->hdr->e_shnum; i++) {
        Elf_Shdr *shdr = &info->sechdrs[i];
        if ((shdr->sh_flags & SHF_ALLOC) && strcmp(info->secstrings + shdr->sh_name, name) == 0) return i;
    }
    return 0;
}

static void *get_sh_base(struct load_info *info, const char *secname)
{
    int idx = find_sec(info, secname);
    if (!idx) return 0;
    Elf_Shdr *infosec = &info->sechdrs[idx];
    void *addr = (void *)info->hdr + infosec->sh_offset;
    return addr;
}

static unsigned long get_sh_size(struct load_info *info, const char *secname)
{
    int idx = find_sec(info, secname);
    if (!idx) return 0;
    Elf_Shdr *infosec = &info->sechdrs[idx];
    return infosec->sh_size;
}

static void layout_sections(struct module *mod, struct load_info *info)
{
    static unsigned long const masks[][2] = {
        /* NOTE: all executable code must be the first section in this array; otherwise modify the text_size finder in the two loops below */
        { SHF_EXECINSTR | SHF_ALLOC, ARCH_SHF_SMALL },
        { SHF_ALLOC, SHF_WRITE | ARCH_SHF_SMALL },
        { SHF_WRITE | SHF_ALLOC, ARCH_SHF_SMALL },
        { ARCH_SHF_SMALL | SHF_ALLOC, 0 }
    };

    for (int i = 0; i < info->hdr->e_shnum; i++)
        info->sechdrs[i].sh_entsize = ~0UL;

    // todo: tslf alloc all rwx and not page aligned
    for (int m = 0; m < sizeof(masks) / sizeof(masks[0]); ++m) {
        for (int i = 0; i < info->hdr->e_shnum; ++i) {
            Elf_Shdr *s = &info->sechdrs[i];
            if ((s->sh_flags & masks[m][0]) != masks[m][0] || (s->sh_flags & masks[m][1]) || s->sh_entsize != ~0UL)
                continue;
            s->sh_entsize = get_offset(mod, &mod->size, s, i);
            // const char *sname = info->secstrings + s->sh_name;
        }
        switch (m) {
        case 0: /* executable */
            mod->size = align(mod->size);
            mod->text_size = mod->size;
            break;
        case 1: /* RO: text and ro-data */
            mod->size = align(mod->size);
            mod->ro_size = mod->size;
            break;
        case 2:
            break;
        case 3: /* whole */
            mod->size = align(mod->size);
            break;
        }
    }
}

static bool is_core_symbol(const Elf_Sym *src, const Elf_Shdr *sechdrs, unsigned int shnum)
{
    const Elf_Shdr *sec;
    if (src->st_shndx == SHN_UNDEF || src->st_shndx >= shnum || !src->st_name) return false;
    sec = sechdrs + src->st_shndx;
    if (!(sec->sh_flags & SHF_ALLOC) || !(sec->sh_flags & SHF_EXECINSTR)) return false;
    return true;
}

/* Change all symbols so that st_value encodes the pointer directly. */
static int simplify_symbols(struct module *mod, const struct load_info *info)
{
    Elf_Shdr *symsec = &info->sechdrs[info->index.sym];
    Elf_Sym *sym = (void *)symsec->sh_addr;
    unsigned long secbase;
    unsigned int i;
    int ret = 0;

    for (i = 1; i < symsec->sh_size / sizeof(Elf_Sym); i++) {
        const char *name = info->strtab + sym[i].st_name;
        switch (sym[i].st_shndx) {
        case SHN_COMMON:
            ret = -ENOEXEC;
            break;
        case SHN_ABS:
            break;
        case SHN_UNDEF:
            unsigned long addr = symbol_lookup_name(name);
            // kernel symbol cause overflow in relocation
            // if (!addr) addr = kallsyms_lookup_name(name);
            if (!addr) {
                logke("unknown symbol: %s\n", name);
                ret = -ENOENT;
                break;
            }
            sym[i].st_value = addr;
            break;
        default:
            secbase = info->sechdrs[sym[i].st_shndx].sh_addr;
            sym[i].st_value += secbase;
            break;
        }
    }
    return ret;
}

static int apply_relocations(struct module *mod, const struct load_info *info)
{
    int rc = 0;
    unsigned int i;
    for (i = 1; i < info->hdr->e_shnum; i++) {
        unsigned int infosec = info->sechdrs[i].sh_info;
        if (infosec >= info->hdr->e_shnum) continue;
        if (!(info->sechdrs[infosec].sh_flags & SHF_ALLOC)) continue;
        if (info->sechdrs[i].sh_type == SHT_REL) {
            rc = apply_relocate(info->sechdrs, info->strtab, info->index.sym, i, mod);
        } else if (info->sechdrs[i].sh_type == SHT_RELA) {
            rc = apply_relocate_add(info->sechdrs, info->strtab, info->index.sym, i, mod);
        }
        if (rc < 0) break;
    }
    return rc;
}

// todo: free .strtab and .symtab after relocation
static void layout_symtab(struct module *mod, struct load_info *info)
{
    Elf_Shdr *symsect = info->sechdrs + info->index.sym;
    Elf_Shdr *strsect = info->sechdrs + info->index.str;
    const Elf_Sym *src;
    unsigned int i, nsrc, ndst, strtab_size = 0;

    /* Put symbol section at end of module. */
    symsect->sh_flags |= SHF_ALLOC;
    symsect->sh_entsize = get_offset(mod, &mod->size, symsect, info->index.sym);

    src = (void *)info->hdr + symsect->sh_offset;
    nsrc = symsect->sh_size / sizeof(*src);

    /* strtab always starts with a nul, so offset 0 is the empty string. */
    strtab_size = 1;
    /* Compute total space required for the core symbols' strtab. */
    for (ndst = i = 0; i < nsrc; i++) {
        if (i == 0 || is_core_symbol(src + i, info->sechdrs, info->hdr->e_shnum)) {
            strtab_size += strlen(&info->strtab[src[i].st_name]) + 1;
            ndst++;
        }
    }

    /* Append room for core symbols at end. */
    info->symoffs = ALIGN(mod->size, symsect->sh_addralign ?: 1);
    info->stroffs = mod->size = info->symoffs + ndst * sizeof(Elf_Sym);
    mod->size += strtab_size;

    /* Put string table section at end of module. */
    strsect->sh_flags |= SHF_ALLOC;
    strsect->sh_entsize = get_offset(mod, &mod->size, strsect, info->index.str);
}

static int rewrite_section_headers(struct load_info *info)
{
    info->sechdrs[0].sh_addr = 0;
    for (int i = 1; i < info->hdr->e_shnum; i++) {
        Elf_Shdr *shdr = &info->sechdrs[i];
        if (shdr->sh_type != SHT_NOBITS && info->len < shdr->sh_offset + shdr->sh_size) {
            return -ENOEXEC;
        }
        /* Mark all sections sh_addr with their address in the temporary image. */
        shdr->sh_addr = (size_t)info->hdr + shdr->sh_offset;
    }
    return 0;
}

static int move_module(struct module *mod, struct load_info *info)
{
    // todo:
    logki("alloc module size: %llx\n", mod->size);
    mod->start = kp_malloc_exec(mod->size);
    if (!mod->start) {
        return -ENOMEM;
    }
    memset(mod->start, 0, mod->size);

    /* Transfer each section which specifies SHF_ALLOC */
    logkd("final section addresses:\n");

    for (int i = 1; i < info->hdr->e_shnum; i++) {
        void *dest;
        Elf_Shdr *shdr = &info->sechdrs[i];
        if (!(shdr->sh_flags & SHF_ALLOC)) continue;

        dest = mod->start + shdr->sh_entsize;
        const char *sname = info->secstrings + shdr->sh_name;

        logkd("    %s %llx %llx\n", sname, dest, shdr->sh_size);

        if (shdr->sh_type != SHT_NOBITS) memcpy(dest, (void *)shdr->sh_addr, shdr->sh_size);

        shdr->sh_addr = (unsigned long)dest;

        if (!mod->init && !strcmp(".kpm.init", sname)) mod->init = (mod_initcall_t *)dest;

        if (!strcmp(".kpm.ctl0", sname)) mod->ctl0 = (mod_ctl0call_t *)dest;
        if (!strcmp(".kpm.ctl1", sname)) mod->ctl1 = (mod_ctl1call_t *)dest;

        if (!mod->exit && !strcmp(".kpm.exit", sname)) mod->exit = (mod_exitcall_t *)dest;

        if (!mod->info.base && !strcmp(".kpm.info", sname)) mod->info.base = (const char *)dest;
    }
    mod->info.name = info->info.name - info->info.base + mod->info.base;
    mod->info.version = info->info.version - info->info.base + mod->info.base;

    if (info->info.license) mod->info.license = info->info.license - info->info.base + mod->info.base;
    if (info->info.author) mod->info.author = info->info.author - info->info.base + mod->info.base;
    if (info->info.description) mod->info.description = info->info.description - info->info.base + mod->info.base;

    return 0;
}

static int setup_load_info(struct load_info *info)
{
    int rc = 0;
    info->sechdrs = (void *)info->hdr + info->hdr->e_shoff;
    info->secstrings = (void *)info->hdr + info->sechdrs[info->hdr->e_shstrndx].sh_offset;

    if ((rc = rewrite_section_headers(info))) {
        logke("rewrite section error\n");
        return rc;
    }

    if (!find_sec(info, ".kpm.init") || !find_sec(info, ".kpm.exit")) {
        logke("no .kpm.init or .kpm.exit section\n");
        return -ENOEXEC;
    }
    /* The flat payload is moved without ELF data relocations. Keep names
     * inline so the compiler cannot emit unadjusted absolute pointers. */
    static const char callbacks[][10] = { ".kpm.init", ".kpm.exit", ".kpm.ctl0", ".kpm.ctl1" };
    for (int i = 0; i < sizeof(callbacks) / sizeof(callbacks[0]); i++) {
        int index = find_sec(info, callbacks[i]);
        if (index && (info->sechdrs[index].sh_size != sizeof(void *) ||
                      info->sechdrs[index].sh_type != SHT_PROGBITS))
            return -ENOEXEC;
    }

    info->index.info = find_sec(info, ".kpm.info");
    if (!info->index.info) {
        logke("no .kpm.info section\n");
        return -ENOEXEC;
    }
    info->info.base = get_sh_base(info, ".kpm.info");
    info->info.size = get_sh_size(info, ".kpm.info");

    const char *name = get_modinfo(info, "name");
    if (!name) {
        logke("module name not found\n");
        return -ENOEXEC;
    }
    info->info.name = name;
    if (!name[0] || strlen(name) >= KPM_NAME_LEN || strchr(name, '\n') || strchr(name, '\r'))
        return -ENOEXEC;
    logkd("loading module: \n");
    logkd("    name: %s\n", name);

    const char *version = get_modinfo(info, "version");
    if (!version) {
        logkd("module version not found\n");
        return -ENOEXEC;
    }
    info->info.version = version;
    if (!version[0] || strlen(version) >= KPM_VERSION_LEN) return -ENOEXEC;
    logkd("    version: %s\n", version);

    const char *license = get_modinfo(info, "license");
    info->info.license = license;
    if (license && strlen(license) >= KPM_LICENSE_LEN) return -ENOEXEC;
    logkd("    license: %s\n", license);

    const char *author = get_modinfo(info, "author");
    info->info.author = author;
    if (author && strlen(author) >= KPM_AUTHOR_LEN) return -ENOEXEC;
    logkd("    author: %s\n", author);
    const char *description = get_modinfo(info, "description");
    info->info.description = description;
    if (description && strlen(description) >= KPM_DESCRIPTION_LEN) return -ENOEXEC;
    if (!valid_info_text(name) || !valid_info_text(version) || !valid_info_text(license) ||
        !valid_info_text(author) || !valid_info_text(description)) return -ENOEXEC;
    logkd("    description: %s\n", description);

    for (int i = 1; i < info->hdr->e_shnum; i++) {
        if (info->sechdrs[i].sh_type == SHT_SYMTAB) {
            info->index.sym = i;
            info->index.str = info->sechdrs[i].sh_link;
            info->strtab = (char *)info->hdr + info->sechdrs[info->index.str].sh_offset;
            break;
        }
    }

    if (info->index.sym == 0) {
        logkd("module has no symbols (stripped?)\n");
        return -ENOEXEC;
    }
    return 0;
}

static int valid_section_range(struct load_info *info, const Elf_Shdr *section)
{
    return section->sh_offset <= info->len && section->sh_size <= info->len - section->sh_offset;
}

static int valid_table_string(const void *table, unsigned long size, unsigned long offset)
{
    return offset < size && memchr((const char *)table + offset, 0, size - offset) != 0;
}

static int elf_header_check(struct load_info *info)
{
    if (info->len < sizeof(*(info->hdr)) || info->len > KPM_MAX_IMAGE) return -ENOEXEC;
    if (memcmp(info->hdr->e_ident, ELFMAG, SELFMAG) || info->hdr->e_type != ET_REL || !elf_check_arch(info->hdr) ||
        info->hdr->e_ident[EI_CLASS] != ELFCLASS64 || info->hdr->e_ident[EI_DATA] != ELFDATA2LSB ||
        info->hdr->e_ident[EI_VERSION] != EV_CURRENT || info->hdr->e_version != EV_CURRENT ||
        info->hdr->e_ehsize != sizeof(Elf_Ehdr) || info->hdr->e_shentsize != sizeof(Elf_Shdr) ||
        !info->hdr->e_shnum || info->hdr->e_shnum > 4096 ||
        info->hdr->e_shstrndx >= info->hdr->e_shnum)
        return -ENOEXEC;
    if (info->hdr->e_shoff >= info->len || (info->hdr->e_shnum * sizeof(Elf_Shdr) > info->len - info->hdr->e_shoff))
        return -ENOEXEC;
    const Elf_Shdr *sections = (const void *)info->hdr + info->hdr->e_shoff;
    const Elf_Shdr *names = &sections[info->hdr->e_shstrndx];
    if (names->sh_type != SHT_STRTAB || !valid_section_range(info, names)) return -ENOEXEC;
    const char *strings = (const char *)info->hdr + names->sh_offset;
    unsigned long allocated = 0;
    int symindex = 0;
    for (int i = 1; i < info->hdr->e_shnum; i++) {
        const Elf_Shdr *section = &sections[i];
        if (section->sh_type != SHT_NOBITS && !valid_section_range(info, section)) return -ENOEXEC;
        if (!valid_table_string(strings, names->sh_size, section->sh_name)) return -ENOEXEC;
        if (section->sh_addralign > 65536 ||
            (section->sh_addralign && (section->sh_addralign & (section->sh_addralign - 1))))
            return -ENOEXEC;
        if (section->sh_flags & SHF_ALLOC) {
            if (section->sh_size > KPM_MAX_ALLOCATION ||
                allocated > KPM_MAX_ALLOCATION - section->sh_size) return -ENOEXEC;
            allocated += section->sh_size;
            if (allocated > KPM_MAX_ALLOCATION - 65536) return -ENOEXEC;
            allocated += 65536;
            const char *name = strings + section->sh_name;
            if (!strncmp(name, ".kpm.", 5)) {
                if (section->sh_type != SHT_PROGBITS) return -ENOEXEC;
                for (int j = 1; j < i; j++)
                    if ((sections[j].sh_flags & SHF_ALLOC) &&
                        !strcmp(name, strings + sections[j].sh_name)) return -ENOEXEC;
                if (!strcmp(name, ".kpm.info") &&
                    (!section->sh_size || ((const char *)info->hdr)[section->sh_offset + section->sh_size - 1]))
                    return -ENOEXEC;
            }
        }
        if (section->sh_type == SHT_REL) return -ENOEXEC;
        if (section->sh_type == SHT_SYMTAB) {
            if (symindex || section->sh_entsize != sizeof(Elf_Sym) ||
                section->sh_size % sizeof(Elf_Sym) || section->sh_size < sizeof(Elf_Sym) ||
                section->sh_link >= info->hdr->e_shnum) return -ENOEXEC;
            symindex = i;
        }
    }
    if (!symindex) return -ENOEXEC;
    const Elf_Shdr *symbols = &sections[symindex];
    const Elf_Shdr *symbol_names = &sections[symbols->sh_link];
    if (symbol_names->sh_type != SHT_STRTAB || !valid_section_range(info, symbol_names)) return -ENOEXEC;
    const Elf_Sym *syms = (const void *)info->hdr + symbols->sh_offset;
    unsigned long symbol_count = symbols->sh_size / sizeof(Elf_Sym);
    for (unsigned long i = 0; i < symbol_count; i++) {
        const Elf_Sym *symbol = &syms[i];
        if (!valid_table_string((const char *)info->hdr + symbol_names->sh_offset,
                                symbol_names->sh_size, symbol->st_name)) return -ENOEXEC;
        if (symbol->st_shndx == SHN_ABS || symbol->st_shndx == SHN_UNDEF) continue;
        if (symbol->st_shndx >= info->hdr->e_shnum ||
            symbol->st_value > sections[symbol->st_shndx].sh_size ||
            symbol->st_size > sections[symbol->st_shndx].sh_size - symbol->st_value) return -ENOEXEC;
    }
    for (int i = 1; i < info->hdr->e_shnum; i++) {
        const Elf_Shdr *section = &sections[i];
        if (section->sh_type != SHT_RELA) continue;
        if (section->sh_entsize != sizeof(Elf64_Rela) || section->sh_size % sizeof(Elf64_Rela) ||
            section->sh_link != symindex || !section->sh_info ||
            section->sh_info >= info->hdr->e_shnum) return -ENOEXEC;
        const Elf64_Rela *relocs = (const void *)info->hdr + section->sh_offset;
        for (unsigned long j = 0; j < section->sh_size / sizeof(*relocs); j++) {
            unsigned long type = ELF64_R_TYPE(relocs[j].r_info);
            unsigned long width = 4;
            if (type == 0 || type == 256) width = 0;
            else if (type == 257 || type == 260) width = 8;
            else if (type == 259 || type == 262) width = 2;
            if (ELF64_R_SYM(relocs[j].r_info) >= symbol_count ||
                relocs[j].r_offset > sections[section->sh_info].sh_size ||
                width > sections[section->sh_info].sh_size - relocs[j].r_offset) return -ENOEXEC;
        }
    }
    return 0;
}

struct module modules = { 0 };
static unsigned int module_busy;

/* A non-waiting gate: callbacks may sleep or reenter; never hold an RCU read
 * lock or spinlock across them, and return EBUSY instead of deadlocking. */
static int module_enter(void)
{
    return !__atomic_exchange_n(&module_busy, 1, __ATOMIC_ACQUIRE);
}

static void module_leave(void)
{
    __atomic_store_n(&module_busy, 0, __ATOMIC_RELEASE);
}

static int module_callback_in_text(const struct module *mod, unsigned long callback)
{
    unsigned long start = (unsigned long)mod->start;
    return !(callback & 3ul) && callback >= start && callback - start < mod->text_size;
}

static int module_callbacks_valid(const struct module *mod)
{
#ifdef KP_LOADER_HOST_TEST
    /* Host fixtures use native callbacks rather than executing ARM64 bytes. */
    return loader_host_callbacks_valid(mod);
#else
    return module_callback_in_text(mod, (unsigned long)*mod->init) &&
           module_callback_in_text(mod, (unsigned long)*mod->exit) &&
           (!mod->ctl0 || !*mod->ctl0 || module_callback_in_text(mod, (unsigned long)*mod->ctl0)) &&
           (!mod->ctl1 || !*mod->ctl1 || module_callback_in_text(mod, (unsigned long)*mod->ctl1));
#endif
}

static struct module *find_module_locked(const char *name)
{
    struct module *pos;
    list_for_each_entry(pos, &modules.list, list)
        if (!strcmp(name, pos->info.name)) return pos;
    return 0;
}

static void release_module(struct module *mod)
{
    list_del(&mod->list);
    struct control_argument *argument = mod->ctl_history;
    while (argument) {
        struct control_argument *next = argument->next;
        kvfree(argument);
        argument = next;
    }
    if (mod->args) kvfree(mod->args);
    kp_free_exec(mod->start);
    kvfree(mod);
}

long load_module(const void *data, int len, const char *args, const char *event, void *__user reserved)
{
    if (!data || len < 0 || !event) return -EINVAL;
    if (!module_enter()) return -EBUSY;
    struct load_info load_info = { .len = len, .hdr = data };
    struct load_info *info = &load_info;
    long rc = 0;

    if ((rc = elf_header_check(info))) goto out;
    logki("KPM_LOAD checkpoint=elf-validated\n");
    if ((rc = setup_load_info(info))) goto out;
    logki("KPM_LOAD checkpoint=metadata-validated\n");

    if (find_module_locked(info->info.name)) {
        logkfd("%s exist\n", info->info.name);
        rc = -EEXIST;
        goto out;
    }
    int count = 0;
    struct module *existing;
    list_for_each_entry(existing, &modules.list, list) count++;
    if (count >= KPM_MAX_MODULES) {
        rc = -ENOSPC;
        goto out;
    }

    logki("KPM_LOAD checkpoint=bookkeeping-alloc-start\n");
    struct module *mod = (struct module *)vmalloc(sizeof(struct module));
    if (!mod) {
        rc = -ENOMEM;
        goto out;
    }
    memset(mod, 0, sizeof(struct module));
    logki("KPM_LOAD checkpoint=bookkeeping-allocated\n");

    if (args) {
        mod->args = vmalloc(strlen(args) + 1);
        if (!mod->args) {
            rc = -ENOMEM;
            goto free1;
        }
        strcpy(mod->args, args);
    }

    layout_sections(mod, info);
    layout_symtab(mod, info);
    if (mod->size > KPM_MAX_ALLOCATION) {
        rc = -E2BIG;
        goto free;
    }

    logki("KPM_LOAD checkpoint=exec-alloc-start\n");
    if ((rc = move_module(mod, info))) goto free;
    logki("KPM_LOAD checkpoint=sections-moved\n");
    if ((rc = simplify_symbols(mod, info))) goto free;
    logki("KPM_LOAD checkpoint=symbols-resolved\n");
    if ((rc = apply_relocations(mod, info))) goto free;
    logki("KPM_LOAD checkpoint=relocated\n");
    if (!mod->init || !*mod->init || !mod->exit || !*mod->exit) {
        rc = -ENOEXEC;
        goto free;
    }
    if (!module_callbacks_valid(mod)) {
        logke("KPM callback target is outside the relocated executable text\n");
        rc = -ENOEXEC;
        goto free;
    }

    /* Relocations wrote executable bytes through D-cache before first fetch. */
    flush_icache_range((unsigned long)mod->start, (unsigned long)mod->start + mod->size);
    logki("KPM_LOAD checkpoint=code-synchronized\n");

    list_add_tail(&mod->list, &modules.list);
    mod->init_called = 1;
    logki("KPM_LOAD checkpoint=pre-init\n");
    rc = (*mod->init)(mod->args, event, reserved);
    logki("KPM_LOAD checkpoint=init-returned rc=%ld\n", rc);
    mod->init_error = rc;
    mod->last_error = rc;

    if (!rc) {
        logkfi("[%s] succeed with [%s] \n", mod->info.name, args);
        goto out;
    } else {
        logkfi("[%s] failed with [%s] error: %d, try exit ...\n", mod->info.name, args, rc);
        mod->pending_unload = 1;
        mod->exit_called = 1;
        logki("KPM_LOAD checkpoint=failed-init-exit-start\n");
        mod->exit_error = (*mod->exit)(reserved);
        logki("KPM_LOAD checkpoint=failed-init-exit-returned rc=%ld\n", mod->exit_error);
        if (mod->exit_error) mod->last_error = mod->exit_error;
        if (!mod->exit_error) release_module(mod);
        goto out;
    }

free:
    if (mod->args) kvfree(mod->args);
    if (mod->start) kp_free_exec(mod->start);
free1:
    kvfree(mod);
out:
    logki("KPM_LOAD checkpoint=load-return rc=%ld\n", rc);
    module_leave();
    return rc;
}

long unload_module(const char *name, void *__user reserved)
{
    if (!name) return -EINVAL;
    if (!module_enter()) return -EBUSY;
    struct module *mod = find_module_locked(name);
    long rc = -ENOENT;
    if (mod) {
        mod->pending_unload = 1;
        if (!mod->exit_called) {
            mod->exit_called = 1;
            mod->exit_error = mod->exit && *mod->exit ? (*mod->exit)(reserved) : -ENOSYS;
        }
        mod->last_error = mod->exit_error ? mod->exit_error : mod->init_error;
        rc = mod->exit_error < 0 ? mod->exit_error : mod->exit_error ? -EIO : 0;
        /* Successful exit completes the module's hook/work teardown.
         * The gate excludes concurrent loader and control callbacks. */
        if (!rc) release_module(mod);
    }
    module_leave();
    return rc;
}

long load_module_path(const char *path, const char *args, void *__user reserved)
{
    long rc = 0;
    logki("KPM_LOAD checkpoint=path-start\n");
    if (!path) return -EINVAL;

    struct file *filp = filp_open(path, O_RDONLY, 0);
    if (unlikely(!filp || IS_ERR(filp))) {
        logkfe("open module: %s error\n", path);
        rc = PTR_ERR(filp);
        goto out;
    }
    logki("KPM_LOAD checkpoint=file-opened\n");
    loff_t len = vfs_llseek(filp, 0, SEEK_END);
    logkfd("module size: %llx\n", len);
    if (len <= 0 || len > KPM_MAX_IMAGE) {
        rc = -E2BIG;
        filp_close(filp, 0);
        goto out;
    }
    if (vfs_llseek(filp, 0, SEEK_SET) < 0) {
        rc = -EIO;
        filp_close(filp, 0);
        goto out;
    }

    logki("KPM_LOAD checkpoint=file-buffer-alloc-start\n");
    void *data = vmalloc(len);
    if (!data) {
        rc = -ENOMEM;
        filp_close(filp, 0);
        goto out;
    }
    logki("KPM_LOAD checkpoint=file-buffer-allocated\n");
    memset(data, 0, len);

    loff_t pos = 0;
    long read_len = kernel_read(filp, data, len, &pos);
    filp_close(filp, 0);
    logki("KPM_LOAD checkpoint=file-read-returned rc=%ld\n", read_len);

    if (read_len != len || pos != len) {
        logkfe("read module: %s error\n", path);
        rc = -EIO;
        goto free;
    }

    rc = load_module(data, len, args, "load-file", reserved);
free:
    kvfree(data);
out:
    logki("KPM_LOAD checkpoint=path-return rc=%ld\n", rc);
    return rc;
}

long module_control0(const char *name, const char *ctl_args, char *__user out_msg, int outlen)
{
    if (!name || !ctl_args || !out_msg || outlen <= 0 || outlen > 4096) return -EINVAL;
    int args_len = strlen(ctl_args);
    if (args_len > KPM_ARGS_LEN - 1) return -E2BIG;

    logkfi("name %s, args: %s\n", name, ctl_args);

    long rc = 0;
    if (!module_enter()) return -EBUSY;

    struct module *mod = find_module_locked(name);
    if (!mod) {
        rc = -ENOENT;
        goto out;
    }

    if (mod->pending_unload) {
        rc = -EBUSY;
        goto out;
    }
    if (!mod->ctl0 || !*mod->ctl0) {
        logkfe("no ctl0\n");
        rc = -ENOSYS;
        goto out;
    }

    if (mod->ctl_count >= KPM_MAX_CONTROLS) {
        rc = -ENOSPC;
        goto out;
    }
    struct control_argument *argument = vmalloc(sizeof(*argument) + args_len + 1);
    if (!argument) {
        rc = -ENOMEM;
        goto out;
    }

    strcpy(argument->text, ctl_args);
    argument->next = mod->ctl_history;
    mod->ctl_history = argument;
    mod->ctl_count++;
    /* A module may retain control strings until its exit callback completes. */
    mod->ctl_args = argument->text;

    rc = (*mod->ctl0)(mod->ctl_args, out_msg, outlen);

    logkfi("name: %s, rc: %d\n", name, rc);
out:
    module_leave();
    return rc;
}

long module_control1(const char *name, void *a1, void *a2, void *a3)
{
    if (!name) return -EINVAL;
    logkfi("name %s, a1: %llx, a2: %llx, a3: %llx\n", name, a1, a2, a3);
    long rc = 0;
    if (!module_enter()) return -EBUSY;

    struct module *mod = find_module_locked(name);
    if (!mod) {
        rc = -ENOENT;
        goto out;
    }

    if (mod->pending_unload) {
        rc = -EBUSY;
        goto out;
    }
    if (!mod->ctl1 || !*mod->ctl1) {
        logkfe("no ctl1\n");
        rc = -ENOSYS;
        goto out;
    }

    rc = (*mod->ctl1)(a1, a2, a3);

    logkfi("name: %s, rc: %d\n", name, rc);
out:
    module_leave();
    return rc;
}

int get_module_nums()
{
    if (!module_enter()) return -EBUSY;

    struct module *pos;
    int n = 0;
    list_for_each_entry(pos, &modules.list, list)
    {
        n++;
    }
    module_leave();

    logkfd("%d\n", n);
    return n;
}

int list_modules(char *out_names, int size)
{
    if (!out_names || size <= 0) return -EINVAL;
    if (!module_enter()) return -EBUSY;

    struct module *pos;
    int off = 0;
    out_names[0] = 0;
    list_for_each_entry(pos, &modules.list, list)
    {
        int written = snprintf(out_names + off, size - off, "%s\n", pos->info.name);
        if (written < 0 || written >= size - off) {
            module_leave();
            return -ENOBUFS;
        }
        off += written;
    }
    if (off > 0) out_names[off - 1] = '\0';

    module_leave();
    return off;
}

int get_module_info(const char *name, char *out_info, int size)
{
    if (!name || !out_info || size <= 0) return -EINVAL;
    if (!module_enter()) return -EBUSY;

    struct module *mod = find_module_locked(name);
    if (!mod) {
        module_leave();
        return -ENOENT;
    }

    int sz = snprintf(out_info, size - 1,
                      "name=%s\n"
                      "version=%s\n"
                      "license=%s\n"
                      "author=%s\n"
                      "description=%s\n"
                      "args=\n"
                      "state=%s\n"
                      "resident=1\n"
                      "pending_unload=%d\n"
                      "has_control=%d\n"
                      "last_error=%ld\n",
                      mod->info.name, mod->info.version, mod->info.license ?: "", mod->info.author ?: "",
                      mod->info.description ?: "",
                      mod->init_error ? "failed" : mod->pending_unload ? "pending-unload" : "loaded",
                      mod->pending_unload, !!(mod->ctl0 && *mod->ctl0), mod->last_error);

    if (sz < 0 || sz >= size - 1) {
        module_leave();
        return -ENOBUFS;
    }
    const char *args = mod->args ?: "";
    unsigned long args_len = strlen(args);
    if (args_len > KPM_ARGS_LEN - 1 || sz + 11 + args_len * 2 >= size) {
        module_leave();
        return -ENOBUFS;
    }
    memcpy(out_info + sz, "args_hex=", 9);
    sz += 9;
    const char *hex = "0123456789abcdef";
    for (unsigned long i = 0; i < args_len; i++) {
        unsigned char byte = args[i];
        out_info[sz++] = hex[byte >> 4];
        out_info[sz++] = hex[byte & 15];
    }
    out_info[sz++] = '\n';
    out_info[sz] = 0;
    logkfd("%s", out_info);

    module_leave();
    return sz;
}

void module_init()
{
    INIT_LIST_HEAD(&modules.list);
    module_busy = 0;
}
