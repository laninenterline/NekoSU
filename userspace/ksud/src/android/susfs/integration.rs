//! SUSFS integration control.
//!
//! The manager exposes a "SUSFS integration" switch that loads the SUSFS guard
//! LKM on demand and at boot. Loading reuses the same `ksuinit` module loader,
//! `{kmi}_susfs_guard_lkm.ko` assets and `expose_proc=1` parameter as the
//! late-load path, so the manual switch and the boot auto-load agree.
//!
//! The switch state is persisted through ksud's module config store. Every CLI
//! command prints one JSON object `{ok, data, error}` so the manager can render
//! the state without parsing human-oriented text.

use std::{
    ffi::CString,
    fs::{self, OpenOptions},
    os::fd::AsRawFd,
    path::Path,
};

use anyhow::{Context, Result, bail, ensure};
use log::{info, warn};
use serde::Serialize;
use serde_json::json;

use crate::{
    android::{
        module::module_config::{self, ConfigType},
        utils,
    },
    assets, defs,
};

const MODULE_NAME: &str = "susfs_guard_lkm";
const MODULE_SYSFS: &str = "/sys/module/susfs_guard_lkm";
const MODULE_PARAMS: &str = "expose_proc=1";
const CONFIG_ID: &str = "internal.ksud.susfsd";
const AUTOLOAD_KEY: &str = "autoload.enabled";
const FALLBACK_KMI_KEY: &str = "autoload.fallback_kmi";
const UNLOAD_PENDING_KEY: &str = "unload.pending";
const LKM_OWNED_KEY: &str = "lkm.loaded_by_ksud";
const AVC_SUSFS_SAVED_KEY: &str = "avc.susfs_saved";

/// Stable status contract shared with the manager.
#[derive(Debug, Clone, Serialize)]
pub struct IntegrationStatus {
    /// Can SUSFS integration be driven on this device (LKM available, or built in).
    pub supported: bool,
    /// SUSFS is operational right now (built-in or loaded LKM).
    pub loaded: bool,
    /// SUSFS comes from the kernel/root module itself, not from the guard LKM.
    pub builtin: bool,
    /// The guard LKM was loaded by this integration, so disabling may unload it.
    pub owned: bool,
    /// Boot auto-load is enabled.
    pub autoload: bool,
    /// `builtin`, `lkm` or `none`.
    pub backend: &'static str,
    pub version: Option<String>,
    pub variant: Option<String>,
    pub kmi: Option<String>,
    /// Human-readable reason when the integration is unavailable or incomplete.
    pub reason: Option<String>,
    /// The LKM is still loaded after a disable request, so a reboot is needed.
    pub reboot_required: bool,
}

struct OperationLock(fs::File);

impl OperationLock {
    fn acquire() -> Result<Self> {
        utils::ensure_dir_exists(defs::WORKING_DIR)?;
        let path = format!("{}.susfsd.lock", defs::WORKING_DIR);
        let file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .write(true)
            .open(path)?;
        ensure!(
            unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX) } == 0,
            "Cannot lock SUSFS control: {}",
            std::io::Error::last_os_error()
        );
        Ok(Self(file))
    }
}

impl Drop for OperationLock {
    fn drop(&mut self) {
        unsafe {
            libc::flock(self.0.as_raw_fd(), libc::LOCK_UN);
        }
    }
}

fn get_config(key: &str) -> Result<Option<String>> {
    module_config::get_config_value(CONFIG_ID, key, ConfigType::Persist)
}

fn set_config(key: &str, value: &str) -> Result<()> {
    module_config::set_config_value(CONFIG_ID, key, value, ConfigType::Persist)
}

fn delete_config(key: &str) {
    let _ = module_config::delete_config_value(CONFIG_ID, key, ConfigType::Persist);
}

fn config_bool(key: &str, fallback: bool) -> Result<bool> {
    Ok(get_config(key)?
        .as_deref()
        .map(module_config::parse_bool_config)
        .unwrap_or(fallback))
}

fn module_present() -> bool {
    Path::new(MODULE_SYSFS).is_dir()
}

/// /sys/module can be hidden from the caller on hardened configurations. When it
/// is not observable, the absence of /sys/module/susfs_guard_lkm proves nothing
/// and the version probe must decide whether SUSFS is active.
fn sysfs_modules_visible() -> bool {
    fs::read_dir("/sys/module")
        .map(|mut entries| entries.next().is_some())
        .unwrap_or(false)
}

