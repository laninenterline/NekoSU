package com.nekosu.ui.screen.main
import com.nekosu.R
import com.nekosu.ui.navigation.Route
data class SettingSearchEntry(
    val titleRes: Int,
    val descriptionRes: Int?,
    val route: Route?,
    val keywords: String,
)

fun settingSearchEntries(): List<SettingSearchEntry> = listOf(
    SettingSearchEntry(R.string.settings_profile_template, R.string.settings_profile_template_summary, Route.AppProfileTemplate, "settings profile template settings profile template summary"),
    SettingSearchEntry(R.string.settings_check_manager_update, R.string.settings_check_manager_update_summary, null, "settings check manager update settings check manager update summary"),
    SettingSearchEntry(R.string.settings_check_beta_update, R.string.settings_check_beta_update_summary, null, "settings check beta update settings check beta update summary"),
    SettingSearchEntry(R.string.settings_check_module_update, R.string.settings_check_module_update_summary, null, "settings check module update settings check module update summary"),
    SettingSearchEntry(R.string.log_center_title, R.string.log_center_summary, Route.LogCenter, "log center 日志 sulog magica crash metamodule problem log"),
    SettingSearchEntry(R.string.theme_settings, R.string.theme_settings, Route.ThemeSettings, "theme settings theme settings"),
    SettingSearchEntry(R.string.block_modules_title, R.string.block_modules_summary, Route.BlockModules, "block modules title block modules summary"),
    SettingSearchEntry(R.string.kmi_diag_title, R.string.kmi_diag_summary, Route.KmiDiagnostics, "kmi diag title kmi diag summary"),
    SettingSearchEntry(R.string.settings_backup_title, R.string.settings_backup_summary, null, "settings backup title settings backup summary"),
    SettingSearchEntry(R.string.settings_restore_title, R.string.settings_restore_summary, null, "settings restore title settings restore summary"),
    SettingSearchEntry(R.string.manager_config_title, R.string.manager_config_summary, Route.DynamicManager, "manager config 管理器 动态管理器 dynamic manager signature uid slot"),
    SettingSearchEntry(R.string.umount_path_manager, R.string.umount_path_manager_summary, Route.UmountManager, "umount path manager umount path manager summary"),
    SettingSearchEntry(R.string.about, null, Route.About, "about"),
    SettingSearchEntry(R.string.settings_enable_swipe_dismiss, R.string.settings_enable_swipe_dismiss_summary, Route.ThemeSettings, "settings enable swipe dismiss settings enable swipe dismiss summary"),
    SettingSearchEntry(R.string.settings_pager_gesture_mode, null, Route.ThemeSettings, "settings pager gesture mode"),
    SettingSearchEntry(R.string.predictive_back_animation, null, Route.ThemeSettings, "predictive back animation"),
    SettingSearchEntry(R.string.predictive_back_exit_direction, null, Route.ThemeSettings, "predictive back exit direction"),
    SettingSearchEntry(R.string.theme_mode, null, Route.ThemeSettings, "theme mode"),
    SettingSearchEntry(R.string.dynamic_color_title, R.string.dynamic_color_summary, Route.ThemeSettings, "dynamic color title dynamic color summary"),
    SettingSearchEntry(R.string.dynamic_palette_style, null, Route.ThemeSettings, "dynamic palette style"),
    SettingSearchEntry(R.string.dynamic_color_spec, null, Route.ThemeSettings, "dynamic color spec"),
    SettingSearchEntry(R.string.app_dpi_title, R.string.app_dpi_summary, Route.ThemeSettings, "app dpi title app dpi summary"),
    SettingSearchEntry(R.string.enable_floating_bottom_bar, R.string.enable_floating_bottom_bar_summary, Route.ThemeSettings, "enable floating bottom bar enable floating bottom bar summary"),
    SettingSearchEntry(R.string.app_icon_title, R.string.app_icon_summary, Route.AppIcon, "app icon title app icon summary"),
    SettingSearchEntry(R.string.show_more_module_info, R.string.show_more_module_info_summary, Route.ThemeSettings, "show more module info show more module info summary"),
    SettingSearchEntry(R.string.settings_custom_monospace_font, R.string.settings_custom_monospace_font_summary, Route.ThemeSettings, "settings custom monospace font settings custom monospace font summary"),
    SettingSearchEntry(R.string.simple_mode, R.string.simple_mode_summary, Route.ThemeSettings, "simple mode simple mode summary"),
    SettingSearchEntry(R.string.navigation_bar_badge, R.string.navigation_bar_badge_summary, Route.ThemeSettings, "navigation bar badge navigation bar badge summary"),
    SettingSearchEntry(R.string.home_card_icons, R.string.home_card_icons_summary, Route.ThemeSettings, "home card icons home card icons summary"),
    SettingSearchEntry(R.string.theme_color, null, Route.ThemeSettings, "theme color"),
    SettingSearchEntry(R.string.settings_card_alpha, null, Route.ThemeSettings, "settings card alpha"),
    SettingSearchEntry(R.string.settings_background_dim, null, Route.ThemeSettings, "settings background dim"),
)
