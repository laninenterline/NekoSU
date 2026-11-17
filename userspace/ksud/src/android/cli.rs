use std::path::PathBuf;

use android_logger::Config;
use anyhow::{Context, Ok, Result};
use clap::Parser;
use log::{LevelFilter, info};

use crate::{
    android::{
        debug, dynamic_manager, feature, init_event, ksucalls, manager_identity,
        module::{self, module_config, regenerate_preinit},
        profile, sepolicy, su, sulog, susfs, uapi, umount_config, utils,
    },
    anykernel3::{self, Slot},
    apk_sign, assets,
    boot_patch::{BootPatchArgs, BootRestoreArgs},
    defs,
    lkm_image::BootPatchV2Args,
};

/// KernelSU userspace cli
#[derive(Parser, Debug)]
#[command(author, version = defs::FULL_VERSION, about, long_about = None)]
struct Args {
    #[command(subcommand)]
    command: Commands,
}

#[derive(clap::Subcommand, Debug)]
enum Commands {
    /// Flash an AnyKernel3 ZIP
    #[command(name = "anykernel3")]
    AnyKernel3 {
        /// AnyKernel3 ZIP file path
        zip: PathBuf,

        /// Select A/B slot for flashing
        #[arg(long, value_enum)]
        slot: Option<Slot>,
    },

    /// Manage KernelSU modules
    Module {
        #[command(subcommand)]
        command: Module,
    },

    /// Trigger `post-fs-data` event
    PostFsData,

    /// Trigger `service` event
    Services,

    /// Run sulog reader daemon. Not for user. Use `ksud debug sulogd` to launch daemon.
    #[command(hide = true)]
    Sulogd,

    /// Trigger `boot-complete` event
    BootCompleted,

    /// Load kernelsu.ko and execute late-load stage scripts
    LateLoad {
        /// Pass allow_shell=1 when loading kernelsu.ko
        #[arg(long)]
        allow_shell: bool,

        /// Specify kernel KMI version instead of auto-detection
        #[arg(long)]
        kmi: Option<String>,


        /// manager package name
        #[arg(long, default_value_t = String::from(defs::DEFAULT_PACKAGE_NAME))]
        package_name: String,
    },

    /// Manage auto apply user custom umount configs
    UmountConfig {
        #[command(subcommand)]
        command: UmountConfigOp,
    },

    /// Emulate system reboot
    SoftReboot,

    /// Load a kernel module with kallsyms access
    Insmod {
        /// kernel module path
        module: PathBuf,
        /// module load parameters (e.g. key=val key2=val2)
        #[arg(trailing_var_arg = true, allow_hyphen_values = true, num_args = 0..)]
        params: Vec<String>,
    },

    /// Install KernelSU userspace component to system
    Install {
        #[arg(long, default_value = None)]
        libadbroot: Option<PathBuf>,

        #[arg(long, default_value = None)]
        data_path: Option<PathBuf>,
    },

    /// Hot-update the KernelSU LKM without re-running the exploit
    HotUpdate {
        /// Path to a new kernelsu.ko (default: embedded asset for current KMI)
        #[arg(long)]
        ko: Option<std::path::PathBuf>,
        /// Manager package to restart (default com.nekosu)
        #[arg(long)]
        package: Option<String>,
        /// Detach and continue in a new session (used by the manager)
        #[arg(long)]
        detach: bool,
        /// Only enable/disable hot-update capability flag (takes effect on next load)
        #[arg(long)]
        set_capability: Option<bool>,
    },

    /// Unload KernelSU kernel module (LKM Only)
    Unload,

    /// Uninstall KernelSU modules and itself(LKM Only)
    Uninstall {
        #[arg(long, default_value_t = String::from("com.nekosu"))]
        package_name: String,
    },

    /// SELinux policy Patch tool
    Sepolicy {
        #[command(subcommand)]
        command: Sepolicy,
    },

    /// Manage App Profiles
    Profile {
        #[command(subcommand)]
        command: Profile,
    },

    /// Manage kernel features
    Feature {
        #[command(subcommand)]
        command: Feature,
    },

    /// Patch boot or init_boot images to apply KernelSU
    BootPatch(BootPatchArgs),

    /// Restore boot or init_boot images patched by KernelSU
    BootRestore(BootRestoreArgs),

