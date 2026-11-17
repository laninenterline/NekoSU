//! Native KPM commands. Boot image composition lives in `crate::kpm_image`.
//!
//! NekoSU does not embed the KPatch runtime payload (`kpimg`) in its kernel image
//! and has no kernel-side KPatch hooks, so the supercall transport is absent:
//! `status`/`list`/`import`/`info`/`remove`/`autoload` and the integration journal
//! work, while `load`/`control`/`unload` and `integration enable` fail with the
//! explicit `engine_inactive` / `kernel_runtime_absent` errors below.
//! See `vendor/kpatch-next/NEKOSU_INTEGRATION.md` for the full analysis.

#[path = "kpm_elf.rs"]
mod elf;
#[path = "kpm_runtime.rs"]
mod runtime;
#[path = "kpm_store.rs"]
mod store;

use std::fmt;
use std::io::Write;
use std::path::{Path, PathBuf};

use anyhow::{Result, ensure};
use clap::Subcommand;
use serde_json::{Value, json};

use runtime::{Engine, NativeEngine, Probe, RuntimeModule};
use store::{Record, Store};

const STATE_ROOT: &str = "/data/adb/ksu/kpm";

#[derive(Debug, Subcommand)]
pub enum KpmCommand {
    Status {
        #[arg(long)]
        runtime_only: bool,
    },
    List,
    Import {
        path: PathBuf,
    },
    Info {
        id: String,
    },
    Load {
        id: String,
        #[arg(long, default_value = "")]
        args: String,
    },
    Control {
        id: String,
        #[arg(long)]
        args: String,
    },
    Unload {
        id: String,
    },
    Remove {
        id: String,
    },
    Autoload {
        id: String,
        #[command(subcommand)]
        action: KpmToggle,
    },
    Integration {
        #[command(subcommand)]
        action: KpmToggle,
    },
}

#[derive(Clone, Copy, Debug, Subcommand)]
pub enum KpmToggle {
    Enable,
    Disable,
}

impl KpmToggle {
    const fn enabled(self) -> bool {
        matches!(self, Self::Enable)
    }
}

#[derive(Debug)]
struct KpmFailure {
    code: &'static str,
    message: String,
}

impl fmt::Display for KpmFailure {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.message)
    }
}

impl std::error::Error for KpmFailure {}

fn failure(code: &'static str, message: impl Into<String>) -> anyhow::Error {
    KpmFailure {
        code,
        message: message.into(),
    }
    .into()
}

fn error_value(error: &anyhow::Error) -> Value {
    let code = error
        .downcast_ref::<KpmFailure>()
        .map_or("operation_failed", |error| error.code);
    json!({ "code": code, "message": format!("{error:#}") })
}

fn envelope(result: &Result<Value>) -> Value {
    match result {
        Ok(data) => json!({ "ok": true, "data": data, "error": null }),
        Err(error) => json!({ "ok": false, "data": null, "error": error_value(error) }),
    }
}

fn require_root() -> Result<()> {
    ensure!(
        unsafe { libc::getuid() } == 0,
        failure("root_required", "KPM commands require UID 0")
    );
    Ok(())
}

fn boot_id() -> Result<String> {
    let id = std::fs::read_to_string("/proc/sys/kernel/random/boot_id")?;
    let id = id.trim();
    ensure!(
        !id.is_empty(),
        failure(
            "boot_identity_unavailable",
            "Cannot identify the current boot"
        )
    );
    Ok(id.to_owned())
}

fn require_engine(probe: &Probe) -> Result<()> {
    ensure!(
        probe.active,
        failure(
            "engine_inactive",
            "kernel does not expose KPatch runtime; KPM load/control/unload are unavailable on this build"
        )
    );
    ensure!(
        probe.managed(),
        failure(
            "incompatible_engine",
            "Engine lacks NekoSU KPM loader safety capabilities; update and reboot"
        )
    );
    Ok(())
}

/// Probe only the native kernel transport, without reading image state.
#[must_use]
pub fn engine_active() -> bool {
    NativeEngine.probe().active
}

#[must_use]
pub fn engine_managed() -> bool {
    NativeEngine.probe().managed()
}

#[must_use]
pub fn avc_conflict() -> bool {
    NativeEngine.probe().avc_conflict()
}

fn status(runtime_only: bool) -> Result<Value> {
    let probe = NativeEngine.probe();
    let root = unsafe { libc::getuid() } == 0;
    let transition = if root {
        if runtime_only {
            crate::kpm_image::runtime_status(probe.active)?
        } else {
            crate::kpm_image::transition_status(probe.active)?
        }
    } else {
        // The state directory is root-only; report unavailable without treating
        // its expected EACCES as a failed integration transition.
        json!({
            "configured_enabled": false,
            "reboot_required": false,
            "transition_failed": false,
            "reason": null,
        })
    };
    status_value(
        &probe,
        &transition,
        cfg!(all(target_os = "android", target_arch = "aarch64")),
        root,
    )
}

