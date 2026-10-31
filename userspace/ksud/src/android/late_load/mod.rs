pub mod magica;

use std::{ffi::CString, process::Command, time::Instant};

use anyhow::{Context, Result};
use log::{info, warn};
use rustix::cstr;

use crate::{
    android::{
        dynamic_manager, init_event,
        module::{ScriptWait, handle_updated_modules, metamodule, prune_modules},
        restorecon, utils,
    },
    assets, defs,
};

const KSU_BLOCK_MODULES_CONFIG: &str = "/data/adb/ksu/block_modules";
const KSU_BLOCK_MODULES_MAX_LEN: usize = 255;

/// Read the optional kernel-module blacklist configured from the manager.
/// The list is passed as the `block_modules` module parameter at load time
/// (see ksu_module_blacklist_init), so it must be set before kernelsu.ko loads.
fn read_block_modules() -> Option<String> {
    let modules = std::fs::read_to_string(KSU_BLOCK_MODULES_CONFIG).ok()?;
    let modules = modules.trim().to_string();
    if modules.is_empty() || modules.len() > KSU_BLOCK_MODULES_MAX_LEN {
        return None;
    }
    let valid = modules.split(',').all(|name| {
        !name.is_empty()
            && name
                .bytes()
                .all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-')
    });
    if valid { Some(modules) } else { None }
}

fn dump_process_info(label: &str) {
    use rustix::process::{getgid, getgroups, getpid, getuid};

    let pid = getpid().as_raw_nonzero();
    let uid = getuid().as_raw();
    let gid = getgid().as_raw();
    let groups: Vec<String> = getgroups()
        .unwrap_or_default()
        .iter()
        .map(|g| g.as_raw().to_string())
        .collect();
    let selinux = std::fs::read_to_string("/proc/self/attr/current")
        .unwrap_or_else(|_| "unknown".to_string());
    let seccomp = std::fs::read_to_string("/proc/self/status")
        .ok()
        .and_then(|s| {
            s.lines()
                .find(|l| l.starts_with("Seccomp:"))
                .map(|l| l.trim().to_string())
        })
        .unwrap_or_else(|| "unknown".to_string());

    info!(
        "[{label}] pid={pid}, uid={uid}, gid={gid}, groups=[{}], selinux={}, {seccomp}",
        groups.join(","),
        selinux.trim(),
    );
}

/// Directory where the manager imports user-built LKM images (e.g. for
/// kernel 5.4, which has no unified GKI image and therefore no embedded KO).
fn custom_lkm_dir(package_name: &str) -> String {
    format!("/data/data/{package_name}/files/lkm/custom")
}

/// Read a user-imported KO, rejecting anything that is not an ELF.
fn read_custom_ko(path: &str) -> Option<Vec<u8>> {
    let data = std::fs::read(path).ok()?;
    if data.len() < 64 || &data[..4] != b"\x7fELF" {
        warn!("custom KO at {path} is not a valid ELF, ignored");
        return None;
    }
    Some(data)
}

/// Load the embedded SUSFS LKM for the running kernel if SUSFS is not already active.
///
/// SUSFS on GKI is shipped as a separate loadable module; this makes it part of the
/// manager/ksud bundle so that a temporary-root activation brings SUSFS up as well,
/// without flashing a separate KernelSU module.
fn load_susfs_lkm(package_name: &str) {
    // Already present (built-in kernel SUSFS, or loaded earlier)?
    if std::path::Path::new("/proc/susfs_path").exists()
        || std::path::Path::new("/sys/module/susfs_guard_lkm").exists()
    {
        info!("SUSFS already present, skip SUSFS LKM");
        return;
    }

    let custom = format!("{}/susfs_guard_lkm.ko", custom_lkm_dir(package_name));
    if let Some(data) = read_custom_ko(&custom) {
        info!("SUSFS LKM: loading custom {custom}");
        match ksuinit::load_module(&data, cstr!("expose_proc=1")) {
            Ok(()) => {
                info!("custom SUSFS LKM loaded successfully");
                return;
            }
            Err(e) => warn!("custom SUSFS LKM failed: {e:#}, falling back to embedded"),
        }
    }

    let kmi = match crate::boot_patch::get_current_kmi() {
        Ok(kmi) => kmi,
        Err(e) => {
            warn!("SUSFS LKM: cannot detect KMI: {e:#}");
            return;
        }
    };
    let name = format!("{kmi}_susfs_guard_lkm.ko");
    let data = match assets::get_asset(&name) {
        Ok(data) => data,
        Err(_) => {
            info!("SUSFS LKM: no embedded {name}, skip");
            return;
        }
    };

    info!("SUSFS LKM: loading {name} ...");
    match ksuinit::load_module(&data, cstr!("expose_proc=1")) {
        Ok(()) => info!("SUSFS LKM loaded successfully"),
        Err(e) => warn!("SUSFS LKM load failed: {e:#}"),
    }
}

