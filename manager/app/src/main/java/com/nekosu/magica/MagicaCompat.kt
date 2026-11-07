package com.nekosu.magica

import android.content.Context
import android.os.Build
import android.os.SystemClock

/**
 * Compatibility and safety helpers for the one-tap "Magica" jailbreak.
 *
 * The Magica bootstrap works by running ksud from an app-zygote child and then
 * rewriting adbd properties to gain an adb root shell. Some OEM ROMs treat such
 * property writes as tampering and force a reboot. This helper:
 *
 *  - flags known-risky vendors so the UI can warn the user first;
 *  - remembers an attempt before it starts, so a reboot during the attempt can
 *    be detected on the next launch and one-tap jailbreak gets disabled instead
 *    of reboot-looping the device.
 */
object MagicaCompat {
    private const val PREFS = "magica_compat"
    private const val KEY_PENDING = "pending_attempt"
    private const val KEY_ATTEMPT_UPTIME = "attempt_uptime"

    private val RISKY_BRANDS = setOf(
        "vivo", "iqoo", "oppo", "realme", "oneplus", "honor", "huawei", "xiaomi", "redmi",
    )

    fun isRiskyDevice(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return RISKY_BRANDS.any { brand.contains(it) || manufacturer.contains(it) }
    }

    fun deviceSummary(): String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

    /** True when the previous attempt was followed by a reboot (uptime reset). */
    fun wasRebootedAfterAttempt(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return false
        val attemptUptime = p.getLong(KEY_ATTEMPT_UPTIME, 0L)
        return SystemClock.elapsedRealtime() < attemptUptime
    }

    fun markAttempt(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_PENDING, true)
            .putLong(KEY_ATTEMPT_UPTIME, SystemClock.elapsedRealtime())
            .apply()
    }

    /** Called as soon as temporary root is detected: the attempt clearly worked. */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).remove(KEY_ATTEMPT_UPTIME).apply()
    }

    /** Consume a detected post-attempt reboot: disable one-tap jailbreak once. */
    fun consumeRebootFlag(context: Context): Boolean {
        if (!wasRebootedAfterAttempt(context)) {
            if (prefs(context).getBoolean(KEY_PENDING, false)) {
                // The device did not reboot; keep the pending flag for the next check.
                return false
            }
            return false
        }
        prefs(context).edit().remove(KEY_PENDING).remove(KEY_ATTEMPT_UPTIME).apply()
        return true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