fn status_value(
    probe: &Probe,
    transition: &Value,
    arm64_android: bool,
    root: bool,
) -> Result<Value> {
    let configured = transition
        .get("configured_enabled")
        .and_then(Value::as_bool)
        .ok_or_else(|| {
            failure(
                "invalid_transition",
                "Integration state has no configured_enabled flag",
            )
        })?;
    let reboot_required = transition
        .get("reboot_required")
        .and_then(Value::as_bool)
        .ok_or_else(|| {
            failure(
                "invalid_transition",
                "Integration state has no reboot_required flag",
            )
        })?;
    let unmanaged = probe.active && !probe.managed();
    let supported = arm64_android && root && !unmanaged;
    let runtime_supported = arm64_android && root && probe.managed();
    let can_configure = arm64_android
        && root
        && transition
            .get("can_configure")
            .and_then(Value::as_bool)
            .unwrap_or(false);
    let reason = if !arm64_android {
        Some("The bundled KPM engine requires an ARM64 kernel")
    } else if !root {
        Some("Root access is required to probe the KPM engine")
    } else if let Some(reason) = transition.get("reason").and_then(Value::as_str) {
        Some(reason)
    } else if let Some(error) = probe.error.as_deref() {
        Some(error)
    } else if unmanaged {
        Some(if can_configure {
            "The running KPM engine lacks required safety capabilities; upgrade the verified integration and reboot"
        } else {
            "The running KPM engine lacks required safety capabilities and has no verified upgrade image"
        })
    } else if !probe.active {
        Some(
            "kernel does not expose KPatch runtime; KPM load/control/unload are unavailable (see vendor/kpatch-next/NEKOSU_INTEGRATION.md)",
        )
    } else {
        None
    };
    Ok(json!({
        "supported": supported,
        "runtime_supported": runtime_supported,
        "can_unload": probe.can_unload(),
        "integration_verified": transition.get("integration_verified").and_then(Value::as_bool).unwrap_or(true),
        "can_configure": can_configure,
        "can_enable": arm64_android && root && transition.get("can_enable").and_then(Value::as_bool).unwrap_or(can_configure),
        "can_disable": arm64_android && root && transition.get("can_disable").and_then(Value::as_bool).unwrap_or(can_configure),
        "configured_enabled": configured,
        "active": probe.active,
        "reboot_required": reboot_required,
        "transition_failed": transition.get("transition_failed").and_then(Value::as_bool).unwrap_or(false),
        "provider": if probe.active && probe.error.is_some() { "unknown" } else if probe.upstream { "kpatch-next" } else if unmanaged { "incompatible" } else if probe.managed() { "nekosu" } else { "none" },
        "state_known": root && transition.get("state_known").and_then(Value::as_bool).unwrap_or(true),
        "version": probe.version,
        "capabilities": probe.capabilities,
        "required_capabilities": runtime::REQUIRED_CAPABILITIES,
        "probe_error": probe.error,
        "configuration_error": transition.get("configuration_error"),
        "upgrade_available": transition.get("upgrade_available").and_then(Value::as_bool).unwrap_or(false),
        "engine_sha256": transition.get("engine_sha256"),
        "runtime_exposed": probe.active,
        "runtime_error": if probe.active { Value::Null } else { Value::String("kernel does not expose KPatch runtime".into()) },
        "reason": reason,
    }))
}

fn runtime_id(name: &str) -> String {
    format!("runtime-{}", sha256::digest(name))
}

fn runtime_value(module: &RuntimeModule) -> Value {
    json!({
        "id": runtime_id(&module.name),
        "name": module.name,
        "version": module.version,
        "author": module.author,
        "description": module.description,
        "args": module.args,
        "sha256": "",
        "autoload": false,
        "loaded": true,
        "pending_unload": module.pending_unload,
        "last_error": module.last_error,
        "has_control": module.has_control,
    })
}

struct Backend<'a, E: Engine> {
    store: Store,
    engine: &'a E,
    boot_id: String,
}

impl<'a, E: Engine> Backend<'a, E> {
    fn open(root: &Path, engine: &'a E, boot_id: String) -> Result<Self> {
        Ok(Self {
            store: Store::open(root)?,
            engine,
            boot_id,
        })
    }

