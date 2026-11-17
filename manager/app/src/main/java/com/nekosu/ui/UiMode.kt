package com.nekosu.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 界面风格。Material 为默认值，保持 NekoSU 原有观感；Miuix 使用小米 Miuix 组件风格。
 *
 * 该值持久化在 AppSettingsRepository（key = "ui_mode"）中，并通过
 * [LocalUiMode] 提供给整棵 Compose 树。
 */
enum class UiMode(val value: String) {
    Material("material"),
    Miuix("miuix"),
    ;

    companion object {
        val DEFAULT: UiMode = Material

        fun fromValue(value: String?): UiMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.DEFAULT }
