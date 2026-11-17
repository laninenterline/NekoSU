#![allow(clippy::unreadable_literal)]
use anyhow::{Result, bail};

use std::{cell::Cell, fs, io, os::fd::RawFd, sync::OnceLock};

use crate::{android::uapi, defs::MountInfo};

// sigsys handler
std::thread_local! {
    #[allow(clippy::missing_const_for_thread_local)]
    static SVC_IN_FLIGHT: Cell<bool> = const { Cell::new(false) };
    #[allow(clippy::missing_const_for_thread_local)]
    static SIGSYS_OCCURRED: Cell<bool> = const { Cell::new(false) };
}

const SYS_SECCOMP: libc::c_int = 1;

fn with_svc_call<F, R>(call: F) -> R
where
    F: FnOnce() -> R,
{
    SVC_IN_FLIGHT.with(|in_flight| in_flight.set(true));
    let result = call();
    SVC_IN_FLIGHT.with(|in_flight| in_flight.set(false));
    result
}

fn take_sigsys_occurred() -> bool {
    SIGSYS_OCCURRED.with(|occurred| occurred.replace(false))
}

extern "C" fn sigsys_handler(
    _sig: libc::c_int,
    info: *mut libc::siginfo_t,
    ctx: *mut libc::c_void,
) {
    unsafe {
        if info.is_null() || ctx.is_null() || (*info).si_code != SYS_SECCOMP {
            return;
        }
        if SVC_IN_FLIGHT.with(Cell::get) {
            SIGSYS_OCCURRED.with(|occurred| occurred.set(true));
        }

        #[cfg(not(target_arch = "riscv64"))]
        let ucontext = ctx.cast::<libc::ucontext_t>();
        #[cfg(target_arch = "aarch64")]
        {
            (*ucontext).uc_mcontext.regs[0] = (-libc::EPERM) as u64;
        }
        #[cfg(target_arch = "arm")]
        {
            (*ucontext).uc_mcontext.arm_r0 = (-libc::EPERM) as u32;
        }
        #[cfg(target_arch = "x86_64")]
        {
            let rax = libc::REG_RAX as usize;
            (*ucontext).uc_mcontext.gregs[rax] = i64::from(-libc::EPERM);
        }
        #[cfg(target_arch = "riscv64")]
        {
            let ucontext = ctx.cast::<uapi::ucontext_t>();
            (*ucontext).uc_mcontext.__gregs[uapi::REG_A0 as usize] =
                (-libc::EPERM) as libc::c_ulong;
        }
    }
}

pub fn setup_sigsys_handler() {
    unsafe {
        let mut sa: libc::sigaction = std::mem::zeroed();
        sa.sa_flags = libc::SA_SIGINFO;
        sa.sa_sigaction = sigsys_handler as *const () as usize;
        libc::sigemptyset(std::ptr::addr_of_mut!(sa.sa_mask));
        if libc::sigaction(libc::SIGSYS, std::ptr::addr_of!(sa), std::ptr::null_mut()) != 0 {
            let error = std::io::Error::last_os_error();
            log::warn!("Failed to set SIGSYS handler: {error}");
        }
    }
}

const DRIVER_FD_NAME: &str = "anon_inode:[ksu_driver]";
const SU_DRIVER_FD_NAME: &str = "anon_inode:[ksu_driver_su]";

// Global driver fd cache
static DRIVER_FD: OnceLock<RawFd> = OnceLock::new();
static INFO_CACHE: OnceLock<uapi::ksu_get_info_cmd> = OnceLock::new();

fn scan_driver_fd() -> io::Result<Option<RawFd>> {
    let fd_dir = fs::read_dir("/proc/self/fd")?;
    let mut driver_fd = None;

    for entry in fd_dir.flatten() {
        if let Ok(fd_num) = entry.file_name().to_string_lossy().parse::<i32>() {
            let link_path = format!("/proc/self/fd/{fd_num}");
            if let Ok(target) = fs::read_link(&link_path) {
                let target_str = target.to_string_lossy();
                if target_str == SU_DRIVER_FD_NAME {
                    return Ok(Some(fd_num));
                }
                if target_str == DRIVER_FD_NAME {
                    driver_fd = Some(fd_num);
                }
            }
        }
    }

    Ok(driver_fd)
}

