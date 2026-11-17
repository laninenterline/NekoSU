//! Minimal KPM integration state for NekoSU.
//!
//! 该 PoC 只持久化「是否启用 KPM 集成」的请求状态，并返回 `kpm.rs` 期望的
//! status JSON。内核侧 KPatch-Next 引擎尚未接入 NekoSU，因此引擎探测结果
//! `active` 恒为 false：
//! - `kpm integration enable` 由 `kpm.rs` 直接拒绝（`kernel_runtime_absent`）；
//! - 历史上已持久化的 enabled 状态：同一次开机内 `reboot_required = true`，
//!   重启后仍无引擎则 `transition_failed = true`（明确提示内核不支持）；
//! - `load`/`control`/`unload` 由 `kpm.rs` 返回明确的 `engine_inactive` 错误。
//!
//! 该文件替代上游 MilkSU 的完整 kpm_image.rs（启动镜像合成/引擎升级/回滚），
//! 只保留 kpm.rs 实际调用的四个入口：`configured_enabled`、`set_enabled`、
//! `runtime_status`、`transition_status`，以及状态目录初始化 `prepare`。
//!
//! # NekoSU 限制（重要）
//!
//! 上游的完整 kpm_image.rs 会用 kptools 在启动镜像的 kernel 中注入 kpimg，
//! 这是 KPatch 运行时（syscall 45 超级调用）真正存在的前提。NekoSU 当前没有：
//! - 构建/内置的 `kpimg` 与 Android `kptools` 资产；
//! - 启动镜像 kernel 打补丁的事务（原镜像备份、slot 状态、回滚、校验）；
//! - 与 NekoSU LKM 模式 `KSULKM` capsule 的兼容组合方案。
//!
//! 因此本模块只维护「集成请求」状态，status 会明确返回
//! `kernel_runtime_error = "kernel does not expose KPatch runtime"`；不要在
//! 未接入启动镜像补丁前把它当作可用引擎。详见
//! `vendor/kpatch-next/NEKOSU_INTEGRATION.md`。

use std::fs;
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, ensure};
use serde_json::{Value, json};

/// 与 kpm.rs 的 `STATE_ROOT` 保持一致。
const STATE_ROOT: &str = "/data/adb/ksu/kpm";
const STATE_FILE: &str = "integration.json";
const SCHEMA: u64 = 1;
const BOOT_ID_PATH: &str = "/proc/sys/kernel/random/boot_id";

/// NekoSU 内核没有 KPatch 运行时时对外的统一错误短语（CLI/status 共用）。
const KERNEL_RUNTIME_ERROR: &str = "kernel does not expose KPatch runtime";
const KERNEL_RUNTIME_DETAIL: &str =
    "NekoSU boots a stock/LKM kernel image without the KPatch (kpimg) payload; KPM requires a boot-image KPatch integration (see vendor/kpatch-next/NEKOSU_INTEGRATION.md)";

fn state_root() -> &'static Path {
    Path::new(STATE_ROOT)
}

fn state_path() -> PathBuf {
    state_root().join(STATE_FILE)
}

fn current_boot_id() -> Option<String> {
    let id = fs::read_to_string(BOOT_ID_PATH).ok()?;
    let id = id.trim();
    (!id.is_empty()).then(|| id.to_owned())
}

/// 创建 root-only 的 KPM 状态目录（与 registry 的权限保持一致）。
pub fn prepare() -> Result<()> {
    let root = state_root();
    fs::create_dir_all(root)?;
    ensure!(
        !fs::symlink_metadata(root)?.file_type().is_symlink(),
        "KPM state root must not be a symlink"
    );
    fs::set_permissions(root, fs::Permissions::from_mode(0o700))?;
    Ok(())
}

#[derive(Default)]
struct IntegrationState {
    enabled: bool,
    boot_id: Option<String>,
}

fn load_state() -> Result<IntegrationState> {
    let path = state_path();
    if !path.try_exists()? {
        return Ok(IntegrationState::default());
    }
    let bytes = fs::read(&path).context("Cannot read KPM integration state")?;
    ensure!(
        bytes.len() <= 16 * 1024,
        "KPM integration state exceeds 16 KiB"
    );
    let value: Value =
        serde_json::from_slice(&bytes).context("Cannot parse KPM integration state")?;
    ensure!(
        value.get("schema").and_then(Value::as_u64) == Some(SCHEMA),
        "Unsupported KPM integration state schema"
    );
    Ok(IntegrationState {
        enabled: value
            .get("enabled")
            .and_then(Value::as_bool)
            .unwrap_or(false),
        boot_id: value
            .get("boot_id")
            .and_then(Value::as_str)
            .map(str::to_owned),
    })
}

