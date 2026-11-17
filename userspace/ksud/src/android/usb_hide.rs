//! Per-caller spoof of the "USB debugging enabled" setting.
//!
//! The real `Settings.Global.adb_enabled` stays 1, so adb keeps working. The
//! kernel hook rewrites the binder parcel string "adb_enabled" to the same
//! length key "adb_spoofed" and this module seeds that key to 0.

use std::fs;
use std::process::Command;
use std::time::Duration;

use anyhow::{Context, Result};
use log::info;

const FLAG_PATH: &str = "/data/adb/ksu/usb_hide";
const SPOOF_KEY: &str = "adb_spoofed";

pub fn enabled() -> bool {
    fs::read_to_string(FLAG_PATH)
        .map(|v| v.trim() == "1")
        .unwrap_or(false)
}

fn set_flag(on: bool) -> Result<()> {
    fs::write(FLAG_PATH, if on { "1\n" } else { "0\n" }).context("write usb_hide flag")
}

fn sh(cmd: &str) -> bool {
    Command::new("sh").arg("-c").arg(cmd).status().map(|s| s.success()).unwrap_or(false)
}

fn seed() {
    let _ = sh(&format!("settings put global {SPOOF_KEY} 0"));
}

fn unseed() {
    let _ = sh(&format!("settings delete global {SPOOF_KEY}"));
}

pub fn enable() -> Result<()> {
    set_flag(true)?;
    seed();
    info!("usb_hide: enabled (app queries redirected to {SPOOF_KEY}=0)");
    Ok(())
}

pub fn disable() -> Result<()> {
    set_flag(false)?;
    unseed();
    info!("usb_hide: disabled");
    Ok(())
}

fn acquire_keeper_lock() -> Option<std::fs::File> {
    let f = std::fs::OpenOptions::new()
        .create(true)
        .write(true)
        .open("/data/adb/ksu/usb_hide_keeper.lock")
        .ok()?;
    let rc = unsafe { libc::flock(std::os::unix::io::AsRawFd::as_raw_fd(&f), libc::LOCK_EX | libc::LOCK_NB) };
    if rc == 0 {
        Some(f)
    } else {
        None
    }
}

fn kill_other_keepers() {
    let my_pid = std::process::id();
    let Ok(entries) = std::fs::read_dir("/proc") else { return };
    for entry in entries.flatten() {
        let Some(pid) = entry.file_name().to_str().and_then(|s| s.parse::<u32>().ok()) else {
            continue;
        };
        if pid == my_pid || pid <= 1 {
            continue;
        }
        let cmdline = std::fs::read(format!("/proc/{pid}/cmdline")).unwrap_or_default();
        let cmdline = String::from_utf8_lossy(&cmdline);
        if cmdline.contains("usb-hide") && cmdline.contains("keep") {
            unsafe { libc::kill(pid as i32, libc::SIGKILL) };
        }
    }
}

pub fn keep_loop() -> ! {
    let Some(_lock) = acquire_keeper_lock() else {
        // another keeper is already running
        std::process::exit(0);
    };
    crate::android::utils::enter_system_cgroup();
    loop {
        if enabled() {
            seed();
        }
        std::thread::sleep(Duration::from_secs(60));
    }
}

pub fn apply_on_boot() {
    if enabled() {
        seed();
        spawn_keeper();
    }
}

pub fn spawn_keeper() {
    if !enabled() {
        return;
    }
    kill_other_keepers();
    let Ok(exe) = std::env::current_exe() else { return };
    let mut cmd = Command::new(exe);
    cmd.args(["usb-hide", "keep"]).stdin(std::process::Stdio::null());
    unsafe {
        use std::os::unix::process::CommandExt;
        cmd.pre_exec(|| {
            libc::setsid();
            libc::prctl(libc::PR_SET_NAME, b"ksud\0".as_ptr() as libc::c_ulong, 0, 0, 0);
            Ok(())
        });
    }
    let _ = cmd.spawn();
}