pub fn claim_inherited_driver_fd() -> io::Result<()> {
    if DRIVER_FD.get().is_none()
        && let Some(fd) = scan_driver_fd()?
    {
        let _ = DRIVER_FD.set(fd);
    }
    Ok(())
}

// Get cached driver fd
fn init_driver_fd() -> Option<RawFd> {
    let fd = scan_driver_fd().ok().flatten();
    if fd.is_none() {
        let mut fd = -1;
        with_svc_call(|| unsafe {
            libc::syscall(
                libc::SYS_reboot,
                uapi::KSU_INSTALL_MAGIC1_RUST,
                uapi::KSU_INSTALL_MAGIC2_RUST,
                0,
                &mut fd,
            )
        });
        if take_sigsys_occurred() {
            eprintln!("KernelSU driver install syscall was blocked by seccomp");
            log::error!("KernelSU driver install syscall was blocked by seccomp");
        }
        if fd >= 0 { Some(fd) } else { None }
    } else {
        fd
    }
}

// ioctl wrapper using libc
fn ksuctl<T>(request: u32, arg: *mut T) -> Result<i32> {
    let fd = *DRIVER_FD.get_or_init(|| init_driver_fd().unwrap_or(-1));
    if fd < 0 {
        bail!("could not retrieve kernelsu driver fd")
    }
    unsafe {
        let ret = libc::ioctl(fd as libc::c_int, request as i32, arg);
        if ret < 0 {
            bail!("ioctl failed: {}", io::Error::last_os_error());
        }
        Ok(ret)
    }
}

// API implementations
pub fn get_info() -> uapi::ksu_get_info_cmd {
    *INFO_CACHE.get_or_init(|| {
        let mut cmd = uapi::ksu_get_info_cmd {
            version: 0,
            flags: 0,
            features: 0,
            uapi_version: 0,
        };

        if ksuctl(uapi::KSU_IOCTL_GET_INFO, &raw mut cmd).is_err() {
            let _ = ksuctl(uapi::KSU_IOCTL_GET_INFO_LEGACY, &raw mut cmd);
        }
        cmd
    })
}

pub fn get_version() -> i32 {
    get_info().version as i32
}

pub fn is_late_load() -> bool {
    get_info().flags & uapi::KSU_GET_INFO_FLAG_LATE_LOAD_RUST != 0
}

pub fn is_lkm() -> bool {
    get_info().flags & uapi::KSU_GET_INFO_FLAG_LKM != 0
}

/// Whether the kernel runs module preinit actions on boot.
pub fn is_preinit_supported() -> bool {
    get_info().flags & uapi::KSU_GET_INFO_FLAG_PREINIT != 0
}

pub const fn uapi_version() -> u32 {
    uapi::KERNEL_SU_UAPI_VERSION
}

pub fn runtime_mode() -> &'static str {
    if is_late_load() {
        "late-load"
    } else if is_lkm() {
        "lkm"
    } else {
        "built-in"
    }
}

pub fn ensure_uapi_version_matched() -> anyhow::Result<()> {
    let kernel_uapi = get_info().uapi_version;
    let userspace_uapi = uapi_version();
    if kernel_uapi != userspace_uapi {
        bail!(
            "UAPI version mismatch: kernel={kernel_uapi}, ksud={userspace_uapi}. Please update KernelSU!"
        );
    }
    Ok(())
}

pub fn get_full_version() -> String {
    let mut cmd = uapi::ksu_get_full_version_cmd {
        version_full: [0; 255],
    };

    let _ = ksuctl(uapi::KSU_IOCTL_GET_FULL_VERSION_RUST, &raw mut cmd);

    let mut buff = [0u8; 256];

    unsafe {
        let src_ptr = cmd.version_full.as_ptr().cast::<u8>();
        let dst_ptr = buff.as_mut_ptr();
        std::ptr::copy_nonoverlapping(src_ptr, dst_ptr, 255);
    }

    buff[255] = 0;

    unsafe {
        std::ffi::CStr::from_ptr(buff.as_ptr() as *const std::os::raw::c_char)
            .to_string_lossy()
            .into_owned()
    }
}

