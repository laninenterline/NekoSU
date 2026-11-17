//! Root-owned, content-addressed KPM registry with an interprocess lock.

use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::os::fd::AsRawFd;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, ensure};
use serde_json::{Value, json};

use super::elf::{FILE_LIMIT, Metadata};
use super::failure;

#[derive(Clone, Debug)]
pub(super) struct Record {
    pub id: String,
    pub metadata: Metadata,
    pub args: String,
    pub autoload: bool,
    pub pending_unload: bool,
    pub pending_boot_id: String,
    pub last_error: Option<String>,
    pub last_attempt_boot: String,
    pub last_success_boot: String,
}

impl Record {
    pub fn value(&self, loaded: bool) -> Value {
        json!({
            "id": self.id,
            "name": self.metadata.name,
            "version": self.metadata.version,
            "author": self.metadata.author,
            "description": self.metadata.description,
            "args": self.args,
            "sha256": self.id,
            "autoload": self.autoload,
            "loaded": loaded,
            "pending_unload": self.pending_unload,
            "last_error": self.last_error,
            "has_control": self.metadata.has_control,
        })
    }

    fn stored_value(&self) -> Value {
        let mut value = self.value(false);
        let object = value.as_object_mut().expect("record is an object");
        object.remove("loaded");
        object.insert("pending_boot_id".into(), json!(self.pending_boot_id));
        object.insert("last_attempt_boot".into(), json!(self.last_attempt_boot));
        object.insert("last_success_boot".into(), json!(self.last_success_boot));
        value
    }

    fn parse(value: &Value) -> Result<Self> {
        let string = |name: &str| -> Result<String> {
            value
                .get(name)
                .and_then(Value::as_str)
                .map(str::to_owned)
                .ok_or_else(|| failure("invalid_registry", format!("Missing string field {name}")))
        };
        let id = string("id")?;
        validate_id(&id)?;
        Ok(Self {
            id,
            metadata: Metadata {
                name: string("name")?,
                version: string("version")?,
                author: string("author")?,
                description: string("description")?,
                has_control: value
                    .get("has_control")
                    .and_then(Value::as_bool)
                    .unwrap_or(false),
            },
            args: string("args")?,
            autoload: value
                .get("autoload")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            pending_unload: value
                .get("pending_unload")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            pending_boot_id: value
                .get("pending_boot_id")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_owned(),
            last_error: value
                .get("last_error")
                .and_then(Value::as_str)
                .map(str::to_owned),
            last_attempt_boot: value
                .get("last_attempt_boot")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_owned(),
            last_success_boot: value
                .get("last_success_boot")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_owned(),
        })
    }
}

pub(super) fn validate_id(id: &str) -> Result<()> {
    ensure!(
        id.len() == 64
            && id
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        failure("invalid_id", "KPM id must be a lowercase SHA-256 digest")
    );
    Ok(())
}

pub(super) fn read_module(path: &Path) -> Result<Vec<u8>> {
    let file = OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW | libc::O_CLOEXEC)
        .open(path)
        .with_context(|| format!("Cannot open {}", path.display()))?;
    ensure!(
        file.metadata()?.is_file(),
        failure("invalid_file", "KPM input is not a regular file")
    );
    ensure!(
        file.metadata()?.len() <= FILE_LIMIT as u64,
        failure("invalid_file", "KPM exceeds 32 MiB")
    );
    let mut bytes = Vec::new();
    file.take(FILE_LIMIT as u64 + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() <= FILE_LIMIT,
        failure("invalid_file", "KPM grew beyond 32 MiB")
    );
    Ok(bytes)
}

pub(super) struct Store {
    root: PathBuf,
    _lock: File,
    pub records: Vec<Record>,
}

