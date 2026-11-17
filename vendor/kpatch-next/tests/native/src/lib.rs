#![allow(dead_code)]
#![deny(clippy::all, clippy::pedantic)]
#![warn(clippy::nursery)]
#![allow(
    clippy::module_name_repetitions,
    clippy::cast_possible_truncation,
    clippy::cast_sign_loss,
    clippy::cast_precision_loss,
    clippy::doc_markdown,
    clippy::too_many_lines,
    clippy::cast_possible_wrap,
    clippy::redundant_field_names
)]

// Exercise production backend files without building Android/boot-image assets.
#[path = "../../../../../userspace/ksud/src/kpm.rs"]
pub mod kpm;

#[allow(clippy::missing_errors_doc)]
pub mod kpm_image {
    use anyhow::Result;
    use serde_json::{Value, json};

    pub const fn configured_enabled() -> Result<bool> {
        Ok(false)
    }

    pub fn transition_status(_active: bool) -> Result<Value> {
        Ok(json!({
            "configured_enabled": false, "reboot_required": false,
            "transition_failed": false, "reason": null,
        }))
    }

    pub fn runtime_status(active: bool) -> Result<Value> {
        transition_status(active)
    }

    pub fn set_enabled(enabled: bool) -> Result<Value> {
        Ok(json!({ "configured_enabled": enabled, "reboot_required": true }))
    }
}