    fn modules(&self) -> Result<Vec<RuntimeModule>> {
        if self.engine.probe().active {
            self.engine.modules()
        } else {
            Ok(Vec::new())
        }
    }

    fn index(&self, id: &str) -> Result<usize> {
        store::validate_id(id)?;
        self.store
            .records
            .iter()
            .position(|record| record.id == id)
            .ok_or_else(|| failure("not_found", "KPM is not registered"))
    }

    fn reconcile(&mut self, modules: &[RuntimeModule]) {
        for record in &mut self.store.records {
            if let Some(module) = modules
                .iter()
                .find(|module| module.name == record.metadata.name)
            {
                record.args.clone_from(&module.args);
                record.pending_unload = module.pending_unload;
                record.metadata.has_control = module.has_control;
                if module.pending_unload {
                    record.pending_boot_id.clone_from(&self.boot_id);
                }
                if module.last_error.is_some() {
                    record.last_error.clone_from(&module.last_error);
                }
            } else {
                record.pending_unload = false;
                record.pending_boot_id.clear();
            }
        }
    }

    fn list(&mut self) -> Result<Value> {
        let modules = self.modules()?;
        self.reconcile(&modules);
        let mut values = self
            .store
            .records
            .iter()
            .map(|record| {
                record.value(
                    modules
                        .iter()
                        .any(|module| module.name == record.metadata.name),
                )
            })
            .collect::<Vec<_>>();
        for module in &modules {
            if !self
                .store
                .records
                .iter()
                .any(|record| record.metadata.name == module.name)
            {
                values.push(runtime_value(module));
            }
        }
        self.store.save()?;
        Ok(json!(values))
    }

    fn info(&mut self, id: &str) -> Result<Value> {
        let values = self.list()?;
        values
            .as_array()
            .and_then(|values| values.iter().find(|value| value["id"] == id))
            .cloned()
            .ok_or_else(|| failure("not_found", "KPM is not registered or resident"))
    }

    fn import(&mut self, path: &Path) -> Result<Value> {
        let bytes = store::read_module(path)?;
        let metadata = elf::parse(&bytes)?;
        let id = sha256::digest(&bytes);
        if self.store.records.iter().any(|record| record.id == id) {
            return self.info(&id);
        }
        ensure!(
            !self
                .store
                .records
                .iter()
                .any(|record| record.metadata.name == metadata.name),
            failure(
                "duplicate_name",
                "Another registered KPM has this name; remove it before importing an update"
            )
        );
        self.store.persist_module(&id, &bytes)?;
        self.store.records.push(Record {
            id: id.clone(),
            metadata,
            args: String::new(),
            autoload: false,
            pending_unload: false,
            pending_boot_id: String::new(),
            last_error: None,
            last_attempt_boot: String::new(),
            last_success_boot: String::new(),
        });
        self.store.save()?;
        self.info(&id)
    }

    fn load(&mut self, id: &str, args: &str) -> Result<Value> {
        require_engine(&self.engine.probe())?;
        ensure!(
            args.len() <= runtime::ARGS_LIMIT && !args.contains('\0'),
            failure(
                "invalid_argument",
                "KPM arguments exceed 1023 bytes or contain NUL"
            )
        );
        let modules = self.engine.modules()?;
        self.reconcile(&modules);
        let index = self.index(id)?;
        let record = &self.store.records[index];
        ensure!(
            !modules
                .iter()
                .any(|module| module.name == record.metadata.name),
            failure(
                "already_loaded",
                "KPM is already resident; reboot before loading it again"
            )
        );
        let image = self.store.verified_module(record)?;
        let record = &mut self.store.records[index];
        args.clone_into(&mut record.args);
        record.last_attempt_boot.clone_from(&self.boot_id);
        record.last_error = Some("Load started; completion has not been confirmed".into());
        self.store.save()?;
        let result = self.engine.load(&image, args);
        match result {
            Ok(()) => {
                let confirmed = self.engine.modules()?;
                if !confirmed.iter().any(|module| {
                    module.name == self.store.records[index].metadata.name
                        && module.state == "loaded"
                        && !module.pending_unload
                }) {
                    self.reconcile(&confirmed);
                    self.store.records[index].autoload = false;
                    self.store.records[index].last_error =
                        Some("The engine did not confirm a live KPM".into());
                    self.store.save()?;
                    return Err(failure(
                        "unconfirmed_load",
                        "The engine did not confirm a live KPM",
                    ));
                }
                let record = &mut self.store.records[index];
                record.last_success_boot.clone_from(&self.boot_id);
                record.last_error = None;
                record.pending_unload = false;
                self.store.save()?;
                self.info(id)
            }
            Err(error) => {
                self.store.records[index].last_error = Some(format!("{error:#}"));
                self.store.records[index].autoload = false;
                if let Ok(modules) = self.engine.modules() {
                    self.reconcile(&modules);
                }
                self.store.save()?;
                Err(error)
            }
        }
    }