pub fn grant_root() -> Result<()> {
    ksuctl(uapi::KSU_IOCTL_GRANT_ROOT_RUST, std::ptr::null_mut::<u8>())?;
    Ok(())
}

fn report_event(event: u32) {
    let mut cmd = uapi::ksu_report_event_cmd { event };
    let _ = ksuctl(uapi::KSU_IOCTL_REPORT_EVENT_RUST, &raw mut cmd);
}

pub fn report_post_fs_data() {
    report_event(uapi::EVENT_POST_FS_DATA_RUST);
}

pub fn report_services() -> Result<bool> {
    let mut cmd = uapi::ksu_report_event_cmd {
        event: uapi::EVENT_SERVICES,
    };
    Ok(ksuctl(uapi::KSU_IOCTL_REPORT_EVENT, &raw mut cmd)? == 1)
}

pub fn report_boot_complete() {
    report_event(uapi::EVENT_BOOT_COMPLETED_RUST);
}

pub fn report_module_mounted() {
    report_event(uapi::EVENT_MODULE_MOUNTED_RUST);
}

pub fn check_kernel_safemode() -> bool {
    let mut cmd = uapi::ksu_check_safemode_cmd { in_safe_mode: 0 };
    let _ = ksuctl(uapi::KSU_IOCTL_CHECK_SAFEMODE_RUST, &raw mut cmd);
    cmd.in_safe_mode != 0
}

pub fn set_sepolicy(payload: *const u8, payload_len: u64) -> Result<i32> {
    let mut ioctl_cmd = uapi::ksu_set_sepolicy_cmd {
        data_len: payload_len,
        data: payload as u64,
    };

    ksuctl(uapi::KSU_IOCTL_SET_SEPOLICY_RUST, &raw mut ioctl_cmd)
}

#[derive(Debug, Clone, Copy, Default)]
pub struct FeatureState {
    pub value: u64,
    pub supported: bool,
    /// Forced by boot config (ksu_config), the kernel refuses to change it
    pub forced: bool,
}

/// Get feature value and support status from kernel
pub fn get_feature(feature_id: u32) -> Result<FeatureState> {
    let mut cmd = uapi::ksu_get_feature_cmd {
        feature_id,
        value: 0,
        supported: 0,
    };
    ksuctl(uapi::KSU_IOCTL_GET_FEATURE, &raw mut cmd)?;
    let flags = u32::from(cmd.supported);
    Ok(FeatureState {
        value: cmd.value,
        // older kernels report 1 for supported, matching the flag
        supported: flags & uapi::KSU_FEATURE_FLAG_SUPPORTED != 0,
        forced: flags & uapi::KSU_FEATURE_FLAG_FORCED != 0,
    })
}

/// Set feature value in kernel
pub fn set_feature(feature_id: u32, value: u64) -> Result<()> {
    let mut cmd = uapi::ksu_set_feature_cmd { feature_id, value };
    ksuctl(uapi::KSU_IOCTL_SET_FEATURE_RUST, &raw mut cmd)?;
    Ok(())
}

pub fn get_wrapped_fd(fd: RawFd) -> Result<RawFd> {
    let mut cmd = uapi::ksu_get_wrapper_fd_cmd {
        fd: fd as u32,
        flags: 0,
    };
    let result = ksuctl(uapi::KSU_IOCTL_GET_WRAPPER_FD_RUST, &raw mut cmd)?;
    Ok(result)
}

pub fn get_sulog_fd() -> Result<RawFd> {
    let mut cmd = uapi::ksu_get_sulog_fd_cmd { flags: 0 };
    let result = ksuctl(uapi::KSU_IOCTL_GET_SULOG_FD, &raw mut cmd)?;
    Ok(result)
}