pub fn run(package_name: &String, kmi: Option<String>, allow_shell: bool) -> Result<()> {
    utils::daemonize(false)?;
    info!("late-load command triggered!");
    dump_process_info("late-load start");

    // 1. Check if KernelSU is already loaded
    if ksuinit::has_kernelsu() {
        info!("KernelSU already loaded, skip loading ko");
    } else {
        // 2. Detect current KMI version
        let kmi = kmi.map_or_else(
            || crate::boot_patch::get_current_kmi().context("Failed to detect current KMI version"),
            Ok,
        )?;
        info!("Detected KMI: {kmi}");

        // 3. Prefer a user-imported custom ko (for kernels without a unified
        // GKI image, e.g. 5.4), then fall back to the embedded KMI asset.
        let custom_ko = format!("{}/kernelsu.ko", custom_lkm_dir(package_name));
        let (ko_data, ko_source) = match read_custom_ko(&custom_ko) {
            Some(data) => {
                info!("Using custom kernelsu.ko: {custom_ko}");
                (data, custom_ko)
            }
            None => {
                let ko_name = format!("{kmi}_kernelsu.ko");
                let data = assets::get_asset(&ko_name)
                    .with_context(|| format!("Failed to get {ko_name} from assets"))?;
                info!("Using embedded {ko_name}");
                (data.into_owned(), ko_name)
            }
        };

        // 4. Load kernelsu.ko from memory with manual relocation
        info!("Loading kernelsu.ko for KMI {kmi}...");
        // bundled flag is meaningless in jailbreak mode since we can't flash boot to update it.
        let mut param_str = String::new();
        if allow_shell {
            param_str.push_str("allow_shell=1");
        }
        if let Some(modules) = read_block_modules() {
            if !param_str.is_empty() {
                param_str.push(' ');
            }
            param_str.push_str("block_modules=");
            param_str.push_str(&modules);
        }
        let params = CString::new(param_str).context("Invalid module parameters")?;
        ksuinit::load_module(&ko_data, params.as_c_str())
            .with_context(|| format!("Failed to load kernelsu.ko from {ko_source}"))?;
        info!("kernelsu.ko loaded successfully!");
        dump_process_info("after load_module");
    }

    load_susfs_lkm(package_name);

    // We need to reset stdin/stdout/stderr; otherwise, sending file descriptors via cmd transactions
    // will be blocked by SELinux because its fsec->sid is still u:r:su:s0 instead of u:r:ksu:s0.
    utils::reset_std()?;

    utils::umask(0);

    if let Err(e) = crate::android::module::module_config::clear_all_temp_configs() {
        warn!("clear temp configs failed: {e}");
    }

    utils::install(None, None).context("Failed to install ksud")?;

    crate::android::hot_install::clean_trash();

    // 5. Handle module updates
    if let Err(e) = handle_updated_modules() {
        warn!("handle updated modules failed: {e}");
    }

    if let Err(e) = prune_modules() {
        warn!("prune modules failed: {e}");
    }

    if let Err(e) = restorecon::restorecon() {
        warn!("restorecon failed: {e}");
    }

    // 6. Load SELinux rules
    if crate::android::module::load_sepolicy_rule().is_err() {
        warn!("load sepolicy.rule failed");
    }

    if let Err(e) = crate::android::profile::apply_sepolies() {
        warn!("apply root profile sepolicy failed: {e}");
    }

    // 7. Initialize features
    crate::android::feature::init_forced_features();
    if let Err(e) = crate::android::feature::init_features() {
        warn!("init features failed: {e}");
    }

    // 8. Execute late-load stage scripts with a shared boot deadline
    let wait = ScriptWait::Until(Instant::now() + defs::BOOT_STAGE_TIMEOUT);
    init_event::run_stage("late-load", wait);

    // 9. Load system.prop
    if let Err(e) = crate::android::module::load_system_prop() {
        warn!("load system.prop failed: {e}");
    }

    // 10. Execute metamodule mount script (OverlayFS)
    if let Err(e) = metamodule::exec_mount_script(defs::MODULE_DIR) {
        warn!("execute metamodule mount failed: {e}");
    }
    // 11. Execute dynamic manager booted load
    if let Err(e) = dynamic_manager::booted_load() {
        warn!("set dynamic manager failed: {e}");
    }

    // 12. Execute post-mount stage scripts (blocking)
    init_event::run_stage("post-mount", wait);

    // 13. Execute service stage scripts (non-blocking)
    init_event::run_stage("service", ScriptWait::NoWait);

    // 14. Execute boot-completed stage scripts (non-blocking)
    init_event::run_stage("boot-completed", ScriptWait::NoWait);

    // 15. Restart Manager so it gets a fresh ksu fd from the newly loaded kernel module
    info!("Restarting KernelSU Manager {package_name}...");
    let _ = Command::new("am")
        .args(["force-stop", package_name])
        .status();
    let _ = Command::new("am")
        .args(["start", "-n", &format!("{package_name}/.ui.MainActivity")])
        .status();

    Ok(())
}
