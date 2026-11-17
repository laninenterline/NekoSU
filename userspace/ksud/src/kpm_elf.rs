//! Validation before untrusted module bytes can reach the kernel loader.

use std::collections::BTreeMap;

use anyhow::{Context, Result, ensure};
use goblin::elf::{Elf, header, reloc, section_header};

use super::failure;

pub(super) const FILE_LIMIT: usize = 32 * 1024 * 1024;

#[derive(Clone, Debug)]
pub(super) struct Metadata {
    pub name: String,
    pub version: String,
    pub author: String,
    pub description: String,
    pub has_control: bool,
}

fn invalid(message: impl Into<String>) -> anyhow::Error {
    failure("invalid_elf", message)
}

fn section_bytes<'a>(bytes: &'a [u8], section: &section_header::SectionHeader) -> Result<&'a [u8]> {
    let start = usize::try_from(section.sh_offset)?;
    let size = usize::try_from(section.sh_size)?;
    let end = start
        .checked_add(size)
        .ok_or_else(|| invalid("Section range overflows"))?;
    bytes
        .get(start..end)
        .ok_or_else(|| invalid("Section extends beyond EOF"))
}

fn relocation_width(kind: u32) -> Result<u64> {
    match kind {
        0 | 256 => Ok(0),
        reloc::R_AARCH64_ABS64 | reloc::R_AARCH64_PREL64 => Ok(8),
        reloc::R_AARCH64_ABS16 | reloc::R_AARCH64_PREL16 => Ok(2),
        // These are the RELA operations actually implemented by the vendored loader.
        258 | 261 | 263..=280 | 282..=293 | 299 => Ok(4),
        _ => Err(invalid(format!("Unsupported AArch64 relocation {kind}"))),
    }
}

