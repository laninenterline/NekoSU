// Minimal KernelSU-style module loader for the QEMU debug VM.
// Binds undefined ELF symbols to /proc/kallsyms addresses (SHN_ABS), then
// calls init_module() - the same trick ksud's ksuinit uses in late-load mode.
#define _GNU_SOURCE
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

struct sym { char name[128]; unsigned long addr; };
static struct sym *syms;
static size_t nsyms, capsyms;

static void load_kallsyms(void) {
    FILE *f = fopen("/proc/kallsyms", "r");
    if (!f) { perror("kallsyms"); exit(2); }
    char line[512];
    while (fgets(line, sizeof line, f)) {
        unsigned long addr; char type; char name[256];
        if (sscanf(line, "%lx %c %255s", &addr, &type, name) != 3) continue;
        if (!addr || name[0] == '\0') continue;
        if (nsyms == capsyms) {
            capsyms = capsyms ? capsyms * 2 : 65536;
            syms = realloc(syms, capsyms * sizeof(*syms));
            if (!syms) exit(2);
        }
        snprintf(syms[nsyms].name, sizeof syms[nsyms].name, "%s", name);
        syms[nsyms].addr = addr;
        nsyms++;
    }
    fclose(f);
    fprintf(stderr, "kallsyms: %zu symbols\n", nsyms);
}

static unsigned long lookup(const char *name) {
    for (size_t i = 0; i < nsyms; i++)
        if (!strcmp(syms[i].name, name)) return syms[i].addr;
    return 0;
}

static unsigned char *read_file(const char *path, size_t *out_len) {
    FILE *f = fopen(path, "rb");
    if (!f) { perror(path); return NULL; }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    unsigned char *buf = malloc(n);
    if (!buf || fread(buf, 1, n, f) != (size_t)n) { fclose(f); return NULL; }
    fclose(f); *out_len = n; return buf;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: %s ko [params]\n", argv[0]); return 2; }
    size_t len; unsigned char *buf = read_file(argv[1], &len);
    if (!buf) return 2;
    Elf64_Ehdr *eh = (Elf64_Ehdr *)buf;
    if (memcmp(eh->e_ident, ELFMAG, SELFMAG) || eh->e_ident[EI_CLASS] != ELFCLASS64) {
        fprintf(stderr, "not ELF64\n"); return 2;
    }
    load_kallsyms();
    Elf64_Shdr *sh = (Elf64_Shdr *)(buf + eh->e_shoff);
    Elf64_Shdr *symtab = NULL, *strtab = NULL;
    for (int i = 0; i < eh->e_shnum; i++)
        if (sh[i].sh_type == SHT_SYMTAB) { symtab = &sh[i]; break; }
    if (!symtab) { fprintf(stderr, "no symtab\n"); return 2; }
    strtab = &sh[symtab->sh_link];
    Elf64_Sym *s = (Elf64_Sym *)(buf + symtab->sh_offset);
    size_t count = symtab->sh_size / sizeof(Elf64_Sym);
    const char *strs = (const char *)(buf + strtab->sh_offset);
    int bound = 0, missing = 0;
    for (size_t i = 0; i < count; i++) {
        if (s[i].st_shndx != SHN_UNDEF || s[i].st_name == 0) continue;
        const char *name = strs + s[i].st_name;
        unsigned long addr = lookup(name);
        if (!addr) {
            fprintf(stderr, "MISSING %s\n", name);
            missing++;
            continue;
        }
        s[i].st_value = addr;
        s[i].st_shndx = SHN_ABS;
        bound++;
    }
    fprintf(stderr, "bound=%d missing=%d\n", bound, missing);
    const char *params = argc > 2 ? argv[2] : "";
    long rc = syscall(SYS_init_module, buf, len, params);
    fprintf(stderr, "init_module rc=%ld errno=%d (%s)\n", rc, errno, strerror(errno));
    return rc == 0 ? 0 : 1;
}
