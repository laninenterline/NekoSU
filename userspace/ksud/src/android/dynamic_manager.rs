use std::fs;
use std::process::Command;

use log::info;

use anyhow::Result;
use serde::{Deserialize, Serialize};

use crate::{android::ksucalls, defs};

#[derive(Debug, Deserialize, Serialize)]
struct Config {
    size: u32,
    hash: String,
}

impl Config {
    fn hash_bytes(&self) -> Result<[u8; 64], String> {
        parse_hash(&self.hash)
    }

    pub fn set_hash_from_bytes(&mut self, hash: [u8; 64]) {
        self.hash = String::from_utf8_lossy(&hash).to_string();
    }
}


/// When the user has not selected a dynamic manager yet, register the manager
/// that just loaded us (package_name) so the dynamic-manager feature works out
/// of the box after a manual late-load.
pub fn ensure_registered(package_name: &str) -> Result<()> {
    let current = fs::read_to_string(defs::DYNAMIC_MANAGER).unwrap_or_default();
    if let Ok(cfg) = serde_json::from_str::<Config>(&current) {
        if cfg.size > 0 && cfg.hash.len() == 64 {
            return Ok(());
        }
    }

    let output = Command::new("pm")
        .args(["path", package_name])
        .output();
    let Ok(output) = output else { return Ok(()) };
    if !output.status.success() {
        return Ok(());
    }
    let text = String::from_utf8_lossy(&output.stdout);
    let Some(apk) = text.lines().find_map(|line| line.strip_prefix("package:")) else {
        // pm path prints "package:/data/app/..." (with the colon attached)
        return Ok(());
    };
    let apk = apk.trim();
    if apk.is_empty() {
        return Ok(());
    }
    let status = Command::new(defs::DAEMON_PATH)
        .args(["kernel", "dynamic-manager", "set-apk", apk])
        .status();
    if let Ok(status) = status {
        if status.success() {
            info!("dynamic manager registered: {package_name}");
        }
    }
    Ok(())
}

pub fn booted_load() -> Result<()> {
    let buf = match fs::read_to_string(defs::DYNAMIC_MANAGER) {
        Ok(s) => s,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(e) => return Err(e.into()),
    };

    let json: Config = serde_json::from_str(&buf)?;
    if json.hash.is_empty() || json.size == 0 {
        return Ok(());
    }
    let Ok(hash) = json.hash_bytes() else {
        return Ok(());
    };

    ksucalls::dynamic_manager_set(json.size, hash)?;
    Ok(())
}

pub fn parse_hash(s: &str) -> Result<[u8; 64], String> {
    s.as_bytes()
        .try_into()
        .map_err(|_| "Incorrect hash".to_string())
}

pub fn clear() -> Result<()> {
    let empty = Config {
        size: 0,
        hash: String::new(),
    };

    let string = serde_json::to_string_pretty(&empty)?;

    fs::write(defs::DYNAMIC_MANAGER, string)?;

    ksucalls::dynamic_manager_clear()?;

    Ok(())
}

pub fn set(size: u32, hash: [u8; 64]) -> Result<()> {
    let mut json_raw = Config {
        size,
        hash: String::new(),
    };
    json_raw.set_hash_from_bytes(hash);

    let string = serde_json::to_string_pretty(&json_raw)?;

    fs::write(defs::DYNAMIC_MANAGER, string)?;

    ksucalls::dynamic_manager_set_synchronous(size, hash)?;
    Ok(())
}
