package com.nekosu.ui.util

import android.content.Context
import com.nekosu.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug-build only unlock switch.
 *
 * Root detection itself is unchanged: the original isRootAvailable/isFullFeatured
 * values are overridden at their single source (HomeViewModel) when this gate is
 * unlocked on a debug build. Unlocking is persisted and exposed as a flow so the
 * rest of the app follows the one root status.
 */
object DebugFeatureGate {
    private const val PREFS = "debug_feature_gate"
    private const val KEY_UNLOCKED = "root_features_unlocked"

    private var initialized = false
    private val _unlocked = MutableStateFlow(false)
    val unlockedFlow: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        _unlocked.value = prefs(context).getBoolean(KEY_UNLOCKED, false)
        initialized = true
    }

    fun isUnlocked(context: Context): Boolean {
        initialize(context)
        return _unlocked.value
    }

    fun unlock(context: Context) {
        initialize(context)
        if (_unlocked.value) return
        _unlocked.value = true
        prefs(context).edit().putBoolean(KEY_UNLOCKED, true).apply()
    }

    /** true when a debug build should behave like a rooted/full-featured install. */
    val forceRoot: Boolean
        get() = BuildConfig.DEBUG && _unlocked.value

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
