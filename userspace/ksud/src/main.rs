#![feature(decl_macro)]

#[cfg(target_os = "android")]
mod android;
#[cfg(target_os = "android")]
mod anykernel3;
mod apk_sign;
mod assets;
mod banner;
mod boot_patch;
#[cfg(not(target_os = "android"))]
mod cli_non_android;
mod defs;
mod feature_id;
#[cfg(target_os = "android")]
mod kpm;
#[cfg(target_os = "android")]
mod kpm_image;
mod lkm_image;
mod lkm_image_btf;
#[cfg(target_os = "android")]
mod atomic;
mod webadmin;
#[cfg(target_os = "android")]
mod webadmin_ksud;

fn main() -> anyhow::Result<()> {
    #[cfg(target_os = "android")]
    {
        android::cli::run()
    }
    #[cfg(not(target_os = "android"))]
    {
        cli_non_android::run()
    }
}
