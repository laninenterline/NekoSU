package com.nekosu.ui.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.nekosu.BuildConfig

/**
 * Debug-build only gate for root-related features.
 *
 * On debug builds the root-only entries stay hidden until the app icon in the
 * About screen is tapped 7 times in a row; release builds always show them.
 * The unlocked flag is a Compose state so every screen recomposes immediately.
 */
object DebugFeatureGate {
    private const val PREFS = "debug_feature_gate"
    private const val KEY_UNLOCKED = "root_features_unlocked"

    private var loaded = false
    private val unlockedState = mutableStateOf(false)

    fun rootFeaturesVisible(context: Context): Boolean {
        ensureLoaded(context)
        return !BuildConfig.DEBUG || unlockedState.value
    }

    fun isUnlocked(context: Context): Boolean {
        ensureLoaded(context)
        return unlockedState.value
    }

    fun unlock(context: Context) {
        ensureLoaded(context)
        if (unlockedState.value) return
        unlockedState.value = true
        prefs(context).edit().putBoolean(KEY_UNLOCKED, true).apply()
    }

    /** Used by screens that need to react to the unlock without a Context read. */
    val unlocked: Boolean
        get() = unlockedState.value

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        unlockedState.value = prefs(context).getBoolean(KEY_UNLOCKED, false)
        loaded = true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