    fn control(&self, id: &str, args: &str) -> Result<Value> {
        require_engine(&self.engine.probe())?;
        let modules = self.engine.modules()?;
        let name = self
            .store
            .records
            .iter()
            .find(|record| record.id == id)
            .map(|record| record.metadata.name.clone())
            .or_else(|| {
                modules
                    .iter()
                    .find(|module| runtime_id(&module.name) == id)
                    .map(|module| module.name.clone())
            })
            .ok_or_else(|| failure("not_found", "KPM is not registered or resident"))?;
        let module = modules
            .iter()
            .find(|module| module.name == name)
            .ok_or_else(|| failure("not_loaded", "KPM is not loaded"))?;
        ensure!(
            !module.pending_unload && module.state == "loaded",
            failure(
                "reboot_required",
                "KPM is pending reboot and cannot accept controls"
            )
        );
        ensure!(
            module.has_control,
            failure("no_control", "This KPM has no ctl0 callback")
        );
        let output = self.engine.control(&name, args)?;
        Ok(json!({ "id": id, "output": output }))
    }

    fn unload(&mut self, id: &str) -> Result<Value> {
        let probe = self.engine.probe();
        require_engine(&probe)?;
        ensure!(
            probe.can_unload(),
            failure(
                "kernel_update_required",
                "Update KPM integration in Settings and reboot to enable unloading"
            )
        );
        let modules = self.engine.modules()?;
        self.reconcile(&modules);
        let local_index = self.store.records.iter().position(|record| record.id == id);
        let name = local_index
            .map(|index| self.store.records[index].metadata.name.clone())
            .or_else(|| {
                modules
                    .iter()
                    .find(|module| runtime_id(&module.name) == id)
                    .map(|module| module.name.clone())
            })
            .ok_or_else(|| failure("not_found", "KPM is not registered or resident"))?;
        ensure!(
            modules.iter().any(|module| module.name == name),
            failure("not_loaded", "KPM is not resident")
        );
        if let Some(index) = local_index {
            // Disable persistence first so an interrupted exit cannot reload it next boot.
            let record = &mut self.store.records[index];
            record.autoload = false;
            record.pending_unload = true;
            record.pending_boot_id.clone_from(&self.boot_id);
            self.store.save()?;
        }
        let result = self.engine.unload(&name);
        if let Some(index) = local_index {
            self.store.records[index].last_error =
                result.as_ref().err().map(|error| format!("{error:#}"));
            if let Ok(modules) = self.engine.modules() {
                self.reconcile(&modules);
            }
            self.store.save()?;
        }
        result?;
        // A legacy/misbehaving engine must not turn residency into a false success.
        ensure!(
            !self
                .engine
                .modules()?
                .iter()
                .any(|module| module.name == name),
            failure(
                "reboot_required",
                "KPM code is still resident; reboot is required"
            )
        );
        if local_index.is_some() {
            self.info(id)
        } else {
            Ok(json!({ "id": id, "loaded": false, "pending_unload": false }))
        }
    }

    fn remove(&mut self, id: &str) -> Result<Value> {
        if id.starts_with("runtime-") {
            self.unload(id)?;
            return Ok(json!({ "id": id, "removed": true }));
        }
        let index = self.index(id)?;
        let modules = self.modules()?;
        if modules
            .iter()
            .any(|module| module.name == self.store.records[index].metadata.name)
        {
            self.unload(id)?;
        }
        let path = self.store.path(id)?;
        self.store.records.remove(index);
        self.store.save()?;
        if path.try_exists()? {
            std::fs::remove_file(&path)?;
        }
        Ok(json!({ "id": id, "removed": true }))
    }

    fn autoload(&mut self, id: &str, enabled: bool) -> Result<Value> {
        let index = self.index(id)?;
        let modules = self.modules()?;
        self.reconcile(&modules);
        ensure!(
            !enabled || !self.store.records[index].pending_unload,
            failure(
                "reboot_required",
                "Cannot enable autoload while unload is pending"
            )
        );
        self.store.records[index].autoload = enabled;
        if enabled {
            self.store.records[index].last_attempt_boot.clear();
            self.store.records[index].last_success_boot.clear();
        }
        self.store.save()?;
        self.info(id)
    }