fn owned_by_ksud(loaded: bool) -> Result<bool> {
    let marked = config_bool(LKM_OWNED_KEY, false)?;
    if marked && !loaded {
        // The module we loaded is gone after a reboot or an external unload.
        delete_config(LKM_OWNED_KEY);
        return Ok(false);
    }
    Ok(marked)
}

fn susfs_version() -> Option<String> {
    crate::android::susfs::api::prelude::version()
        .ok()
        .filter(|version| !version.is_empty() && version != "Unsupported")
}

fn susfs_variant() -> Option<String> {
    crate::android::susfs::api::prelude::variant()
        .ok()
        .filter(|variant| !variant.is_empty())
}

fn detected_kmi() -> Option<String> {
    crate::boot_patch::get_current_kmi().ok()
}

fn manager_package() -> Option<String> {
    crate::android::manager_identity::get()
        .ok()
        .flatten()
        .map(|record| record.package)
        .or_else(|| Some(crate::defs::DEFAULT_PACKAGE_NAME.to_string()))
}

/// Locate a user-imported custom SUSFS LKM. The manager passes its own package
/// name for the explicit enable/status commands (hidden managers rename the
/// package); boot auto-load falls back to the recorded identity or the build
/// default.
fn custom_ko_path(package: Option<&str>) -> Option<String> {
    let package = package.map(str::to_owned).or_else(manager_package)?;
    Some(format!(
        "/data/data/{package}/files/lkm/custom/{MODULE_NAME}.ko"
    ))
}

fn has_custom_ko(package: Option<&str>) -> bool {
    custom_ko_path(package).is_some_and(|path| Path::new(&path).is_file())
}

fn has_embedded_ko(kmi: Option<&str>) -> bool {
    kmi.is_some_and(|kmi| assets::get_asset(&format!("{kmi}_{MODULE_NAME}.ko")).is_ok())
}

fn read_custom_ko(package: Option<&str>) -> Option<Vec<u8>> {
    let path = custom_ko_path(package)?;
    let data = fs::read(&path).ok()?;
    if data.len() < 64 || &data[..4] != b"\x7fELF" {
        warn!("custom SUSFS LKM at {path} is not a valid ELF, ignored");
        return None;
    }
    Some(data)
}

pub fn status(package: Option<&str>) -> Result<IntegrationStatus> {
    let module_present = module_present();
    let sysfs_visible = sysfs_modules_visible();
    let version = susfs_version();
    let variant = susfs_variant();
    let operational = version.is_some();
    let loaded = module_present || operational;
    let owned = owned_by_ksud(loaded)?;
    // Built-in SUSFS has no module entry, but that is only meaningful when the
    // sysfs module list is actually observable and the version probe works.
    let builtin = operational && !module_present && sysfs_visible && !owned;
    let kmi = detected_kmi();
    let autoload = config_bool(AUTOLOAD_KEY, true)?;
    let arch_supported = cfg!(target_arch = "aarch64");
    let supported = builtin
        || loaded
        || (arch_supported && (has_custom_ko(package) || has_embedded_ko(kmi.as_deref())));

    let mut reboot_required = config_bool(UNLOAD_PENDING_KEY, false)?;
    if reboot_required && !loaded {
        delete_config(UNLOAD_PENDING_KEY);
        reboot_required = false;
    }

    let reason = if !supported {
        Some(if !arch_supported {
            "SUSFS LKM is only supported on arm64".to_string()
        } else if kmi.is_none() {
            "Cannot detect the running KMI".to_string()
        } else {
            format!(
                "No bundled SUSFS LKM for KMI {}",
                kmi.as_deref().unwrap_or("unknown")
            )
        })
    } else if builtin {
        Some("SUSFS is built into the kernel; loading an LKM is not needed".to_string())
    } else if reboot_required {
        Some("SUSFS is still loaded; reboot to finish disabling".to_string())
    } else if module_present && !operational {
        Some("SUSFS LKM is loaded but the version probe is unavailable".to_string())
    } else if operational && !module_present && !sysfs_visible {
        Some(
            "SUSFS is active but /sys/module is not visible; treating it as a loaded LKM"
                .to_string(),
        )
    } else if loaded && !owned && !builtin {
        Some(
            "SUSFS was loaded by another provider; this switch only unloads an LKM it loaded"
                .to_string(),
        )
    } else {
        None
    };

    Ok(IntegrationStatus {
        supported,
        loaded,
        builtin,
        owned,
        autoload,
        backend: if builtin {
            "builtin"
        } else if loaded {
            "lkm"
        } else {
            "none"
        },
        version,
        variant,
        kmi,
        reason,
        reboot_required,
    })
}

