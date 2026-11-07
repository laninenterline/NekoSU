package com.nekosu.ui.util

import android.os.Build
import com.nekosu.KernelVersion

/** One embedded LKM variant: android<recipe> + kernel <major>.<minor>. */
data class KmiEntry(val kmi: String, val androidN: Int, val major: Int, val minor: Int)

val embeddedKmis = listOf(
    KmiEntry("android11-5.4", 11, 5, 4),
    KmiEntry("android12-5.10", 12, 5, 10),
    KmiEntry("android13-5.10", 13, 5, 10),
    KmiEntry("android13-5.15", 13, 5, 15),
    KmiEntry("android14-5.15", 14, 5, 15),
    KmiEntry("android14-6.1", 14, 6, 1),
    KmiEntry("android15-6.6", 15, 6, 6),
    KmiEntry("android16-6.12", 16, 6, 12),
    KmiEntry("android17-6.18", 17, 6, 18),
)

private val androidTagRegex = Regex("android(\\d+)")

/**
 * Pick the embedded KMI for a running kernel.
 *
 * GKI names embed the Android recipe in the release string (e.g.
 * `5.10.245-android13-8-...`). Some vendor kernels drop that tag, so fall back
 * to the Android SDK level when the release string does not carry it.
 */
fun matchKmi(
    version: KernelVersion,
    release: String = "",
    sdk: Int = Build.VERSION.SDK_INT,
): KmiEntry? {
    val candidates = embeddedKmis.filter { it.major == version.major && it.minor == version.patchLevel }
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.single()

    val tagAndroid = androidTagRegex.find(release)?.groupValues?.get(1)?.toIntOrNull()
    val sdkAndroid = when {
        sdk == 30 -> 11
        sdk in 31..32 -> 12
        sdk == 33 -> 13
        sdk == 34 -> 14
        sdk == 35 -> 15
        sdk == 36 -> 16
        sdk >= 37 -> 17
        else -> null
    }
    val wanted = tagAndroid ?: sdkAndroid
    return candidates.firstOrNull { it.androidN == wanted } ?: candidates.first()
}
