//! Keep uid-0 userspace processes inside the system cgroup tree.
//!
//! Detectors enumerate /proc, read each process' uid + cgroup + ppid and flag
//! groups whose cgroup path does not match: a root process sitting in `/` or in
//! an app's cgroup (`/apps/uid_10370`) is exactly that. ZygiskSU, LSPosed and
//! Yuehong helpers all live in `/`, so a small resident normalizer moves them to
//! `/system/uid_0` a couple of seconds after they spawn.

use std::fs;
use std::io::Write;
use std::time::Duration;

const LOCK: &str = "/data/adb/ksu/cgroup_norm.lock";

fn move_pid(pid: u32) {
    let s = pid.to_string();
    for path in [
        "/sys/fs/cgroup/system/uid_0/cgroup.procs",
        "/dev/memcg/apps/in_active_bg/cgroup.procs",
        "/dev/cpuset/foreground/tasks",
    ] {
        if let Ok(mut f) = fs::OpenOptions::new().write(true).open(path) {
            let _ = f.write_all(s.as_bytes());
        }
    }
}

pub fn normalize_once() {
    let Ok(entries) = fs::read_dir("/proc") else { return };
    for entry in entries.flatten() {
        let Some(pid) = entry.file_name().to_str().and_then(|s| s.parse::<u32>().ok()) else {
            continue;
        };
        if pid <= 1 {
            continue;
        }
        let status = fs::read_to_string(format!("/proc/{pid}/status")).unwrap_or_default();
        let uid = status
            .lines()
            .find(|l| l.starts_with("Uid:"))
            .and_then(|l| l.split_whitespace().nth(1))
            .and_then(|v| v.parse::<u32>().ok());
        let ppid = status
            .lines()
            .find(|l| l.starts_with("PPid:"))
            .and_then(|l| l.split_whitespace().nth(1))
            .and_then(|v| v.parse::<u32>().ok());
        if uid != Some(0) || ppid == Some(2) {
            continue;
        }
        let comm = fs::read_to_string(format!("/proc/{pid}/comm")).unwrap_or_default();
        if comm.trim() == "init" || comm.contains('/') {
            continue;
        }
        let cgroup = fs::read_to_string(format!("/proc/{pid}/cgroup")).unwrap_or_default();
        let v2 = cgroup
            .lines()
            .find(|l| l.starts_with("0::"))
            .map(|l| l.splitn(3, ':').nth(2).unwrap_or("").to_string())
            .unwrap_or_default();
        if !v2.starts_with("/system/uid_0") {
            move_pid(pid);
        }
    }
}

pub fn spawn_daemon() {
    let Ok(exe) = std::env::current_exe() else { return };
    let mut cmd = std::process::Command::new(exe);
    cmd.arg("cgroup-norm").stdin(std::process::Stdio::null());
    unsafe {
        use std::os::unix::process::CommandExt;
        cmd.pre_exec(|| {
            libc::setsid();
            libc::prctl(libc::PR_SET_NAME, b"cgroup-norm\0".as_ptr() as libc::c_ulong, 0, 0, 0);
            Ok(())
        });
    }
    let _ = cmd.spawn();
}

pub fn run_loop() -> ! {
    let lock = fs::OpenOptions::new().create(true).write(true).open(LOCK).ok();
    if let Some(f) = &lock {
        let rc = unsafe {
            libc::flock(std::os::unix::io::AsRawFd::as_raw_fd(f), libc::LOCK_EX | libc::LOCK_NB)
        };
        if rc != 0 {
            std::process::exit(0);
        }
    }
    loop {
        normalize_once();
        std::thread::sleep(Duration::from_secs(2));
    }
}
