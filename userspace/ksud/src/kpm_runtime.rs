//! Root-only KPatch syscall transport. No shell or legacy CLI is involved.

use std::ffi::CString;
use std::io;

use anyhow::{Result, ensure};

use super::failure;

const HELLO: u32 = 0x1000;
const VERSION: u32 = 0x1008;
const LOAD_PATH: u32 = 0x1020;
const LOAD_BUFFER: u32 = 0x1023;
const UNLOAD: u32 = 0x1021;
const CONTROL: u32 = 0x1022;
const LIST: u32 = 0x1031;
const INFO: u32 = 0x1032;
const CAPABILITIES: u32 = 0x1033;
const HELLO_MAGIC: i64 = 0x2026_2026;
pub(super) const REQUIRED_CAPABILITIES: u64 = 0x3f;
pub(super) const UNLOAD_CAPABILITY: u64 = 0x40;
pub(super) const OUTPUT_LIMIT: usize = 4096;
pub(super) const ARGS_LIMIT: usize = 1023;

#[derive(Clone, Debug, Default)]
pub(super) struct Probe {
    pub active: bool,
    pub version: Option<String>,
    pub capabilities: Option<u64>,
    pub error: Option<String>,
    pub upstream: bool,
}

impl Probe {
    pub const fn can_unload(&self) -> bool {
        self.managed()
            && (self.upstream
                || matches!(self.capabilities, Some(capabilities) if capabilities & UNLOAD_CAPABILITY != 0))
    }

    pub const fn avc_conflict(&self) -> bool {
        self.error.is_some() || (self.active && (self.upstream || !self.managed()))
    }

    pub const fn managed(&self) -> bool {
        self.active
            && self.error.is_none()
            && (self.upstream
                || matches!(
                    self.capabilities,
                    Some(capabilities)
                        if capabilities & REQUIRED_CAPABILITIES == REQUIRED_CAPABILITIES
                ))
    }
}

#[derive(Clone, Debug, Default)]
pub(super) struct RuntimeModule {
    pub name: String,
    pub version: String,
    pub author: String,
    pub description: String,
    pub args: String,
    pub pending_unload: bool,
    pub last_error: Option<String>,
    pub has_control: bool,
    pub state: String,
}

pub(super) trait Engine {
    fn probe(&self) -> Probe;
    fn modules(&self) -> Result<Vec<RuntimeModule>>;
    fn load(&self, image: &[u8], args: &str) -> Result<()>;
    fn control(&self, name: &str, args: &str) -> Result<String>;
    fn unload(&self, name: &str) -> Result<()>;
}

pub(super) struct NativeEngine;

fn command_word(command: u32) -> u64 {
    (0x0000_0d05_u64 << 32) | (0x2026_u64 << 16) | u64::from(command)
}

fn compatible_command(command: u32, version: i64) -> u64 {
    if version >= 0x0a05 {
        command_word(command)
    } else {
        u64::from(command)
    }
}

fn syscall(command: u32, args: [usize; 4]) -> Result<i64> {
    #[cfg(all(target_os = "android", target_arch = "aarch64"))]
    {
        // Never send the repurposed truncate syscall from an unprivileged UID.
        ensure!(
            unsafe { libc::getuid() } == 0,
            failure("root_required", "KPM operations require UID 0")
        );
        // Match KPatch-Next's compact_cmd(), including older unversioned engines.
        let version = unsafe { libc::syscall(45 as libc::c_long, 0_usize, command_word(VERSION)) };
        let result = unsafe {
            libc::syscall(
                45 as libc::c_long,
                0_usize,
                compatible_command(command, version),
                args[0],
                args[1],
                args[2],
                args[3],
            )
        };
        if result < 0 {
            let error = io::Error::last_os_error();
            let pending = error.raw_os_error() == Some(libc::EINPROGRESS);
            return Err(anyhow::Error::new(error).context(super::KpmFailure {
                code: if pending {
                    "reboot_required"
                } else {
                    "kernel_error"
                },
                message: format!("KPatch command {command:#x}"),
            }));
        }
        Ok(result)
    }
    #[cfg(not(all(target_os = "android", target_arch = "aarch64")))]
    {
        let _ = (
            compatible_command(command, 0x0d05),
            args,
            io::Error::last_os_error(),
        );
        Err(failure(
            "unsupported",
            "KPM requires an Android ARM64 kernel",
        ))
    }
}