    /// Patch KernelSU into a boot image
    ///
    /// Always operates on a boot image; never selects init_boot or vendor_boot.
    BootPatchV2(BootPatchV2Args),

    /// Show boot information
    BootInfo {
        #[command(subcommand)]
        command: BootInfo,
    },

    /// Manage Kernel Patch Module (KPM)
    Kpm {
        #[command(subcommand)]
        command: crate::kpm::KpmCommand,
    },

    /// For developers
    Debug {
        #[command(subcommand)]
        command: Debug,
    },
    /// Kernel interface
    Kernel {
        #[command(subcommand)]
        command: Kernel,
    },

    /// Resetprop - Magisk-compatible system property tool
    Resetprop(crate::android::resetprop::Args),

    /// Manage susfs component
    Susfs(susfs::cli::SusfsArgs),

    /// Manage initrc injection
    Initrc {
        #[command(subcommand)]
        command: Initrc,
    },

    /// Manage the hidden manager identity record
    ManagerIdentity {
        #[command(subcommand)]
        command: ManagerIdentity,
    },

    /// Manage the ksud-hosted local web admin
    Webadmin {
        #[command(subcommand)]
        command: Webadmin,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Webadmin {
    /// Enable the web admin and start the ksud-hosted server
    Enable,

    /// Disable the web admin and stop the server
    Disable,

    /// Show enabled/port/pid/token state (no token value)
    Status,

    /// Print the full local URL including the access token
    Url,

    /// Rotate the access token (old links stop working) and print the new URL
    ResetToken,

    /// Print diagnostics for troubleshooting
    Diagnose,

    /// Run the resident server in the foreground (internal)
    #[command(hide = true)]
    Serve {
        /// Override the listening port
        #[arg(long)]
        port: Option<u16>,
    },
}

#[derive(clap::Subcommand, Debug)]
enum UmountConfigOp {
    /// Add an new umount config to configuration file
    Add {
        /// mount point path
        mnt: String,
        /// umount flags (default: 0, MNT_DETACH: 2)
        #[arg(short, long, default_value = "0")]
        flags: u32,
    },
    /// Delete an umount config from configuration file
    Del {
        /// mount point path
        mnt: String,
    },
    /// Clear all auto apply umount config from configuration file
    Clear,
    /// List all configured auto apply umount configuration
    List,
}

#[derive(clap::Subcommand, Debug)]
enum BootInfo {
    /// show current kmi version
    CurrentKmi,

    /// show supported kmi versions
    SupportedKmis,

    /// check if device is A/B capable
    IsAbDevice,

    /// show auto-selected boot partition name
    DefaultPartition,

    /// list available partitions for current or OTA toggled slot
    AvailablePartitions,

    /// show slot suffix for current or OTA toggled slot
    SlotSuffix {
        /// toggle to another slot
        #[arg(short = 'u', long, default_value = "false")]
        ota: bool,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Debug {
    /// Set the manager app, kernel CONFIG_KSU_DEBUG should be enabled.
    SetManager {
        /// manager package name
        #[arg(default_value_t = String::from(defs::DEFAULT_PACKAGE_NAME))]
        apk: String,
    },

    /// Get apk size and hash
    GetSign {
        /// apk path
        apk: String,
    },

    /// Root Shell
    Su {
        /// switch to gloabl mount namespace
        #[arg(short, long, default_value = "false")]
        global_mnt: bool,
    },

    /// Get kernel version
    Version,

    /// For testing
    Test,

    /// Extract an embedded binary to a specified path
    ExtractBinary {
        /// binary name (e.g. busybox, resetprop, bootctl)
        name: String,
        /// destination file path
        path: PathBuf,
    },

    /// Process mark management
    Mark {
        #[command(subcommand)]
        command: MarkCommand,
    },

    /// Launch sulogd daemon manually
    Sulogd,

    /// Get kernel info
    Info,

    /// Print default package name
    Package,
}

#[derive(clap::Subcommand, Debug)]
enum MarkCommand {
    /// Get mark status for a process (or all)
    Get {
        /// target pid (0 for total count)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Mark a process
    Mark {
        /// target pid (0 for all processes)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Unmark a process
    Unmark {
        /// target pid (0 for all processes)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Refresh mark for all running processes
    Refresh,
}

#[derive(clap::Subcommand, Debug)]
enum Sepolicy {
    /// Patch sepolicy
    Patch {
        /// sepolicy statements
        sepolicy: String,
    },

    /// Apply sepolicy from file
    Apply {
        /// sepolicy file path
        file: String,
    },

    /// Check if sepolicy statement is supported/valid
    Check {
        /// sepolicy statements
        sepolicy: String,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Module {
    /// Install module <ZIP>
    Install {
        /// module zip file path
        zip: String,
    },

    /// Undo module uninstall mark <id>
    UndoUninstall {
        /// module id
        id: String,
    },

    /// Uninstall module <id>
    Uninstall {
        /// module id
        id: String,
    },

    /// enable module <id>
    Enable {
        /// module id
        id: String,
    },

    /// disable module <id>
    Disable {
        // module id
        id: String,
    },

    /// run action for module <id>
    Action {
        // module id
        id: String,
    },

    /// list all modules
    List,

    /// preinit actions run before SELinux policy is loaded
    Preinit {
        #[command(subcommand)]
        command: PreinitCmd,
    },

    /// manage module configuration
    Config {
        /// target internal module name (resolved as internal.<name>)
        #[arg(long)]
        internal: Option<String>,
        #[command(subcommand)]
        command: ModuleConfigCmd,
    },
}

#[derive(clap::Subcommand, Debug)]
enum PreinitCmd {
    /// show per-module preinit state for the next boot
    Status,
    /// re-enable preinit after it was disabled by repeated failed boots
    Reset,
}

#[derive(clap::Subcommand, Debug)]
enum ModuleConfigCmd {
    /// Get a config value
    Get {
        /// config key
        key: String,
    },

    /// Set a config value
    Set {
        /// config key
        key: String,
        /// config value (omit to read from stdin)
        value: Option<String>,
        /// read value from stdin (default if value not provided)
        #[arg(long)]
        stdin: bool,
        /// use temporary config (cleared on reboot)
        #[arg(short, long)]
        temp: bool,
    },

    /// List all config entries
    List,

    /// Delete a config entry
    Delete {
        /// config key
        key: String,
        /// delete from temporary config
        #[arg(short, long)]
        temp: bool,
    },

    /// Clear all config entries
    Clear {
        /// clear temporary config
        #[arg(short, long)]
        temp: bool,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Profile {
    /// get root profile's selinux policy of <package-name>
    GetSepolicy {
        /// package name
        package: String,
    },

    /// set root profile's selinux policy of <package-name> to <profile>
    SetSepolicy {
        /// package name
        package: String,
        /// policy statements
        policy: String,
    },

    /// get template of <id>
    GetTemplate {
        /// template id
        id: String,
    },

    /// set template of <id> to <template string>
    SetTemplate {
        /// template id
        id: String,
        /// template string
        template: String,
    },

    /// delete template of <id>
    DeleteTemplate {
        /// template id
        id: String,
    },

    /// list all templates
    ListTemplates {
        /// print template names instead of ids
        #[arg(short, long)]
        name: bool,

        /// locale used to resolve localized template names (for example, zh_CN)
        #[arg(long, requires = "name")]
        locale: Option<String>,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Feature {
    /// Get feature value and support status
    Get {
        /// Feature ID or name (su_compat, kernel_umount, sulog, adb_root, selinux_hide, shell_su_adb_only, avc_spoof)
        id: String,
        /// Read from config file
        #[arg(long, default_value_t = false)]
        config: bool,
    },

    /// Set feature value
    Set {
        /// Feature ID or name
        id: String,
        /// Feature value (0=disable, 1=enable)
        value: u64,
    },

    /// List all available features
    List,

    /// Check feature status (supported/unsupported/managed/forced)
    Check {
        /// Feature ID or name (su_compat, kernel_umount, sulog, adb_root, selinux_hide, shell_su_adb_only, avc_spoof)
        id: String,
    },

    /// Load configuration from file and apply to kernel
    Load,

    /// Save current kernel feature states to file
    Save,
}

#[derive(clap::Subcommand, Debug)]
enum Kernel {
    /// Nuke ext4 sysfs
    NukeExt4Sysfs {
        /// mount point
        mnt: String,
    },
    /// Manage umount list
    Umount {
        #[command(subcommand)]
        command: UmountOp,
    },
    /// Manage dynamic manager
    DynamicManager {
        #[command(subcommand)]
        command: DynamicManagerOp,
    },
    /// Notify that module is mounted
    NotifyModuleMounted,
}

#[derive(clap::Subcommand, Debug)]
enum DynamicManagerOp {
    /// Get the signature of the current dynamic manager (size+hash)
    Get {
        #[arg(long)]
        internal: Option<bool>,
    },
    /// Set the signature of the dynamic manager
    Set {
        /// the signature size
        size: u32,
        /// the signature hash
        #[arg(value_parser = dynamic_manager::parse_hash)]
        hash: [u8; 64],
    },
    /// Set the signature of the dynamic manager for apk
    SetApk {
        /// the apk path
        apk: String,
    },
    /// Clear the dynamic manager
    Clear,
}

#[derive(clap::Subcommand, Debug)]
enum UmountOp {
    /// Add mount point to umount list
    Add {
        /// mount point path
        mnt: String,
        /// umount flags (default: 0, MNT_DETACH: 2)
        #[arg(short, long, default_value = "0")]
        flags: u32,
    },
    /// Delete mount point from umount list
    Del {
        /// mount point path
        mnt: String,
    },
    /// Wipe all entries from umount list
    Wipe,
    /// List all entries from umount list
    List,
}

#[derive(clap::Subcommand, Debug)]
enum Initrc {
    /// Regenerate preinit rc file and preinit dirs
    Refresh,
}

#[derive(clap::Subcommand, Debug)]
enum ManagerIdentity {
    /// Print the recorded hidden manager identity as JSON
    Get,

    /// Record the hidden manager package and signing certificate
    Set {
        /// randomized manager package name
        package: String,
        /// DER size of the v2 signing certificate
        certificate_size: u32,
        /// lowercase hex SHA-256 of the v2 signing certificate
        certificate_sha256: String,
    },

    /// Remove the recorded hidden manager identity
    Clear,

    /// Check whether the kernel supports hidden manager identities
    Probe,
}

#[allow(clippy::similar_names)]
pub fn run() -> Result<()> {
    android_logger::init_once(
        Config::default()
            .with_max_level(crate::debug_select!(LevelFilter::Trace, LevelFilter::Info))
            .with_tag("KernelSU"),
    );

    ksucalls::setup_sigsys_handler();

    // the kernel executes su with argv[0] = "su" and replace it with us
    let arg0 = std::env::args().next().unwrap_or_default();
    if arg0 == "su" || arg0.ends_with("/su") {
        return su::root_shell();
    }

    if arg0.ends_with("resetprop") {
        let all_args: Vec<String> = std::env::args().collect();
        return crate::android::resetprop::run_from_args(&all_args);
    }

    if arg0.ends_with("ksu_susfs") {
        let all_args: Vec<String> = std::env::args().collect();
        return crate::android::susfs::cli::run_from_args(&all_args);
    }

    let cli = Args::parse();

    log::info!("command: {:?}", cli.command);

    let result = match cli.command {
        Commands::AnyKernel3 { zip, slot } => anykernel3::flash(&zip, slot),
        Commands::Susfs(args) => crate::android::susfs::cli::run_main(args),
        Commands::PostFsData => init_event::on_post_fs_data(),
        Commands::BootCompleted => {
            init_event::on_boot_completed();
            Ok(())
        }
        Commands::UmountConfig { command } => match command {
            UmountConfigOp::Add { mnt, flags } => umount_config::add_umount(&mnt, flags),
            UmountConfigOp::Del { mnt } => umount_config::del_umount(&mnt),
            UmountConfigOp::Clear => umount_config::wipe_umount(),
            UmountConfigOp::List => umount_config::list_umount(),
        },
        Commands::SoftReboot => crate::android::soft_reboot::soft_reboot(),
        Commands::Webadmin { command } => match command {
            Webadmin::Enable => {
                crate::webadmin_ksud::set_enabled(true)?;
                crate::webadmin_ksud::ensure_running()?;
                println!("网页管理器：已开启（ksud 常驻，不依赖 App）");
                // 顺带把带密钥的完整地址打出来（只有调用方看得到，不要重定向进日志）。
                println!("{}", crate::webadmin::url());
                Ok(())
            }
            Webadmin::Disable => {
                crate::webadmin_ksud::set_enabled(false)?;
                crate::webadmin_ksud::stop_running()?;
                println!("网页管理器：已关闭");
                Ok(())
            }
            Webadmin::Status => {
                println!("{}", crate::webadmin_ksud::status_text());
                Ok(())
            }
            Webadmin::Url => {
                // ⚠️ stdout 含访问密钥，调用方（App/用户）自己用，不要写日志。
                println!("{}", crate::webadmin::url());
                Ok(())
            }
            Webadmin::ResetToken => {
                let url = crate::webadmin_ksud::reset_token_url()?;
                println!("网页管理器：访问密钥已重置（旧链接立即失效）");
                println!("{url}");
                Ok(())
            }
            Webadmin::Diagnose => {
                println!("{}", crate::webadmin_ksud::diagnose_text());
                Ok(())
            }
            Webadmin::Serve { port } => crate::webadmin_ksud::serve_forever(port),
        },
        Commands::Insmod { module, params } => debug::insmod(&module, &params),
        Commands::Module { command } => {
            utils::switch_mnt_ns(1)?;
            match command {
                Module::Install { zip } => module::install_module(&zip),
                Module::UndoUninstall { id } => module::undo_uninstall_module(&id),
                Module::Uninstall { id } => module::uninstall_module(&id),
                Module::Enable { id } => module::enable_module(&id),
                Module::Disable { id } => module::disable_module(&id),
                Module::Action { id } => module::run_action(&id),
                Module::List => module::list_modules(),
                Module::Preinit { command } => match command {
                    PreinitCmd::Status => {
                        crate::android::preinit::print_status();
                        Ok(())
                    }
                    PreinitCmd::Reset => crate::android::preinit::reset_bootcount(),
                },
                Module::Config { internal, command } => {
                    let module_id = match internal {
                        Some(internal_name) => format!("internal.{internal_name}"),
                        None => std::env::var("KSU_MODULE").map_err(|_| {
                            anyhow::anyhow!(
                                "This command must be run in the context of a module or passed --internal <name>"
                            )
                        })?,
                    };
                    crate::android::module::validate_module_id(&module_id)?;

                    match command {
                        ModuleConfigCmd::Get { key } => {
                            // Use merge_configs to respect priority (temp overrides persist)
                            let config = module_config::merge_configs(&module_id)?;
                            match config.get(&key) {
                                Some(value) => {
                                    println!("{value}");
                                    Ok(())
                                }
                                None => anyhow::bail!("Key '{key}' not found"),
                            }
                        }
                        ModuleConfigCmd::Set {
                            key,
                            value,
                            stdin,
                            temp,
                        } => {
                            // Validate key at CLI layer for better user experience
                            module_config::validate_config_key(&key)?;

                            // Read value from stdin or argument
                            let value_str = match value {
                                Some(v) if !stdin => v,
                                _ => {
                                    // Read from stdin
                                    use std::io::Read;
                                    let mut buffer = String::new();
                                    std::io::stdin()
                                        .read_to_string(&mut buffer)
                                        .context("Failed to read from stdin")?;
                                    buffer
                                }
                            };

                            // Validate value
                            module_config::validate_config_value(&value_str)?;

                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::set_config_value(
                                &module_id,
                                &key,
                                &value_str,
                                config_type,
                            )
                        }
                        ModuleConfigCmd::List => {
                            let config = module_config::merge_configs(&module_id)?;
                            if config.is_empty() {
                                println!("No config entries found");
                            } else {
                                for (key, value) in config {
                                    println!("{key}={value}");
                                }
                            }
                            Ok(())
                        }
                        ModuleConfigCmd::Delete { key, temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::delete_config_value(&module_id, &key, config_type)
                        }
                        ModuleConfigCmd::Clear { temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::clear_config(&module_id, config_type)
                        }
                    }
                }
            }
        }
        Commands::Install {
            libadbroot,
            data_path,
        } => utils::install(libadbroot, data_path),
        Commands::HotUpdate { ko, package, detach, set_capability } => {
            match set_capability {
                Some(value) => crate::android::hot_update::set_capability(value),
                None => crate::android::hot_update::run(ko, package, detach),
            }
        }
        Commands::Unload => crate::android::unload::unload(),
        Commands::Uninstall { package_name } => utils::uninstall(&package_name),
        Commands::Sepolicy { command } => match command {
            Sepolicy::Patch { sepolicy } => sepolicy::live_patch(&sepolicy),
            Sepolicy::Apply { file } => sepolicy::apply_file(file),
            Sepolicy::Check { sepolicy } => sepolicy::check_rule(&sepolicy),
        },
        Commands::LateLoad {
            allow_shell,
            kmi,
            package_name,
        } => crate::android::late_load::run(&package_name, kmi, allow_shell),
        Commands::Services => {
            if ksucalls::get_version() <= 0 {
                info!("KernelSU not available, exiting services");
                std::process::exit(0);
            }
            init_event::on_services();
            Ok(())
        }
        Commands::Sulogd => sulog::run_sulogd(),
        Commands::Profile { command } => match command {
            Profile::GetSepolicy { package } => profile::get_sepolicy(package),
            Profile::SetSepolicy { package, policy } => profile::set_sepolicy(package, policy),
            Profile::GetTemplate { id } => profile::get_template(id),
            Profile::SetTemplate { id, template } => profile::set_template(id, template),
            Profile::DeleteTemplate { id } => profile::delete_template(id),
            Profile::ListTemplates { name, locale } => {
                profile::list_templates(name, locale.as_deref())
            }
        },

        Commands::Feature { command } => match command {
            Feature::Get { id, config } => {
                if config {
                    feature::get_feature_config(&id)
                } else {
                    feature::get_feature(&id)
                }
            }
            Feature::Set { id, value } => feature::set_feature(&id, value),
            Feature::List => {
                feature::list_features();
                Ok(())
            }
            Feature::Check { id } => feature::check_feature(&id),
            Feature::Load => feature::load_config_and_apply(),
            Feature::Save => feature::save_config(),
        },

        Commands::Kpm { command } => crate::kpm::run(command),

        Commands::Debug { command } => match command {
            Debug::SetManager { apk } => debug::set_manager(&apk),
            Debug::GetSign { apk } => {
                let sign = apk_sign::get_apk_signature(&apk)?;
                println!("size: {:#x}, hash: {}", sign.0, sign.1);
                Ok(())
            }
            Debug::Version => {
                println!("Kernel Version: {}", ksucalls::get_version());
                Ok(())
            }
            Debug::Su { global_mnt } => su::grant_root(global_mnt),
            Debug::Test => assets::ensure_binaries(false),
            Debug::ExtractBinary { name, path } => {
                let data = assets::get_asset(&name)?;
                utils::ensure_binary(&path, data.as_ref(), false)
            }
            Debug::Mark { command } => match command {
                MarkCommand::Get { pid } => debug::mark_get(pid),
                MarkCommand::Mark { pid } => debug::mark_set(pid),
                MarkCommand::Unmark { pid } => debug::mark_unset(pid),
                MarkCommand::Refresh => debug::mark_refresh(),
            },
            Debug::Sulogd => sulog::ensure_sulogd_running(),
            Debug::Info => {
                let info = ksucalls::get_info();
                println!("version: {}", info.version);
                println!("full_version: {}", ksucalls::get_full_version());
                println!("flags: 0x{:x}", info.flags);
                println!("uapi_version: {}", info.uapi_version);
                println!("features: 0x{:x}", info.features);
                println!("lkm: {}", ksucalls::is_lkm());
                println!(
                    "bundled: {}",
                    (info.flags & uapi::KSU_GET_INFO_FLAG_BUNDLED) != 0
                );
                println!("late_load: {}", ksucalls::is_late_load());
                println!("runtime_mode: {}", ksucalls::runtime_mode());
                println!(
                    "pr_build: {}",
                    (info.flags & uapi::KSU_GET_INFO_FLAG_PR_BUILD) != 0
                );
                Ok(())
            }
            Debug::Package => {
                println!("{}", defs::DEFAULT_PACKAGE_NAME);
                Ok(())
            }
        },

        Commands::BootPatch(boot_patch) => crate::boot_patch::patch(boot_patch),

        Commands::BootInfo { command } => match command {
            BootInfo::CurrentKmi => {
                let kmi = crate::boot_patch::get_current_kmi()?;
                println!("{kmi}");
                // return here to avoid printing the error message
                return Ok(());
            }
            BootInfo::SupportedKmis => {
                let kmi = crate::assets::list_supported_kmi();
                for kmi in &kmi {
                    println!("{kmi}");
                }
                return Ok(());
            }
            BootInfo::IsAbDevice => {
                let val =
                    utils::getprop("ro.build.ab_update").unwrap_or_else(|| String::from("false"));
                let is_ab = val.trim().to_lowercase() == "true";
                println!("{}", if is_ab { "true" } else { "false" });
                return Ok(());
            }
            BootInfo::DefaultPartition => {
                let kmi = crate::boot_patch::get_current_kmi().unwrap_or_else(|_| String::new());
                let name = crate::boot_patch::choose_boot_partition(&kmi, false, &None);
                println!("{name}");
                return Ok(());
            }
            BootInfo::SlotSuffix { ota } => {
                let suffix = crate::boot_patch::get_slot_suffix(ota);
                println!("{suffix}");
                return Ok(());
            }
            BootInfo::AvailablePartitions => {
                let parts = crate::boot_patch::list_available_partitions();
                for p in &parts {
                    println!("{p}");
                }
                return Ok(());
            }
        },
        Commands::BootRestore(boot_restore) => crate::boot_patch::restore(boot_restore),
        Commands::Resetprop(resetprop_args) => crate::android::resetprop::run(&resetprop_args),
        Commands::BootPatchV2(patch) => crate::lkm_image::patch_boot(&patch),
        Commands::Kernel { command } => match command {
            Kernel::NukeExt4Sysfs { mnt } => ksucalls::nuke_ext4_sysfs(&mnt),
            Kernel::Umount { command } => match command {
                UmountOp::Add { mnt, flags } => ksucalls::umount_list_add(&mnt, flags),
                UmountOp::Del { mnt } => ksucalls::umount_list_del(&mnt),
                UmountOp::Wipe => ksucalls::umount_list_wipe(),
                UmountOp::List => {
                    let list = ksucalls::umount_list_list()?;
                    println!("{}", serde_json::to_string(&list)?);
                    Ok(())
                }
            },
            Kernel::DynamicManager { command } => match command {
                DynamicManagerOp::Set { size, hash } => dynamic_manager::set(size, hash),
                DynamicManagerOp::Get { internal } => {
                    let (size, hash) = ksucalls::dynamic_manager_get()?;
                    if internal.is_some_and(|s| s) {
                        println!(
                            "{}",
                            serde_json::to_string_pretty(
                                &serde_json::json!({"size":size,"hash":String::from_utf8_lossy(&hash)})
                            )?
                        );
                    } else {
                        println!("size: {}, hash: {}", size, String::from_utf8_lossy(&hash));
                    }
                    Ok(())
                }
                DynamicManagerOp::SetApk { apk } => {
                    let sign = apk_sign::get_apk_signature(&apk)?;

                    let bytes = sign.1.as_bytes();

                    let mut hash = [0u8; 64];
                    hash.copy_from_slice(bytes);

                    dynamic_manager::set(sign.0, hash)
                }
                DynamicManagerOp::Clear => dynamic_manager::clear(),
            },
            Kernel::NotifyModuleMounted => {
                ksucalls::report_module_mounted();
                Ok(())
            }
        },
        Commands::Initrc { command } => match command {
            Initrc::Refresh => regenerate_preinit(),
        },
        Commands::ManagerIdentity { command } => match command {
            ManagerIdentity::Get => {
                if let Some(record) = manager_identity::get()? {
                    println!(
                        "{}",
                        serde_json::to_string_pretty(&serde_json::json!({
                            "package": record.package,
                            "size": record.certificate_size,
                            "sha256": record.certificate_sha256,
                        }))?
                    );
                } else {
                    println!("{}", serde_json::json!({ "package": null }));
                }
                Ok(())
            }
            ManagerIdentity::Set {
                package,
                certificate_size,
                certificate_sha256,
            } => manager_identity::set(&package, certificate_size, &certificate_sha256),
            ManagerIdentity::Clear => manager_identity::clear(),
            ManagerIdentity::Probe => manager_identity::probe(),
        },
    };

    if let Err(e) = &result {
        log::error!("Error: {e:?}");
    }
    result
}
