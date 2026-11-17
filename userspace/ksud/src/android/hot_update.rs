//! Hot-update the KernelSU LKM without re-running the exploit/jailbreak.
//!
//! Sequence: stop userspace consumers -> delete_module("kernelsu") -> load the
//! new (embedded or user-supplied) kernelsu.ko -> reinstall ksud + features ->
//! restart the manager. A previously saved fallback KO is used if the new one
//! fails to load.

use std::ffi::CString;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use anyhow::{bail, Context, Result};
use log::{info, warn};

use crate::android::{
    dynamic_manager, feature, init_event, module::ScriptWait, restorecon, utils,
};
use crate::{assets, defs, webadmin_ksud};

const MODULE_NAME: &str = "kernelsu";
const WORK_DIR: &str = "/data/adb/ksu";
const KO_DIR: &str = "/data/adb/ksu/ko";
const LOG_PATH: &str = "/data/adb/ksu/hot-update.log";
const BLOCK_MODULES_CONFIG: &str = "/data/adb/ksu/block_modules";
const DEFAULT_BLOCK_MODULES: &str = "vr,vklp,oplus_secure_guard,oplus_secure_guard_new,mkp";

fn log_line(msg: &str) {
    info!("{msg}");
    let _ = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(LOG_PATH)
        .and_then(|mut f| {
            use std::io::Write;
            writeln!(f, "{msg}")
        });
}

fn read_block_modules() -> Option<String> {
    let modules = fs::read_to_string(BLOCK_MODULES_CONFIG).unwrap_or_else(|_| DEFAULT_BLOCK_MODULES.into());
    let modules = modules.trim().to_string();
    if modules.is_empty() || modules.len() > 255 {
        return None;
    }
    let valid = modules.split(',').all(|name| {
        !name.is_empty()
            && name
                .bytes()
                .all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-')
    });
    valid.then_some(modules)
}

fn read_ko(path: &Path) -> Result<Vec<u8>> {
    let data = fs::read(path).with_context(|| format!("读取 KO 失败: {}", path.display()))?;
    if data.len() < 64 || &data[..4] != b"\x7fELF" {
        bail!("不是有效的 ELF 模块: {}", path.display());
    }
    Ok(data)
}

fn embedded_ko(kmi: &str) -> Result<Vec<u8>> {
    let name = format!("{kmi}_kernelsu.ko");
    let data = assets::get_asset(&name).with_context(|| format!("内置资产缺少 {name}"))?;
    Ok(data.into_owned())
}

fn save_fallback(kmi: &str, data: &[u8]) {
    if let Err(e) = fs::create_dir_all(KO_DIR) {
        warn!("hot-update: create {KO_DIR} failed: {e}");
        return;
    }
    let path = format!("{KO_DIR}/fallback_{kmi}.ko");
    if let Err(e) = fs::write(&path, data) {
        warn!("hot-update: save fallback failed: {e}");
    }
}

/// Kill processes holding Android KernelSU driver fds (except ourselves).
fn kill_ksu_fd_holders() {
    let my_pid = std::process::id() as i32;
    let Ok(entries) = fs::read_dir("/proc") else { return };
    for entry in entries.flatten() {
        let Some(pid) = entry.file_name().to_str().and_then(|s| s.parse::<i32>().ok()) else {
            continue;
        };
        if pid == my_pid || pid <= 1 {
            continue;
        }
        let Ok(fds) = fs::read_dir(format!("/proc/{pid}/fd")) else { continue };
        let mut holds = false;
        for fd in fds.flatten() {
            if let Ok(target) = fs::read_link(fd.path()) {
                let t = target.to_string_lossy();
                if t.contains("[ksu_driver") || t.contains("[ksu_fdwrapper]") || t.contains("[ksu_driver_su") {
                    holds = true;
                    break;
                }
            }
        }
        if holds {
            info!("hot-update: kill ksu fd holder {pid}");
            unsafe { libc::kill(pid, libc::SIGKILL) };
        }
    }
}

fn close_own_ksu_fds() {
    let Ok(entries) = fs::read_dir("/proc/self/fd") else { return };
    for entry in entries.flatten() {
        if let Ok(target) = fs::read_link(entry.path()) {
            let t = target.to_string_lossy();
            if t.contains("[ksu_driver") || t.contains("[ksu_fdwrapper]") {
                if let Ok(fd) = entry.file_name().to_string_lossy().parse::<i32>() {
                    unsafe { libc::close(fd) };
                }
            }
        }
    }
}