pub(super) fn parse(bytes: &[u8]) -> Result<Metadata> {
    ensure!(
        bytes.len() >= 64 && bytes.len() <= FILE_LIMIT,
        invalid("KPM length is outside the supported range")
    );
    let elf = Elf::parse(bytes).map_err(|error| invalid(error.to_string()))?;
    ensure!(
        elf.is_64
            && elf.little_endian
            && elf.header.e_type == header::ET_REL
            && elf.header.e_machine == header::EM_AARCH64
            && elf.header.e_version == 1
            && elf.header.e_ehsize == 64
            && elf.header.e_shentsize == 64,
        invalid("Expected a little-endian AArch64 ELF64 ET_REL")
    );
    ensure!(
        !elf.section_headers.is_empty()
            && elf.section_headers.len() <= 4096
            && usize::from(elf.header.e_shstrndx) < elf.section_headers.len(),
        invalid("Invalid section-name table index")
    );
    let mut sections = BTreeMap::new();
    let mut allocated = 0_u64;
    let mut symtab = None;
    for (index, section) in elf.section_headers.iter().enumerate().skip(1) {
        let name = elf
            .shdr_strtab
            .get_at(section.sh_name)
            .ok_or_else(|| invalid("Invalid section name"))?;
        ensure!(
            section.sh_addralign <= 65536
                && (section.sh_addralign == 0 || section.sh_addralign.is_power_of_two()),
            invalid("Unsupported section alignment")
        );
        if section.sh_type != section_header::SHT_NOBITS {
            section_bytes(bytes, section)?;
        }
        if section.sh_flags & u64::from(section_header::SHF_ALLOC) != 0 {
            allocated = allocated
                .checked_add(section.sh_size)
                .and_then(|value| value.checked_add(65536))
                .ok_or_else(|| invalid("Allocated section size overflows"))?;
            ensure!(
                allocated <= 64 * 1024 * 1024,
                invalid("KPM allocation is too large")
            );
            ensure!(
                sections.insert(name, (index, section)).is_none(),
                invalid("Duplicate allocated section name")
            );
        }
        if section.sh_type == section_header::SHT_SYMTAB {
            ensure!(
                symtab.is_none(),
                invalid("Multiple symbol tables are unsupported")
            );
            ensure!(
                section.sh_entsize == 24 && section.sh_size % 24 == 0,
                invalid("Malformed symbol table")
            );
            let strings = elf
                .section_headers
                .get(section.sh_link as usize)
                .ok_or_else(|| invalid("Invalid symbol string-table link"))?;
            ensure!(
                strings.sh_type == section_header::SHT_STRTAB,
                invalid("Invalid symbol strings")
            );
            symtab = Some(index);
        }
        ensure!(
            section.sh_type != section_header::SHT_REL,
            invalid("REL relocations are unsupported")
        );
    }
    let symtab = symtab.ok_or_else(|| invalid("KPM has no symbol table"))?;
    ensure!(!elf.syms.is_empty(), invalid("KPM symbol table is empty"));
    for symbol in &elf.syms {
        elf.strtab
            .get_at(symbol.st_name)
            .ok_or_else(|| invalid("Invalid symbol name"))?;
        ensure!(
            symbol.st_shndx == section_header::SHN_UNDEF as usize
                || symbol.st_shndx == section_header::SHN_ABS as usize
                || symbol.st_shndx < elf.section_headers.len(),
            invalid("Invalid or unsupported symbol section")
        );
        if symbol.st_shndx > 0 && symbol.st_shndx < elf.section_headers.len() {
            let target = &elf.section_headers[symbol.st_shndx];
            ensure!(
                symbol.st_value <= target.sh_size
                    && symbol.st_size <= target.sh_size - symbol.st_value,
                invalid("Symbol extends beyond its section")
            );
        }
    }
    for section in &elf.section_headers {
        if section.sh_type == section_header::SHT_RELA {
            ensure!(
                section.sh_entsize == 24
                    && section.sh_size % 24 == 0
                    && section.sh_link as usize == symtab
                    && section.sh_info != 0
                    && (section.sh_info as usize) < elf.section_headers.len(),
                invalid("Invalid RELA table")
            );
        }
    }
    for (index, relocations) in &elf.shdr_relocs {
        let section = &elf.section_headers[*index];
        let target = &elf.section_headers[section.sh_info as usize];
        for relocation in relocations {
            let width = relocation_width(relocation.r_type)?;
            ensure!(
                relocation.r_sym < elf.syms.len()
                    && relocation
                        .r_offset
                        .checked_add(width)
                        .is_some_and(|end| end <= target.sh_size),
                invalid("Relocation extends beyond its target or symbol table")
            );
        }
    }
    for name in [".kpm.init", ".kpm.exit"] {
        let (index, section) = sections
            .get(name)
            .ok_or_else(|| invalid(format!("Missing {name}")))?;
        ensure!(
            section.sh_size == 8 && section.sh_type == section_header::SHT_PROGBITS,
            invalid(format!("Invalid {name} function-pointer section"))
        );
        let pointer = section_bytes(bytes, section)?;
        let has_relocation = elf.shdr_relocs.iter().any(|(rel_index, relocations)| {
            elf.section_headers[*rel_index].sh_info as usize == *index
                && relocations
                    .iter()
                    .any(|rel| rel.r_offset == 0 && rel.r_type == reloc::R_AARCH64_ABS64)
        });
        ensure!(
            pointer.iter().any(|byte| *byte != 0) || has_relocation,
            invalid(format!("Null {name} function pointer"))
        );
    }
    if let Some((_, section)) = sections.get(".kpm.ctl0") {
        ensure!(
            section.sh_size == 8 && section.sh_type == section_header::SHT_PROGBITS,
            invalid("Invalid control function-pointer section")
        );
    }
    let (_, info) = sections
        .get(".kpm.info")
        .ok_or_else(|| invalid("Missing .kpm.info"))?;
    let info = section_bytes(bytes, info)?;
    ensure!(
        info.last() == Some(&0),
        invalid("KPM metadata is not NUL terminated")
    );
    let mut fields = BTreeMap::new();
    for field in info
        .split(|byte| *byte == 0)
        .filter(|field| !field.is_empty())
    {
        let field = std::str::from_utf8(field).map_err(|_| invalid("Metadata is not UTF-8"))?;
        let (key, value) = field
            .split_once('=')
            .ok_or_else(|| invalid("Malformed metadata field"))?;
        ensure!(
            !value.chars().any(char::is_control),
            invalid("Control characters in metadata")
        );
        ensure!(
            fields.insert(key, value).is_none(),
            invalid("Duplicate metadata key")
        );
    }
    for (key, limit) in [
        ("name", 31),
        ("version", 11),
        ("author", 31),
        ("license", 31),
        ("description", 511),
    ] {
        if let Some(value) = fields.get(key) {
            ensure!(
                value.len() <= limit,
                invalid(format!("Metadata {key} exceeds {limit} bytes"))
            );
        }
    }
    let name = fields
        .get("name")
        .context(invalid("Missing KPM name"))?
        .to_string();
    let version = fields
        .get("version")
        .context(invalid("Missing KPM version"))?
        .to_string();
    ensure!(
        !name.is_empty() && !version.is_empty() && !name.contains(['/', '\\']),
        invalid("Invalid KPM name or version")
    );
    Ok(Metadata {
        name,
        version,
        author: fields.get("author").copied().unwrap_or_default().to_owned(),
        description: fields
            .get("description")
            .copied()
            .unwrap_or_default()
            .to_owned(),
        has_control: sections.contains_key(".kpm.ctl0"),
    })
}