fn print_wrapper(ok: bool, status: Option<&IntegrationStatus>, error: Option<String>) {
    println!("{}", json!({ "ok": ok, "data": status, "error": error }));
}

pub fn run_status(package: Option<&str>) -> Result<()> {
    match status(package) {
        Ok(status) => {
            print_wrapper(true, Some(&status), None);
            Ok(())
        }
        Err(error) => {
            print_wrapper(false, None, Some(format!("{error:#}")));
            Err(error)
        }
    }
}

fn require_usable(status: &IntegrationStatus) -> Result<()> {
    if status.builtin {
        bail!("SUSFS is built into the kernel; no LKM integration is required");
    }
    if !status.supported {
        bail!(
            "{}",
            status
                .reason
                .clone()
                .unwrap_or_else(|| "SUSFS LKM is unsupported".to_string())
        );
    }
    Ok(())
}

fn load_guard(package: Option<&str>) -> Result<()> {
    ensure!(
        cfg!(target_arch = "aarch64"),
        "SUSFS LKM is only supported on arm64"
    );
    if module_present() || susfs_version().is_some() {
        return Ok(());
    }

    let kmi = detected_kmi()
        .or_else(|| get_config(FALLBACK_KMI_KEY).ok().flatten())
        .context("Cannot detect the current KMI for SUSFS LKM loading")?;

    let data = if let Some(data) = read_custom_ko(package) {
        info!(
            "SUSFS LKM: loading custom {}",
            custom_ko_path(package).unwrap_or_default()
        );
        data
    } else {
        assets::get_asset(&format!("{kmi}_{MODULE_NAME}.ko"))
            .with_context(|| format!("No bundled SUSFS LKM for KMI {kmi}"))?
            .into_owned()
    };

    info!("SUSFS LKM: loading for KMI {kmi} ...");
    let params = CString::new(MODULE_PARAMS).context("Invalid SUSFS module parameters")?;
    ksuinit::load_module(&data, params.as_c_str()).context("Failed to load the SUSFS LKM")?;
    ensure!(
        module_present() || susfs_version().is_some(),
        "SUSFS LKM load reported success but the module is not active"
    );
    // Remember that this integration owns the module, so `susfs disable` may
    // unload it (never a module loaded by another provider).
    let _ = set_config(LKM_OWNED_KEY, "true");
    delete_config(UNLOAD_PENDING_KEY);
    info!("SUSFS LKM loaded successfully");
    Ok(())
}

enum UnloadOutcome {
    Unloaded,
    NotLoaded,
    External,
    RebootRequired,
}

