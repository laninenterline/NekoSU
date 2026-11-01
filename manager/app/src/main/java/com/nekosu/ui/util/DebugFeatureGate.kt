package com.nekosu.ui.util

import android.content.Context
import com.nekosu.BuildConfig

/**
 * Debug-build only gate for root-related features.
 *
 * On debug builds the root-only entries stay hidden until the app icon in the
 * About screen is tapped 7 times in a row; release builds always show them.
 */
object DebugFeatureGate {
    private const val PREFS = "debug_feature_gate"
    private const val KEY_UNLOCKED = "root_features_unlocked"

    fun rootFeaturesVisible(context: Context): Boolean =
        !BuildConfig.DEBUG || isUnlocked(context)

    fun isUnlocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UNLOCKED, false)

    fun unlock(context: Context) {
        prefs(context).edit().putBoolean(KEY_UNLOCKED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
