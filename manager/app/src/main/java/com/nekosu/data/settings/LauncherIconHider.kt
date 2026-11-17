package com.nekosu.data.settings

import android.content.Context
import android.content.pm.PackageManager
import com.nekosu.data.AppSettingsRepository

/**
 * Desktop icon visibility.
 *
 * Launcher entry points are activity-alias components; MainActivity itself has no
 * LAUNCHER filter, so disabling every alias hides the icon while the activity can
 * still be started explicitly (secret dial code / internal intents).
 */
object LauncherIconHider {
    const val KEY = "hide_desktop_icon"

    fun isHidden(settings: AppSettingsRepository): Boolean = settings.getBoolean(KEY, false)

    fun setHidden(context: Context, settings: AppSettingsRepository, hidden: Boolean) {
        settings.putBoolean(KEY, hidden)
        if (hidden) {
            disableAll(context)
        } else {
            showVariant(context, settings)
        }
    }

    /** Restore the user's selected icon (used by the secret-code recovery path). */
    fun showSelected(context: Context, settings: AppSettingsRepository) {
        settings.putBoolean(KEY, false)
        showVariant(context, settings)
    }

    fun ensureConsistent(context: Context, settings: AppSettingsRepository) {
        if (isHidden(settings)) {
            disableAll(context)
        } else {
            showVariant(context, settings)
        }
    }

    private fun showVariant(context: Context, settings: AppSettingsRepository) {
        val selected = IconVariants.loadIndex(settings)
        IconVariants.allComponents(context).forEachIndexed { index, component ->
            val state = if (index == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private fun disableAll(context: Context) {
        IconVariants.allComponents(context).forEach { component ->
            context.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
