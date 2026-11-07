package com.nekosu.ui.screen.main

import com.nekosu.R
import com.nekosu.ui.navigation.Route

fun extraSettingSearchEntries(): List<SettingSearchEntry> = listOf(
    SettingSearchEntry(R.string.settings_adb_root, R.string.settings_adb_root_summary, null, "adb root adbroot 调试 root adbd"),
    SettingSearchEntry(R.string.settings_shell_su_adb_only, R.string.settings_shell_su_adb_only_summary, null, "shell su adb only 仅 adb"),
    SettingSearchEntry(R.string.settings_sulog, R.string.settings_sulog_summary, null, "su log sulog 日志"),
    SettingSearchEntry(R.string.settings_selinux_hide, R.string.settings_selinux_hide_summary, null, "selinux hide 隐藏"),
    SettingSearchEntry(R.string.settings_kernel_umount, R.string.settings_kernel_umount_summary, null, "kernel umount 卸载 mount"),
    SettingSearchEntry(R.string.settings_umount_modules_default, R.string.settings_umount_modules_default_summary, null, "umount modules default 模块卸载"),
    SettingSearchEntry(R.string.settings_auto_jailbreak, R.string.settings_auto_jailbreak_summary, null, "jailbreak 越狱 auto 自动"),
    SettingSearchEntry(R.string.settings_soft_reboot, R.string.settings_soft_reboot_summary, null, "soft reboot 软重启 reboot"),
    SettingSearchEntry(R.string.settings_sucompat, R.string.settings_sucompat_summary, null, "su compat sucompat 兼容"),
    SettingSearchEntry(R.string.settings_language, null, Route.ThemeSettings, "language locale 语言"),
    SettingSearchEntry(R.string.settings_config_enable_blur, R.string.settings_config_enable_blur_summary, Route.ThemeSettings, "blur 模糊"),
    SettingSearchEntry(R.string.settings_config_use_custom_background_seed_color, R.string.settings_config_use_custom_background_seed_color_summary, Route.ThemeSettings, "background seed color 背景取色"),
    SettingSearchEntry(R.string.settings_custom_background, R.string.settings_custom_background_summary, Route.ThemeSettings, "background wallpaper 背景 壁纸"),
    SettingSearchEntry(R.string.settings_custom_enable_high_contrast, R.string.settings_custom_enable_high_contrast_summary, Route.ThemeSettings, "high contrast 高对比度"),
    SettingSearchEntry(R.string.settings_exp_draw_background_to_blur, null, Route.ThemeSettings, "draw background blur 背景模糊"),
    SettingSearchEntry(R.string.settings_flash_card_background, R.string.settings_flash_card_background_summary, Route.ThemeSettings, "flash card background 刷写卡片背景"),
    SettingSearchEntry(R.string.background_crop_method_title, null, Route.ThemeSettings, "background crop 背景裁剪"),
)
