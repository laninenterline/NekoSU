use std::fs;
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

use anyhow::{bail, Context, Result};

use crate::{android::ksucalls, defs};

const MAGIC: u32 = 0x4d53_4849;
const VERSION: u32 = 1;
const PACKAGE_SIZE: usize = 256;
const SHA256_STR_SIZE: usize = 65;
const RECORD_SIZE: usize = 4 + 4 + 4 + PACKAGE_SIZE + SHA256_STR_SIZE;
const MAX_CERTIFICATE_SIZE: u32 = 1024;

#[derive(Debug, Clone)]
pub struct ManagerIdentityRecord {
    pub package: String,
    pub certificate_size: u32,
    pub certificate_sha256: String,
}

fn valid_package(package: &str) -> bool {
    if package.is_empty() || package.len() >= PACKAGE_SIZE {
        return false;
    }

    let mut dot = false;
    let bytes = package.as_bytes();
    for (i, &c) in bytes.iter().enumerate() {
        match c {
            b'.' => {
                if i == 0 || bytes[i - 1] == b'.' || i + 1 == bytes.len() {
                    return false;
                }
                dot = true;
            }
            b'a'..=b'z' | b'0'..=b'9' | b'_' => {
                if (i == 0 || bytes[i - 1] == b'.') && !c.is_ascii_lowercase() {
                    return false;
                }
            }
            _ => return false,
        }
    }

    dot
}

fn valid_sha256(value: &str) -> bool {
    value.len() == 64 && value.bytes().all(|c| c.is_ascii_hexdigit() && !c.is_ascii_uppercase())
}

fn encode(record: &ManagerIdentityRecord) -> Vec<u8> {
    let mut bytes = vec![0u8; RECORD_SIZE];
    bytes[0..4].copy_from_slice(&MAGIC.to_le_bytes());
    bytes[4..8].copy_from_slice(&VERSION.to_le_bytes());
    bytes[8..12].copy_from_slice(&record.certificate_size.to_le_bytes());
    bytes[12..12 + record.package.len()].copy_from_slice(record.package.as_bytes());
    let sha_offset = 12 + PACKAGE_SIZE;
    bytes[sha_offset..sha_offset + record.certificate_sha256.len()]
        .copy_from_slice(record.certificate_sha256.as_bytes());
    bytes
}

fn decode(bytes: &[u8]) -> Result<ManagerIdentityRecord> {
    if bytes.len() != RECORD_SIZE {
        bail!("manager identity record has an unexpected size");
    }

    let magic = u32::from_le_bytes(bytes[0..4].try_into()?);
    let version = u32::from_le_bytes(bytes[4..8].try_into()?);
    let certificate_size = u32::from_le_bytes(bytes[8..12].try_into()?);
    if magic != MAGIC || version != VERSION {
        bail!("manager identity record header is invalid");
    }

    let package_field = &bytes[12..12 + PACKAGE_SIZE];
    let Some(package_end) = package_field.iter().position(|&c| c == 0) else {
        bail!("manager identity package is not terminated");
    };
    let package =
        std::str::from_utf8(&package_field[..package_end]).context("manager identity package is not UTF-8")?;

    let sha_offset = 12 + PACKAGE_SIZE;
    let sha_field = &bytes[sha_offset..sha_offset + SHA256_STR_SIZE];
    let Some(sha_end) = sha_field.iter().position(|&c| c == 0) else {
        bail!("manager identity certificate hash is not terminated");
    };
    let sha =
        std::str::from_utf8(&sha_field[..sha_end]).context("manager identity certificate hash is not UTF-8")?;

    if certificate_size == 0 || certificate_size > MAX_CERTIFICATE_SIZE {
        bail!("manager identity certificate size is invalid");
    }
    if !valid_package(package) || !valid_sha256(sha) {
        bail!("manager identity record content is invalid");
    }

    Ok(ManagerIdentityRecord {
        package: package.to_string(),
        certificate_size,
        certificate_sha256: sha.to_string(),
    })
}

fn write_record(record: &ManagerIdentityRecord) -> Result<()> {
    let path = Path::new(defs::MANAGER_IDENTITY);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }

    let temporary = path.with_extension("new");
    {
        let mut file = fs::File::create(&temporary)?;
        file.write_all(&encode(record))?;
        file.sync_all()?;
    }
    fs::set_permissions(&temporary, fs::Permissions::from_mode(0o600))?;
    fs::rename(&temporary, path)?;
    fs::set_permissions(path, fs::Permissions::from_mode(0o600))?;
    Ok(())
}

fn refresh_kernel() {
    // Older kernels do not know the refresh ioctl. The record is already on disk,
    // so a later packages.list change still picks it up; do not fail the write.
    if let Err(error) = ksucalls::refresh_manager() {
        log::warn!("manager identity written but kernel refresh failed: {error}");
    }
}

pub fn get() -> Result<Option<ManagerIdentityRecord>> {
    let bytes = match fs::read(defs::MANAGER_IDENTITY) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error.into()),
    };

    Ok(Some(decode(&bytes)?))
}

pub fn set(package: &str, certificate_size: u32, certificate_sha256: &str) -> Result<()> {
    if !valid_package(package) {
        bail!("invalid manager package name");
    }
    if certificate_size == 0 || certificate_size > MAX_CERTIFICATE_SIZE {
        bail!("invalid manager certificate size");
    }
    if !valid_sha256(certificate_sha256) {
        bail!("invalid manager certificate SHA-256");
    }

    write_record(&ManagerIdentityRecord {
        package: package.to_string(),
        certificate_size,
        certificate_sha256: certificate_sha256.to_string(),
    })?;
    refresh_kernel();
    Ok(())
}

/// Check whether the running kernel understands the manager refresh ioctl.
pub fn probe() -> Result<()> {
    ksucalls::refresh_manager()
}

pub fn clear() -> Result<()> {
    match fs::remove_file(defs::MANAGER_IDENTITY) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
    }
    refresh_kernel();
    Ok(())
}
