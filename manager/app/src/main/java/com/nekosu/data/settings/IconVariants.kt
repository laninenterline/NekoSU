package com.nekosu.data.settings

import android.content.ComponentName
import android.content.Context
import com.nekosu.R

/** All real launcher icons the manager ships; switching is done with activity-alias components. */
object IconVariants {
    data class Variant(
        val labelRes: Int,
        val mipmapRes: Int,
        val previewRes: Int,
        val aliasClass: String?,
    )

    private const val MAIN_ACTIVITY = "com.nekosu.ui.MainActivity"

    val variants: List<Variant> = listOf(
        Variant(R.string.app_icon_default, R.mipmap.ic_launcher, R.drawable.ic_launcher_foreground, null),
        Variant(R.string.app_icon_alt, R.mipmap.ic_launcher_alt, R.drawable.ic_launcher_foreground_alt, "com.nekosu.ui.MainActivityAlias"),
        Variant(R.string.app_icon_variant_sukisu, R.mipmap.ic_launcher_alt2, R.mipmap.ic_launcher_alt2, "com.nekosu.ui.MainActivityAlias2"),
        Variant(R.string.app_icon_variant_next, R.mipmap.ic_launcher_alt3, R.mipmap.ic_launcher_alt3, "com.nekosu.ui.MainActivityAlias3"),
        Variant(R.string.app_icon_variant_resuki, R.mipmap.ic_launcher_alt4, R.drawable.ic_launcher_foreground_alt4, "com.nekosu.ui.MainActivityAlias4"),
        Variant(R.string.app_icon_variant_fina, R.mipmap.ic_launcher_alt5, R.drawable.ic_launcher_foreground_alt5, "com.nekosu.ui.MainActivityAlias5"),
    )

    fun coerce(index: Int): Int = index.coerceIn(variants.indices)

    fun componentName(context: Context, index: Int): ComponentName {
        val variant = variants[coerce(index)]
        return ComponentName(context.packageName, variant.aliasClass ?: MAIN_ACTIVITY)
    }

    fun allComponents(context: Context): List<ComponentName> = variants.map { variant ->
        ComponentName(context.packageName, variant.aliasClass ?: MAIN_ACTIVITY)
    }

    fun loadIndex(settings: com.nekosu.data.AppSettingsRepository): Int =
        settings.getInt("icon_variant", if (settings.getBoolean("use_alt_icon", false)) 1 else 0)
}