/// Best-effort unload of the guard LKM. Only a module loaded by this integration
/// is deleted; a module loaded by another provider is left resident.
fn unload_guard() -> Result<UnloadOutcome> {
    let loaded = module_present() || susfs_version().is_some();
    if !loaded {
        delete_config(UNLOAD_PENDING_KEY);
        delete_config(LKM_OWNED_KEY);
        return Ok(UnloadOutcome::NotLoaded);
    }
    if !config_bool(LKM_OWNED_KEY, false)? {
        info!("SUSFS LKM was loaded by another provider; leaving it loaded");
        return Ok(UnloadOutcome::External);
    }

    // Hot-unplug: keep retrying long enough for the LKM's exit path (LSM hook
    // removal + synchronize_rcu) to drain. 5 attempts of 20ms was too short on
    // some kernels and falsely reported "reboot required".
    for attempt in 0..50 {
        match rustix::system::delete_module(c"susfs_guard_lkm", 0) {
            Ok(()) => {
                for _ in 0..20 {
                    if !module_present() && susfs_version().is_none() {
                        delete_config(UNLOAD_PENDING_KEY);
                        delete_config(LKM_OWNED_KEY);
                        info!("SUSFS LKM unloaded");
                        return Ok(UnloadOutcome::Unloaded);
                    }
                    std::thread::sleep(std::time::Duration::from_millis(50));
                }
                break;
            }
            Err(error)
                if attempt < 49
                    && (error == rustix::io::Errno::BUSY
                        || error == rustix::io::Errno::AGAIN) =>
            {
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
            Err(error) if error == rustix::io::Errno::NOENT => {
                delete_config(UNLOAD_PENDING_KEY);
                delete_config(LKM_OWNED_KEY);
                return Ok(UnloadOutcome::NotLoaded);
            }
            Err(error) => {
                warn!("SUSFS LKM unload failed: {error}");
                break;
            }
        }
    }

    if !module_present() && susfs_version().is_none() {
        delete_config(UNLOAD_PENDING_KEY);
        delete_config(LKM_OWNED_KEY);
        info!("SUSFS LKM unloaded");
        return Ok(UnloadOutcome::Unloaded);
    }

    let _ = set_config(UNLOAD_PENDING_KEY, "true");
    warn!("SUSFS LKM is still busy; keeping it loaded (retry disable later)");
    Ok(UnloadOutcome::RebootRequired)
}

fn write_autoload_marker(enabled: bool) {
    use std::os::unix::fs::PermissionsExt;

    let path = defs::SUSFS_AUTOLOAD_CONFIG;
    if let Err(error) = fs::write(path, if enabled { "1\n" } else { "0\n" }) {
        warn!("Cannot write the SUSFS auto-load marker {path}: {error}");
        return;
    }
    let _ = std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600));
}

fn set_autoload(enabled: bool, fallback_kmi: Option<&str>) -> Result<()> {
    set_config(AUTOLOAD_KEY, if enabled { "true" } else { "false" })
        .context("Cannot persist the SUSFS auto-load setting")?;
    // The standalone susfs_guard_lkm KernelSU module loads the same LKM from its
    // own scripts; mirror the choice so disabling the integration also disables
    // that autoload path.
    write_autoload_marker(enabled);
    if enabled {
        if let Some(kmi) = fallback_kmi {
            let _ = set_config(FALLBACK_KMI_KEY, kmi);
        }
    } else {
        delete_config(UNLOAD_PENDING_KEY);
    }
    Ok(())
}

pub fn autoload_enabled() -> bool {
    config_bool(AUTOLOAD_KEY, true).unwrap_or(true)
}

/// Load the SUSFS LKM at boot when the manager enabled the integration.
pub fn load_if_autoload_enabled() -> Result<()> {
    let before = status(None)?;
    if before.builtin {
        return Ok(());
    }
    if !before.autoload {
        if !before.loaded {
            delete_config(UNLOAD_PENDING_KEY);
        }
        return Ok(());
    }
    if before.loaded {
        // Never adopt a module that another provider loaded; ownership is only
        // recorded by load_guard() when this integration loads it itself.
        return Ok(());
    }
    info!("SUSFS auto-load enabled, loading the guard LKM");
    load_guard(None)?;
    let after = status(None)?;
    ensure!(after.loaded, "SUSFS auto-load did not activate SUSFS");
    Ok(())
}

pub fn enable(package: Option<&str>) -> Result<IntegrationStatus> {
    let _operation = OperationLock::acquire()?;
    let before = status(package)?;
    require_usable(&before)?;
    if !before.loaded {
        load_guard(package)?;
    }
    let kmi = if before.kmi.is_some() {
        before.kmi.clone()
    } else {
        detected_kmi()
    };
    set_autoload(true, kmi.as_deref())?;
    delete_config(UNLOAD_PENDING_KEY);
    let after = status(package)?;
    ensure!(
        after.loaded,
        "SUSFS integration was enabled but SUSFS is not active"
    );
    Ok(after)
}

pub fn disable() -> Result<IntegrationStatus> {
    let _operation = OperationLock::acquire()?;
    let before = status(None)?;
    if before.builtin {
        bail!("SUSFS is built into the kernel; integration cannot be disabled");
    }
    set_autoload(false, None)?;
    match unload_guard()? {
        UnloadOutcome::Unloaded => info!("SUSFS integration disabled and the LKM was unloaded"),
        UnloadOutcome::NotLoaded => info!("SUSFS integration disabled; no LKM was loaded"),
        UnloadOutcome::External => {
            info!("SUSFS integration disabled; the LKM loaded by another provider stays resident")
        }
        UnloadOutcome::RebootRequired => {
            info!("SUSFS integration disabled; reboot required to unload the LKM")
        }
    }
    status(None)
}