/// Get mark status for a process (pid=0 returns total marked count)
pub fn mark_get(pid: i32) -> Result<u32> {
    let mut cmd = uapi::ksu_manage_mark_cmd {
        operation: uapi::KSU_MARK_GET_RUST,
        pid,
        result: 0,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(cmd.result)
}

/// Mark a process (pid=0 marks all processes)
pub fn mark_set(pid: i32) -> Result<()> {
    let mut cmd = uapi::ksu_manage_mark_cmd {
        operation: uapi::KSU_MARK_MARK_RUST,
        pid,
        result: 0,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(())
}

/// Unmark a process (pid=0 unmarks all processes)
pub fn mark_unset(pid: i32) -> Result<()> {
    let mut cmd = uapi::ksu_manage_mark_cmd {
        operation: uapi::KSU_MARK_UNMARK_RUST,
        pid,
        result: 0,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(())
}

/// Refresh mark for all running processes
pub fn mark_refresh() -> Result<()> {
    let mut cmd = uapi::ksu_manage_mark_cmd {
        operation: uapi::KSU_MARK_REFRESH_RUST,
        pid: 0,
        result: 0,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_MARK_RUST, &raw mut cmd)?;
    Ok(())
}

pub fn nuke_ext4_sysfs(mnt: &str) -> anyhow::Result<()> {
    let c_mnt = std::ffi::CString::new(mnt)?;
    let mut ioctl_cmd = uapi::ksu_nuke_ext4_sysfs_cmd {
        arg: c_mnt.as_ptr() as u64,
    };
    ksuctl(uapi::KSU_IOCTL_NUKE_EXT4_SYSFS_RUST, &raw mut ioctl_cmd)?;
    Ok(())
}

/// Wipe all entries from umount list
pub fn umount_list_wipe() -> Result<()> {
    let mut cmd = uapi::ksu_manage_try_umount_cmd {
        arg: 0,
        flags: 0,
        mode: uapi::KSU_UMOUNT_WIPE_RUST,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut cmd)?;
    Ok(())
}

/// Add mount point to umount list
pub fn umount_list_add(path: &str, flags: u32) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = uapi::ksu_manage_try_umount_cmd {
        arg: c_path.as_ptr() as u64,
        flags,
        mode: uapi::KSU_UMOUNT_ADD_RUST,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut cmd)?;
    Ok(())
}

/// Delete mount point from umount list
pub fn umount_list_del(path: &str) -> anyhow::Result<()> {
    let c_path = std::ffi::CString::new(path)?;
    let mut cmd = uapi::ksu_manage_try_umount_cmd {
        arg: c_path.as_ptr() as u64,
        flags: 0,
        mode: uapi::KSU_UMOUNT_DEL_RUST,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut cmd)?;
    Ok(())
}

/// Set current process's process group to init_group (pgid = 0)
pub fn set_init_pgrp() -> Result<()> {
    ksuctl(
        uapi::KSU_IOCTL_SET_INIT_PGRP_RUST,
        std::ptr::null_mut::<u8>(),
    )?;
    Ok(())
}

/// Look up the addresses of kernel symbols, exported or not.
///
/// Returns one entry per name, `None` when the running kernel has no such
/// symbol.
pub fn lookup_symbols(names: &[&str]) -> Result<Vec<Option<u64>>> {
    let mut result = Vec::with_capacity(names.len());
    for chunk in names.chunks(uapi::KSU_LOOKUP_SYMBOLS_MAX as usize) {
        let c_names = chunk
            .iter()
            .map(|name| std::ffi::CString::new(*name))
            .collect::<Result<Vec<_>, _>>()?;
        let ptrs: Vec<u64> = c_names.iter().map(|name| name.as_ptr() as u64).collect();
        let mut addrs = vec![0u64; chunk.len()];
        let mut cmd = uapi::ksu_lookup_symbols_cmd {
            names: ptrs.as_ptr() as u64,
            addrs: addrs.as_mut_ptr() as u64,
            count: chunk.len() as u32,
            flags: 0,
        };
        ksuctl(uapi::KSU_IOCTL_LOOKUP_SYMBOLS, &raw mut cmd)?;
        result.extend(addrs.into_iter().map(|addr| (addr != 0).then_some(addr)));
    }
    Ok(result)
}

pub fn set_ksu_no_new_privs() -> anyhow::Result<()> {
    let result = ksuctl(
        uapi::KSU_IOCTL_DISABLE_ESCAPE_TO_ROOT,
        std::ptr::null_mut::<u8>(),
    )?;
    if result != 0 {
        bail!("unexpected result: {result}");
    }
    Ok(())
}

// downstream begin

pub fn dynamic_manager_set(size: u32, hash: [u8; 64]) -> anyhow::Result<()> {
    let mut cmd = uapi::ksu_dynamic_manager_cmd {
        operation: uapi::DYNAMIC_MANAGER_OP_SET_RUST,
        size,
        hash,
    };
    ksuctl(uapi::KSU_IOCTL_DYNAMIC_MANAGER_RUST, &raw mut cmd)?;
    Ok(())
}

pub fn dynamic_manager_set_synchronous(size: u32, hash: [u8; 64]) -> anyhow::Result<()> {
    let mut cmd = uapi::ksu_dynamic_manager_cmd {
        operation: uapi::DYNAMIC_MANAGER_OP_SET_SYNCHRONOUS_RUST,
        size,
        hash,
    };
    ksuctl(uapi::KSU_IOCTL_DYNAMIC_MANAGER_RUST, &raw mut cmd)?;
    Ok(())
}

pub fn dynamic_manager_get() -> anyhow::Result<(u32, [u8; 64])> {
    let mut cmd = uapi::ksu_dynamic_manager_cmd {
        operation: uapi::DYNAMIC_MANAGER_OP_GET_RUST,
        size: 0,
        hash: [0u8; 64],
    };
    ksuctl(uapi::KSU_IOCTL_DYNAMIC_MANAGER_RUST, &raw mut cmd)?;
    Ok((cmd.size, cmd.hash))
}

pub fn dynamic_manager_clear() -> anyhow::Result<()> {
    let mut cmd = uapi::ksu_dynamic_manager_cmd {
        operation: uapi::DYNAMIC_MANAGER_OP_WIPE_RUST,
        size: 0,
        hash: [0u8; 64],
    };
    ksuctl(uapi::KSU_IOCTL_DYNAMIC_MANAGER_RUST, &raw mut cmd)?;
    Ok(())
}

/// Ask the kernel to rescan packages.list and the hidden manager identity record.
pub fn refresh_manager() -> anyhow::Result<()> {
    ksuctl(
        uapi::KSU_IOCTL_REFRESH_MANAGER_RUST,
        std::ptr::null_mut::<u8>(),
    )?;
    Ok(())
}

/// List all mount points in umount list
pub fn umount_list_list() -> anyhow::Result<Vec<MountInfo>> {
    const FLAGS_SIZE: usize = std::mem::size_of::<u32>();
    let mut total_size: usize = 0;
    let mut size_cmd = uapi::ksu_manage_try_umount_cmd {
        arg: &raw mut total_size as u64,
        flags: 0,
        mode: uapi::KSU_UMOUNT_GETSIZE_NEW_RUST,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut size_cmd)?;

    if total_size == 0 {
        return Ok(vec![]);
    }

    let mut buffer = vec![0u8; total_size];
    let mut list_cmd = uapi::ksu_manage_try_umount_cmd {
        arg: buffer.as_mut_ptr() as u64,
        flags: 0,
        mode: uapi::KSU_UMOUNT_GETLIST_NEW_RUST,
    };
    ksuctl(uapi::KSU_IOCTL_MANAGE_TRY_UMOUNT_RUST, &raw mut list_cmd)?;

    let mut list = Vec::new();
    let mut cursor = 0;
    let len = buffer.len();

    while cursor < len {
        use anyhow::Context;
        let null_pos = buffer[cursor..]
            .iter()
            .position(|&b| b == 0)
            .context("Malformed buffer: missing null terminator")?;

        let end_str = cursor + null_pos;

        let path = String::from_utf8_lossy(&buffer[cursor..end_str]).into_owned();

        cursor = end_str + 1;

        if cursor + 4 > len {
            break;
        }

        let flags_bytes: [u8; FLAGS_SIZE] = buffer[cursor..cursor + FLAGS_SIZE]
            .try_into()
            .expect("Slice length matches");

        let flags = u32::from_ne_bytes(flags_bytes);

        cursor += FLAGS_SIZE;

        list.push(MountInfo { path, flags });
    }

    Ok(list)
}

// ── WebAdmin support: allow/deny lists, manager appid, app profile, stealth ──
// Ported from 7kimisu's ksud (manager-privileged ioctls are executed in a forked
// child that setresuid()s to the manager uid and rescans the driver fd).

pub fn allow_list_uids() -> Vec<u32> {
    list_uids(true)
}

pub fn deny_list_uids() -> Vec<u32> {
    list_uids(false)
}

fn list_uids(allow: bool) -> Vec<u32> {
    let req = if allow {
        uapi::KSU_IOCTL_NEW_GET_ALLOW_LIST
    } else {
        uapi::KSU_IOCTL_NEW_GET_DENY_LIST
    };
    let mut probe: uapi::ksu_new_get_allow_list_cmd = unsafe { std::mem::zeroed() };
    probe.count = 0;
    if ksuctl(req, &raw mut probe).is_err() {
        return Vec::new();
    }
    let total = (probe.total_count as usize).min(4096);
    if total == 0 {
        return Vec::new();
    }
    let base = std::mem::size_of::<uapi::ksu_new_get_allow_list_cmd>();
    let need = base + total * std::mem::size_of::<u32>();
    let mut buf: Vec<u32> = vec![0; need.div_ceil(std::mem::size_of::<u32>())];
    let cmd = buf.as_mut_ptr().cast::<uapi::ksu_new_get_allow_list_cmd>();
    unsafe {
        (*cmd).count = total as u16;
        (*cmd).total_count = 0;
    }
    if ksuctl(req, cmd).is_err() {
        return Vec::new();
    }
    let n = unsafe { (*cmd).count as usize }.min(total);
    let ptr = unsafe { (*cmd).uids.as_ptr() };
    unsafe { std::slice::from_raw_parts(ptr, n) }.to_vec()
}

pub fn manager_appid() -> Result<u32> {
    let mut cmd: uapi::ksu_get_manager_appid_cmd = unsafe { std::mem::zeroed() };
    ksuctl(uapi::KSU_IOCTL_GET_MANAGER_APPID, &raw mut cmd)?;
    Ok(cmd.appid)
}

pub fn write_cstr(buf: &mut [libc::c_char], s: &str) {
    let n = s.len().min(buf.len().saturating_sub(1));
    for (i, b) in s.as_bytes()[..n].iter().enumerate() {
        buf[i] = *b as libc::c_char;
    }
    if let Some(last) = buf.get_mut(n) {
        *last = 0;
    }
}

pub fn read_cstr(buf: &[libc::c_char]) -> String {
    let bytes: Vec<u8> = buf.iter().take_while(|c| **c != 0).map(|c| *c as u8).collect();
    String::from_utf8_lossy(&bytes).to_string()
}

const MANAGER_IOCTL_MAX: usize = 4096;

fn ioctl_as_manager(req: u32, input: &[u8]) -> Result<Option<Vec<u8>>> {
    anyhow::ensure!(input.len() <= MANAGER_IOCTL_MAX, "入参结构体太大");
    let appid = manager_appid()?;
    if appid == u32::MAX || appid == 0 {
        bail!("拿不到管理器 appid");
    }
    let inherited = DRIVER_FD.get().copied().unwrap_or(-1);
    let len = input.len();
    let mut fds = [0i32; 2];
    unsafe {
        if libc::pipe(fds.as_mut_ptr()) != 0 {
            bail!("pipe 失败: {}", io::Error::last_os_error());
        }
    }
    let [rd, wr] = fds;
    let mut out = vec![0u8; len];
    unsafe {
        let pid = libc::fork();
        if pid == 0 {
            libc::close(rd);
            let mut buf = [0u8; MANAGER_IOCTL_MAX];
            std::ptr::copy_nonoverlapping(input.as_ptr(), buf.as_mut_ptr(), len);
            if libc::setresuid(appid, appid, appid) != 0 {
                libc::_exit(10);
            }
            if inherited >= 0 {
                libc::close(inherited);
            }
            let Ok(Some(fd)) = scan_driver_fd() else { libc::_exit(11) };
            let ret = libc::ioctl(fd, req as libc::c_int, buf.as_mut_ptr());
            if ret != 0 {
                let errno = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                libc::_exit(if errno == libc::ENOENT { 20 } else { 12 });
            }
            let mut off = 0usize;
            while off < len {
                let n = libc::write(wr, buf.as_ptr().add(off).cast::<libc::c_void>(), len - off);
                if n <= 0 {
                    libc::_exit(13);
                }
                off += n as usize;
            }
            libc::close(wr);
            libc::_exit(0);
        } else if pid < 0 {
            libc::close(rd);
            libc::close(wr);
            bail!("fork 失败: {}", io::Error::last_os_error());
        }
        libc::close(wr);
        let mut off = 0usize;
        while off < len {
            let n = libc::read(rd, out.as_mut_ptr().add(off).cast::<libc::c_void>(), len - off);
            if n <= 0 {
                break;
            }
            off += n as usize;
        }
        libc::close(rd);
        let mut status: libc::c_int = 0;
        libc::waitpid(pid, &raw mut status, 0);
        if !libc::WIFEXITED(status) {
            bail!("管理器身份子进程异常退出");
        }
        match libc::WEXITSTATUS(status) {
            0 => {}
            20 => return Ok(None),
            c => bail!("ioctl 失败（子进程退出码 {c}）"),
        }
        anyhow::ensure!(off == len, "ioctl 结果回传不完整（{off}/{len}）");
    }
    Ok(Some(out))
}

fn struct_bytes<T>(v: &T) -> &[u8] {
    let p = std::ptr::from_ref(v).cast::<u8>();
    unsafe { std::slice::from_raw_parts(p, std::mem::size_of::<T>()) }
}

fn struct_from_bytes<T: Copy>(b: &[u8]) -> Result<T> {
    anyhow::ensure!(b.len() >= std::mem::size_of::<T>(), "结构体字节数不足");
    let mut v: T = unsafe { std::mem::zeroed() };
    unsafe {
        std::ptr::copy_nonoverlapping(
            b.as_ptr(),
            std::ptr::addr_of_mut!(v).cast::<u8>(),
            std::mem::size_of::<T>(),
        );
    }
    Ok(v)
}

pub fn get_app_profile(uid: u32, key: &str) -> Result<Option<uapi::app_profile>> {
    let mut cmd: uapi::ksu_get_app_profile_cmd = unsafe { std::mem::zeroed() };
    cmd.profile.version = uapi::KSU_APP_PROFILE_VER;
    cmd.profile.curr_uid = uid as i32;
    write_cstr(&mut cmd.profile.key[..], key);
    match ioctl_as_manager(uapi::KSU_IOCTL_GET_APP_PROFILE, struct_bytes(&cmd))? {
        None => Ok(None),
        Some(out) => {
            let got: uapi::ksu_get_app_profile_cmd = struct_from_bytes(&out)?;
            Ok(Some(got.profile))
        }
    }
}

pub fn set_app_profile(profile: &uapi::app_profile) -> Result<()> {
    let mut cmd: uapi::ksu_set_app_profile_cmd = unsafe { std::mem::zeroed() };
    cmd.profile = *profile;
    match ioctl_as_manager(uapi::KSU_IOCTL_SET_APP_PROFILE, struct_bytes(&cmd))? {
        Some(_) => Ok(()),
        None => bail!("内核说没有这个 profile"),
    }
}

pub fn stealth_get_authed() -> Result<bool> {
    let out = ioctl_as_manager(uapi::KSU_IOCTL_STEALTH_GET, &[0u8; 1])?
        .ok_or_else(|| anyhow::anyhow!("stealth: 无返回"))?;
    Ok(out.first().copied().unwrap_or(0) != 0)
}

pub fn stealth_set_authed(enabled: bool) -> Result<()> {
    let input = [u8::from(enabled)];
    ioctl_as_manager(uapi::KSU_IOCTL_STEALTH_SET, &input)?
        .ok_or_else(|| anyhow::anyhow!("stealth: 无返回"))?;
    Ok(())
}