    fn load_autoload(&mut self) -> Result<()> {
        require_engine(&self.engine.probe())?;
        let ids = self
            .store
            .records
            .iter()
            .filter(|record| record.autoload)
            .map(|record| record.id.clone())
            .collect::<Vec<_>>();
        let mut failures = 0;
        for id in ids {
            let index = self.index(&id)?;
            let record = &self.store.records[index];
            if !record.last_attempt_boot.is_empty()
                && record.last_attempt_boot != record.last_success_boot
            {
                let record = &mut self.store.records[index];
                record.autoload = false;
                record.last_error =
                    Some("Previous load did not complete; autoload quarantined".into());
                self.store.save()?;
                failures += 1;
                continue;
            }
            if self
                .engine
                .modules()?
                .iter()
                .any(|module| module.name == record.metadata.name)
            {
                continue;
            }
            let args = record.args.clone();
            if let Err(error) = self.load(&id, &args) {
                // A rejected preflight must also be quarantined without deleting the file.
                let index = self.index(&id)?;
                self.store.records[index].autoload = false;
                self.store.records[index].last_error = Some(format!("{error:#}"));
                self.store.save()?;
                log::error!("KPM autoload {id}: {error:#}");
                failures += 1;
            }
        }
        ensure!(
            failures == 0,
            failure(
                "autoload_failed",
                format!("{failures} KPM auto-loads failed or were quarantined")
            )
        );
        Ok(())
    }
}

fn execute(command: KpmCommand) -> Result<Value> {
    if let KpmCommand::Status { runtime_only } = command {
        return status(runtime_only);
    }
    require_root()?;
    if let KpmCommand::Integration { action } = command {
        // NekoSU 没有在启动镜像中注入 kpimg，也没有内核侧 KPatch 钩子。
        // 在没有运行时的内核上持久化「已启用」只会制造误导性的待重启状态，
        // 因此直接返回明确错误（disable 仍允许，用于清理历史 PoC 状态）。
        if action.enabled() {
            ensure!(
                engine_active(),
                failure(
                    "kernel_runtime_absent",
                    "kernel does not expose KPatch runtime; KPM integration cannot be enabled on this build (see vendor/kpatch-next/NEKOSU_INTEGRATION.md)"
                )
            );
        }
        return crate::kpm_image::set_enabled(action.enabled());
    }
    crate::kpm_image::prepare()?;
    let mut backend = Backend::open(Path::new(STATE_ROOT), &NativeEngine, boot_id()?)?;
    match command {
        KpmCommand::List => backend.list(),
        KpmCommand::Import { path } => backend.import(&path),
        KpmCommand::Info { id } => backend.info(&id),
        KpmCommand::Load { id, args } => {
            // 先报告内核缺少 KPatch 运行时，而不是把原因归为「集成未启用」：
            // 在 NekoSU 上没有 boot-image kpimg 补丁时，启用集成本身就会被拒绝。
            ensure!(
                engine_active(),
                failure(
                    "engine_inactive",
                    "kernel does not expose KPatch runtime; KPM loading is unavailable on this build"
                )
            );
            ensure!(
                crate::kpm_image::configured_enabled()?,
                failure(
                    "integration_disabled",
                    "Enable KPM integration before loading modules"
                )
            );
            backend.load(&id, &args)
        }
        KpmCommand::Control { id, args } => backend.control(&id, &args),
        KpmCommand::Unload { id } => backend.unload(&id),
        KpmCommand::Remove { id } => backend.remove(&id),
        KpmCommand::Autoload { id, action } => backend.autoload(&id, action.enabled()),
        KpmCommand::Status { .. } | KpmCommand::Integration { .. } => unreachable!(),
    }
}

/// Execute a command and emit exactly one JSON envelope to stdout.
///
/// # Errors
/// Returns command, registry, kernel, integration, or output failures. Command
/// errors are included in the envelope before they propagate to the CLI.
pub fn run(command: KpmCommand) -> Result<()> {
    let result = execute(command);
    let mut stdout = std::io::stdout().lock();
    serde_json::to_writer(&mut stdout, &envelope(&result))?;
    stdout.write_all(b"\n")?;
    stdout.flush()?;
    result.map(|_| ())
}

/// The caller must screen safe mode before invoking this boot hook.
///
/// # Errors
/// Returns root, registry, kernel, or auto-load failures. Failed modules remain
/// stored, and their auto-load setting is disabled until an explicit retry.
pub fn load_autoload() -> Result<()> {
    require_root()?;
    if !crate::kpm_image::configured_enabled()? {
        return Ok(());
    }
    let mut backend = Backend::open(Path::new(STATE_ROOT), &NativeEngine, boot_id()?)?;
    if !backend.store.records.iter().any(|record| record.autoload) {
        return Ok(());
    }
    backend.load_autoload()
}