fn finish(result: Result<IntegrationStatus>, package: Option<&str>) -> Result<()> {
    match result {
        Ok(status) => {
            print_wrapper(true, Some(&status), None);
            Ok(())
        }
        Err(error) => {
            let fallback = status(package).ok();
            print_wrapper(false, fallback.as_ref(), Some(format!("{error:#}")));
            Err(error)
        }
    }
}

pub fn run_enable(package: Option<&str>) -> Result<()> {
    finish(enable(package), package)
}

pub fn run_disable() -> Result<()> {
    finish(disable(), None)
}

/// Keep the KernelSU AVC spoof and SUSFS AVC log spoofing from fighting.
///
/// When the KernelSU feature is enabled, the current SUSFS `avc_log_spoofing`
/// value is remembered and SUSFS's spoof is switched off (kernel and persisted
/// config). When the KernelSU feature is disabled again, the remembered value is
/// written back, so the SUSFS setting is restored instead of being lost.
pub fn sync_susfs_avc_precedence(ksu_avc_enabled: bool) {
    use crate::android::susfs::config::model::Config;

    if ksu_avc_enabled {
        // Repeated apply (boot, feature save) must not overwrite the value that
        // was remembered the first time the KernelSU feature was enabled, but it
        // must keep asserting the precedence.
        let saved = match get_config(AVC_SUSFS_SAVED_KEY) {
            Ok(Some(value)) => module_config::parse_bool_config(&value),
            Ok(None) => {
                let saved = match Config::read() {
                    Ok(config) => config.avc_log_spoofing(),
                    Err(error) => {
                        info!("SUSFS config not found, assuming AVC spoof is disabled: {error}");
                        false
                    }
                };
                if let Err(error) =
                    set_config(AVC_SUSFS_SAVED_KEY, if saved { "true" } else { "false" })
                {
                    warn!("Cannot remember the SUSFS AVC setting: {error:#}");
                }
                saved
            }
            Err(error) => {
                warn!("Cannot read the remembered SUSFS AVC setting: {error:#}");
                return;
            }
        };
        if let Err(error) = crate::android::susfs::api::prelude::enable_avc_log_spoofing(false) {
            info!(
                "SUSFS AVC spoof is not available while enabling the KernelSU AVC spoof: {error}"
            );
        }
        // Even when the remembered value was already false, assert the precedence
        // again: the SUSFS config may have re-enabled it from another tool.
        match Config::read() {
            Ok(mut config) => {
                if config.avc_log_spoofing() {
                    config.set_avc_log_spoofing(false);
                    if let Err(error) = config.save() {
                        warn!("Cannot persist SUSFS AVC spoof disabled: {error:#}");
                    } else {
                        info!("SUSFS AVC spoof disabled by the KernelSU AVC spoof setting");
                    }
                }
            }
            Err(error) => info!("SUSFS config changed, skip persisting AVC spoof=off: {error}"),
        }
    } else {
        let saved = match get_config(AVC_SUSFS_SAVED_KEY) {
            Ok(Some(value)) => module_config::parse_bool_config(&value),
            Ok(None) => return,
            Err(error) => {
                warn!("Cannot read the remembered SUSFS AVC setting: {error:#}");
                return;
            }
        };
        if let Err(error) = crate::android::susfs::api::prelude::enable_avc_log_spoofing(saved) {
            info!("SUSFS AVC spoof is not loaded while restoring its setting: {error}");
        }
        match Config::read() {
            Ok(mut config) => {
                config.set_avc_log_spoofing(saved);
                if let Err(error) = config.save() {
                    warn!("Cannot restore the SUSFS AVC setting: {error:#}");
                } else {
                    info!("SUSFS AVC spoof restored to {saved}");
                }
            }
            Err(error) => info!("SUSFS config not found, skip restoring AVC setting: {error}"),
        }
        delete_config(AVC_SUSFS_SAVED_KEY);
    }
}
