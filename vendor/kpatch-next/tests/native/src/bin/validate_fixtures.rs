//! TEST ONLY: validate built ARM64 fixtures with the production Goblin parser.

#[path = "../../../../../../userspace/ksud/src/kpm_elf.rs"]
mod elf;

use std::path::Path;

use anyhow::{Result, ensure};
use serde_json::json;

fn failure(code: &'static str, message: impl Into<String>) -> anyhow::Error {
    anyhow::anyhow!("{code}: {}", message.into())
}

fn main() -> Result<()> {
    let mut validated = Vec::new();
    for argument in std::env::args_os().skip(1) {
        let path = Path::new(&argument);
        let bytes = std::fs::read(path)?;
        let metadata = elf::parse(&bytes)?;
        let binary = goblin::elf::Elf::parse(&bytes)?;
        let mut imports = Vec::new();
        for symbol in &binary.syms {
            if symbol.st_shndx == 0 && symbol.st_name != 0 {
                let name = binary.strtab.get_at(symbol.st_name).unwrap_or_default();
                ensure!(
                    matches!(name, "printk" | "compat_copy_to_user"),
                    "Unexpected fixture import: {name}"
                );
                imports.push(name.to_owned());
            }
        }
        imports.sort();
        imports.dedup();
        let no_control = metadata.name == "milksu-test-no-ctl0";
        ensure!(
            imports.iter().any(|name| name == "printk"),
            "Missing printk marker import"
        );
        ensure!(
            metadata.has_control != no_control,
            "Incorrect ctl0 section in {}",
            metadata.name
        );
        ensure!(
            imports.iter().any(|name| name == "compat_copy_to_user") != no_control,
            "Incorrect usercopy imports"
        );
        validated.push(json!({
            "path": path,
            "name": metadata.name,
            "version": metadata.version,
            "author": metadata.author,
            "description": metadata.description,
            "has_control": metadata.has_control,
            "sha256": sha256::digest(&bytes),
            "imports": imports,
            "bytes": bytes.len(),
        }));
    }
    ensure!(validated.len() == 4, "Pass all four built fixture paths");
    println!(
        "{}",
        json!({ "test_only": true, "passed": 4, "fixtures": validated })
    );
    Ok(())
}