fn unload_module() -> Result<()> {
    for attempt in 0..100 {
        match rustix::system::delete_module(c"kernelsu", 0) {
            Ok(()) => {
                info!("hot-update: kernelsu unloaded");
                return Ok(());
            }
            Err(e)
                if attempt < 99
                    && (e == rustix::io::Errno::BUSY || e == rustix::io::Errno::AGAIN) =>
            {
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
            Err(e) => bail!("卸载 kernelsu 失败: {e}（root 未受影响，可重试）"),
        }
    }
    bail!("卸载 kernelsu 超时（EBUSY），root 未受影响")
}

fn load_ko(data: &[u8], allow_shell: bool) -> Result<()> {
    let mut params = String::from("allow_unload=1");
    if allow_shell {
        params.push_str(" allow_shell=1");
    }
    if let Some(modules) = read_block_modules() {
        if !params.is_empty() {
            params.push(' ');
        }
        params.push_str("block_modules=");
        params.push_str(&modules);
    }
    let params = CString::new(params).context("模块参数含 NUL")?;
    ksuinit::load_module(data, params.as_c_str())
}

fn restart_manager(package: &str) {
    let _ = Command::new("am").args(["force-stop", package]).status();
    let _ = Command::new("am")
        .args(["start", "-n", &format!("{package}/.ui.MainActivity")])
        .status();
}

fn detach_self() -> Result<()> {
    use std::os::unix::process::CommandExt;
    let exe = std::env::current_exe().context("current_exe")?;
    let args: Vec<String> = std::env::args().skip(1).filter(|a| a != "--detach").collect();
    let mut cmd = Command::new(exe);
    cmd.args(args).stdin(std::process::Stdio::null());
    unsafe {
        cmd.pre_exec(|| {
            libc::setsid();
            Ok(())
        });
    }
    cmd.spawn().context("detach spawn")?;
    Ok(())
}

/// Entry point for `ksud hot-update`.
pub fn run(ko: Option<PathBuf>, package: Option<String>, detach: bool) -> Result<()> {
    if detach {
        return detach_self();
    }
    utils::switch_cgroups();
    fs::create_dir_all(WORK_DIR).ok();

    let package = package.unwrap_or_else(|| "com.nekosu".to_string());
    let kmi = crate::boot_patch::get_current_kmi().context("检测 KMI 失败")?;
    log_line(&format!("hot-update start kmi={kmi} package={package}"));

    let fallback = embedded_ko(&kmi).ok();
    if let Some(data) = &fallback {
        save_fallback(&kmi, data);
    }
    let new_ko = match &ko {
        Some(path) => read_ko(path)?,
        None => embedded_ko(&kmi)?,
    };
    log_line(&format!("hot-update new ko size={}", new_ko.len()));

    // The running module must have been loaded with allow_unload=1; otherwise
    // its sysfs kobject was deleted and delete_module() would panic.
    let unloadable = std::path::Path::new("/sys/module/kernelsu/parameters/allow_unload").exists();
    if !unloadable {
        bail!("当前内核模块不支持热卸载：请先在管理器开启「允许热更新」并重新加载一次内核模块（ksud late-load），之后再热更新");
    }
    let _ = fs::write(format!("{WORK_DIR}/hot_update"), b"1\n");

    // Stop userland consumers so the module refcount can reach zero.
    let _ = Command::new("am").args(["force-stop", &package]).status();
    let _ = webadmin_ksud::stop_running();
    kill_ksu_fd_holders();
    close_own_ksu_fds();
    std::thread::sleep(std::time::Duration::from_millis(300));

    unload_module()?;
    log_line("hot-update old module unloaded");

    utils::reset_std().ok();
    utils::umask(0);

    match load_ko(&new_ko, false) {
        Ok(()) => log_line("hot-update new module loaded"),
        Err(e) => {
            warn!("hot-update: load new KO failed: {e:#}");
            log_line(&format!("hot-update load new KO failed: {e:#}"));
            if let Some(data) = fallback {
                match load_ko(&data, false) {
                    Ok(()) => log_line("hot-update fallback module loaded"),
                    Err(e2) => {
                        log_line(&format!("hot-update fallback load failed: {e2:#}"));
                        return Err(e2.context("新 KO 和 fallback 都加载失败，需要重启"));
                    }
                }
            } else {
                return Err(e);
            }
        }
    }

    // Rebuild userspace state on top of the fresh module.
    if let Err(e) = utils::install(None, None) {
        warn!("hot-update: install ksud failed: {e:#}");
    }
    if let Err(e) = restorecon::restorecon() {
        warn!("hot-update: restorecon failed: {e:#}");
    }
    feature::init_forced_features();
    if let Err(e) = feature::init_features() {
        warn!("hot-update: init features failed: {e:#}");
    }
    if crate::android::susfs::integration::autoload_enabled() {
        if let Err(e) = crate::android::susfs::integration::load_if_autoload_enabled() {
            warn!("hot-update: SUSFS load failed: {e:#}");
        }
    }
    if let Err(e) = dynamic_manager::ensure_registered(&package) {
        warn!("hot-update: register dynamic manager failed: {e:#}");
    }
    init_event::run_stage("late-load", ScriptWait::Until(std::time::Instant::now() + std::time::Duration::from_secs(60)));

    restart_manager(&package);
    log_line("hot-update finished");
    Ok(())
}

/// Enable/disable the hot-update capability flag (next late-load will pass allow_unload=1).
pub fn set_capability(enabled: bool) -> Result<()> {
    fs::create_dir_all(WORK_DIR).ok();
    fs::write(format!("{WORK_DIR}/hot_update"), if enabled { "1\n" } else { "0\n" })
        .context("write hot_update flag")?;
    println!(
        "热更新能力已{}（下次加载内核模块生效）",
        if enabled { "开启" } else { "关闭" }
    );
    Ok(())
}