fn checked_string(text: &str, limit: usize) -> Result<CString> {
    ensure!(
        text.len() <= limit,
        failure("invalid_argument", format!("Input exceeds {limit} bytes"))
    );
    CString::new(text).map_err(|_| failure("invalid_argument", "Input contains a NUL byte"))
}

pub(super) fn load_arguments_pointer(arguments: &CString) -> usize {
    if arguments.as_bytes().is_empty() {
        0
    } else {
        arguments.as_ptr() as usize
    }
}

fn output_string(buffer: &[u8]) -> Result<String> {
    let length = buffer.iter().position(|byte| *byte == 0).ok_or_else(|| {
        failure(
            "invalid_kernel_response",
            "Kernel output is not NUL terminated",
        )
    })?;
    String::from_utf8(buffer[..length].to_vec())
        .map_err(|_| failure("invalid_kernel_response", "Kernel output is not UTF-8"))
}

fn parse_info(text: &str) -> Result<RuntimeModule> {
    // Standard KPatch-Next info has no lifecycle or control-capability fields.
    let mut module = RuntimeModule {
        state: "loaded".into(),
        has_control: true,
        ..RuntimeModule::default()
    };
    for line in text.lines() {
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        match key {
            "name" => value.clone_into(&mut module.name),
            "version" => value.clone_into(&mut module.version),
            "author" => value.clone_into(&mut module.author),
            "description" => value.clone_into(&mut module.description),
            "args" => value.clone_into(&mut module.args),
            "args_hex" => {
                ensure!(
                    value.len() % 2 == 0 && value.len() <= ARGS_LIMIT * 2,
                    failure(
                        "invalid_kernel_response",
                        "Invalid hexadecimal KPM arguments"
                    )
                );
                let decoded = value
                    .as_bytes()
                    .chunks_exact(2)
                    .map(|pair| {
                        let text = std::str::from_utf8(pair)?;
                        Ok(u8::from_str_radix(text, 16)?)
                    })
                    .collect::<Result<Vec<u8>>>()?;
                module.args = String::from_utf8(decoded)?;
            }
            "state" => value.clone_into(&mut module.state),
            "has_control" => module.has_control = value == "1",
            "pending_unload" => module.pending_unload = value == "1",
            "last_error" if value != "0" && !value.is_empty() => {
                module.last_error = Some(format!("kernel errno {value}"));
            }
            _ => {}
        }
    }
    ensure!(
        !module.name.is_empty() && module.name.len() < 32,
        failure(
            "invalid_kernel_response",
            "Invalid KPM name in kernel response"
        )
    );
    Ok(module)
}

pub(super) fn probe_with(mut call: impl FnMut(u32) -> Result<i64>) -> Probe {
    if call(HELLO).ok() != Some(HELLO_MAGIC) {
        return Probe::default();
    }
    let version = match call(VERSION).and_then(|value| u32::try_from(value).map_err(Into::into)) {
        Ok(version) => version,
        Err(error) => {
            return Probe {
                active: true,
                error: Some(format!("KPM version probe failed: {error:#}")),
                ..Probe::default()
            };
        }
    };
    let mut probe = Probe {
        active: true,
        version: Some(format!(
            "{}.{}.{}",
            (version >> 16) & 255,
            (version >> 8) & 255,
            version & 255
        )),
        ..Probe::default()
    };
    match call(CAPABILITIES).and_then(|value| u64::try_from(value).map_err(Into::into)) {
        Ok(capabilities) => probe.capabilities = Some(capabilities),
        Err(error) => {
            if error.chain().any(|cause| {
                cause
                    .downcast_ref::<io::Error>()
                    .is_some_and(|error| error.raw_os_error() == Some(libc::ENOSYS))
            }) {
                // 0x1033 is a MilkSU extension, absent from standard KPatch-Next.
                probe.upstream = true;
            } else {
                probe.error = Some(format!("KPM capability probe failed: {error:#}"));
            }
        }
    }
    probe
}