fn save_state(state: &IntegrationState) -> Result<()> {
    prepare()?;
    let value = json!({
        "schema": SCHEMA,
        "enabled": state.enabled,
        "boot_id": state.boot_id,
    });
    let mut temporary = tempfile::NamedTempFile::new_in(state_root())?;
    temporary
        .as_file()
        .set_permissions(fs::Permissions::from_mode(0o600))?;
    serde_json::to_writer(&mut temporary, &value)?;
    temporary.write_all(b"\n")?;
    temporary.as_file().sync_all()?;
    temporary.persist(state_path())?;
    fs::File::open(state_root())?.sync_all()?;
    Ok(())
}

/// 读取持久化的集成开关；文件不存在或不可解析时返回错误（由调用方决定如何上报）。
pub fn configured_enabled() -> Result<bool> {
    Ok(load_state()?.enabled)
}

fn status_value(state: &IntegrationState, active: bool) -> Value {
    // 同一次开机内启用但引擎尚未接管 -> 需要重启；重启后仍无引擎 -> 集成失败。
    let same_boot = match (&state.boot_id, current_boot_id()) {
        (Some(stored), Some(current)) => *stored == current,
        _ => false,
    };
    let reboot_required = if active {
        state.enabled != active
    } else {
        state.enabled && same_boot
    };
    let transition_failed = state.enabled && !active && !same_boot;
    let reason = if transition_failed {
        Some(
            "KPM integration is enabled but kernel does not expose KPatch runtime; a reboot cannot activate it on this build (see vendor/kpatch-next/NEKOSU_INTEGRATION.md)",
        )
    } else if reboot_required {
        Some(
            "KPM integration state changed; reboot to apply. Note: kernel does not expose KPatch runtime, so KPM loading remains unavailable",
        )
    } else if active {
        None
    } else {
        Some(
            "kernel does not expose KPatch runtime; KPM loading/control/unload are unavailable on this build (see vendor/kpatch-next/NEKOSU_INTEGRATION.md)",
        )
    };
    json!({
        "configured_enabled": state.enabled,
        "active": active,
        "reboot_required": reboot_required,
        "transition_failed": transition_failed,
        "can_configure": true,
        "can_enable": !state.enabled,
        "can_disable": state.enabled,
        "state_known": true,
        "integration_verified": true,
        "upgrade_available": false,
        "engine_sha256": Value::Null,
        "configuration_error": Value::Null,
        "kernel_runtime_exposed": false,
        "kernel_runtime_error": KERNEL_RUNTIME_ERROR,
        "kernel_runtime_detail": KERNEL_RUNTIME_DETAIL,
        "reason": reason,
    })
}

fn engine_active() -> bool {
    crate::kpm::engine_active()
}

/// 保存集成开关并返回最新状态（不写启动镜像，只改持久化开关）。
pub fn set_enabled(enabled: bool) -> Result<Value> {
    // 防御性检查：kpm.rs 的 Integration 命令已在更上层用带错误码的 KpmFailure
    // 拦截，这里保证任何直接调用者也不会在无运行时内核上写入「已启用」。
    if enabled {
        ensure!(
            engine_active(),
            "{}; KPM integration cannot be enabled without the KPatch runtime (see vendor/kpatch-next/NEKOSU_INTEGRATION.md)",
            KERNEL_RUNTIME_ERROR
        );
    }
    let state = IntegrationState {
        enabled,
        boot_id: current_boot_id(),
    };
    save_state(&state)?;
    Ok(status_value(&state, engine_active()))
}

/// `kpm status --runtime-only` 使用：只反映运行时状态。
pub fn runtime_status(active: bool) -> Result<Value> {
    Ok(status_value(&load_state()?, active))
}

/// `kpm status` 使用：完整状态（PoC 中与 runtime status 相同，不做镜像事务）。
pub fn transition_status(active: bool) -> Result<Value> {
    Ok(status_value(&load_state()?, active))
}