impl Store {
    pub fn open(root: &Path) -> Result<Self> {
        fs::create_dir_all(root)?;
        ensure!(
            !fs::symlink_metadata(root)?.file_type().is_symlink(),
            failure("invalid_registry", "KPM state root must not be a symlink")
        );
        fs::set_permissions(root, fs::Permissions::from_mode(0o700))?;
        let lock = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW | libc::O_CLOEXEC)
            .open(root.join(".lock"))?;
        if unsafe { libc::flock(lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
            return Err(failure(
                "busy",
                "Another KPM operation holds the registry lock",
            ));
        }
        let registry = root.join("registry.json");
        let records = if registry.try_exists()? {
            let input = OpenOptions::new()
                .read(true)
                .custom_flags(libc::O_NOFOLLOW | libc::O_CLOEXEC)
                .open(&registry)?;
            ensure!(
                input.metadata()?.is_file() && input.metadata()?.len() <= 1024 * 1024,
                failure("invalid_registry", "Invalid KPM registry size or file type")
            );
            let value: Value =
                serde_json::from_reader(input).context("Cannot parse KPM registry")?;
            ensure!(
                value.get("schema").and_then(Value::as_u64) == Some(1),
                failure("invalid_registry", "Unsupported KPM registry schema")
            );
            let modules = value
                .get("modules")
                .and_then(Value::as_array)
                .ok_or_else(|| failure("invalid_registry", "KPM module array is missing"))?;
            let parsed = modules
                .iter()
                .map(Record::parse)
                .collect::<Result<Vec<_>>>()?;
            let mut ids = std::collections::BTreeSet::new();
            let mut names = std::collections::BTreeSet::new();
            for record in &parsed {
                ensure!(
                    ids.insert(&record.id) && names.insert(&record.metadata.name),
                    failure("invalid_registry", "Duplicate KPM id or name")
                );
            }
            parsed
        } else {
            Vec::new()
        };
        Ok(Self {
            root: root.to_owned(),
            _lock: lock,
            records,
        })
    }

    pub fn path(&self, id: &str) -> Result<PathBuf> {
        validate_id(id)?;
        Ok(self.root.join(format!("{id}.kpm")))
    }

    pub fn save(&self) -> Result<()> {
        let value = json!({
            "schema": 1,
            "modules": self.records.iter().map(Record::stored_value).collect::<Vec<_>>(),
        });
        let mut temporary = tempfile::NamedTempFile::new_in(&self.root)?;
        temporary
            .as_file()
            .set_permissions(fs::Permissions::from_mode(0o600))?;
        serde_json::to_writer(&mut temporary, &value)?;
        temporary.write_all(b"\n")?;
        temporary.as_file().sync_all()?;
        temporary.persist(self.root.join("registry.json"))?;
        File::open(&self.root)?.sync_all()?;
        Ok(())
    }

    pub fn persist_module(&self, id: &str, bytes: &[u8]) -> Result<()> {
        let path = self.path(id)?;
        if path.try_exists()? {
            ensure!(
                sha256::digest(read_module(&path)?) == id,
                failure(
                    "integrity_error",
                    "Existing KPM content does not match its digest"
                )
            );
            return Ok(());
        }
        let mut temporary = tempfile::NamedTempFile::new_in(&self.root)?;
        temporary
            .as_file()
            .set_permissions(fs::Permissions::from_mode(0o600))?;
        temporary.write_all(bytes)?;
        temporary.as_file().sync_all()?;
        temporary.persist_noclobber(path)?;
        File::open(&self.root)?.sync_all()?;
        Ok(())
    }

    pub fn verified_module(&self, record: &Record) -> Result<Vec<u8>> {
        let path = self.path(&record.id)?;
        let bytes = read_module(&path)?;
        ensure!(
            sha256::digest(&bytes) == record.id,
            failure("integrity_error", "Stored KPM content has changed")
        );
        let parsed = super::elf::parse(&bytes)?;
        ensure!(
            parsed.name == record.metadata.name,
            failure("integrity_error", "Stored KPM name has changed")
        );
        Ok(bytes)
    }
}
