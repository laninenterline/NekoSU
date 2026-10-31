package com.nekosu.ui.util

import com.nekosu.KernelVersion

data class KmiEntry(val kmi: String, val major: Int, val minor: Int)

val embeddedKmis = listOf(
    KmiEntry("android14-6.1", 6, 1),
    KmiEntry("android15-6.6", 6, 6),
    KmiEntry("android16-6.12", 6, 12),
    KmiEntry("android17-6.18", 6, 18),
)

fun matchKmi(version: KernelVersion): KmiEntry? =
    embeddedKmis.firstOrNull { it.major == version.major && it.minor == version.patchLevel }