impl Engine for NativeEngine {
    fn probe(&self) -> Probe {
        probe_with(|command| syscall(command, [0; 4]))
    }

    fn modules(&self) -> Result<Vec<RuntimeModule>> {
        let mut buffer = vec![0; OUTPUT_LIMIT];
        syscall(LIST, [buffer.as_mut_ptr() as usize, buffer.len(), 0, 0])?;
        let names = output_string(&buffer)?;
        let mut modules = Vec::new();
        for name in names.lines().filter(|name| !name.is_empty()) {
            let name = checked_string(name, 31)?;
            buffer.fill(0);
            syscall(
                INFO,
                [
                    name.as_ptr() as usize,
                    buffer.as_mut_ptr() as usize,
                    buffer.len(),
                    0,
                ],
            )?;
            modules.push(parse_info(&output_string(&buffer)?)?);
        }
        Ok(modules)
    }

    fn load(&self, image: &[u8], args: &str) -> Result<()> {
        ensure!(
            !image.is_empty() && image.len() <= super::elf::FILE_LIMIT,
            failure(
                "invalid_file",
                "KPM must contain 1 byte to 32 MiB of image data"
            )
        );
        let args = checked_string(args, ARGS_LIMIT)?;
        if self.probe().upstream {
            use std::io::Write;
            use std::os::unix::ffi::OsStrExt;

            // Match sc_kpm_load(path, args, NULL). Keep the validated bytes
            // private and the file alive until the synchronous syscall returns.
            let mut file = tempfile::NamedTempFile::new()?;
            file.write_all(image)?;
            file.as_file().sync_all()?;
            let path = CString::new(file.path().as_os_str().as_bytes())?;
            syscall(
                LOAD_PATH,
                [path.as_ptr() as usize, load_arguments_pointer(&args), 0, 0],
            )?;
            return Ok(());
        }
        syscall(
            LOAD_BUFFER,
            [
                image.as_ptr() as usize,
                image.len(),
                load_arguments_pointer(&args),
                0,
            ],
        )?;
        Ok(())
    }

    fn control(&self, name: &str, args: &str) -> Result<String> {
        let name = checked_string(name, 31)?;
        let args = checked_string(args, ARGS_LIMIT)?;
        let mut buffer = vec![0; OUTPUT_LIMIT];
        syscall(
            CONTROL,
            [
                name.as_ptr() as usize,
                args.as_ptr() as usize,
                buffer.as_mut_ptr() as usize,
                buffer.len(),
            ],
        )?;
        output_string(&buffer)
    }

    fn unload(&self, name: &str) -> Result<()> {
        let name = checked_string(name, 31)?;
        syscall(UNLOAD, [name.as_ptr() as usize, 0, 0, 0])?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn standard_info_is_a_loaded_controllable_module() {
        let module = parse_info("name=standard\nversion=1\nauthor=author\nargs=x=y\n").unwrap();
        assert_eq!(module.state, "loaded");
        assert!(module.has_control);
        assert_eq!(module.args, "x=y");
        assert!(!module.pending_unload);
        let module =
            parse_info("name=extended\nstate=failed\nhas_control=0\npending_unload=1\n").unwrap();
        assert_eq!(module.state, "failed");
        assert!(!module.has_control);
        assert!(module.pending_unload);
    }

    #[test]
    fn command_encoding_matches_upstream_compact_cmd() {
        assert_eq!(compatible_command(LOAD_PATH, 0x0a04), 0x1020);
        assert_eq!(compatible_command(HELLO, -i64::from(libc::ENOSYS)), 0x1000);
        assert_eq!(
            compatible_command(LOAD_PATH, 0x0a05),
            command_word(LOAD_PATH)
        );
        assert_eq!(
            compatible_command(LOAD_BUFFER, 0x0d05),
            command_word(LOAD_BUFFER)
        );
    }
}
