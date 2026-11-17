package com.nekosu.ui.screen.themeSettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AcUnit
import androidx.compose.material.icons.twotone.BlurOn
import androidx.compose.material.icons.twotone.Campaign
import androidx.compose.material.icons.twotone.Contrast
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Grain
import androidx.compose.material.icons.twotone.Opacity
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.ScreenRotation
import androidx.compose.material.icons.twotone.Style
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.material.icons.twotone.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.settings.AnnouncementPrefs
import com.nekosu.data.settings.rememberPersonalizationSettings
import com.nekosu.data.wallpaper.WallpaperStore
import com.nekosu.data.wallpaper.rememberWallpaperStore
import com.nekosu.ui.component.KeyPointSlider
import com.nekosu.ui.component.settings.SegmentedColumn
import com.nekosu.ui.component.settings.SettingsBaseWidget
import com.nekosu.ui.component.settings.SettingsChooseWidget
import com.nekosu.ui.component.settings.SettingsSwitchWidget
import com.nekosu.ui.screen.themeSettings.crop.BackgroundCropActivity
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 个性化设置区（壁纸 / 氛围特效 / 扁平化 / 公告）。
 *
 * 只提供 Composable，由 lead 插入到主题设置的 LazyColumn 中（不要直接改
 * SettingsPage.kt / SettingsMiuix.kt / ThemeSettings.kt）。典型用法：
 * ```kotlin
 * item { PersonalizationSettingsSection() }
 * ```
 *
 * 状态持久化 key 见 [WallpaperStore.Keys]、[com.nekosu.data.settings.PersonalizationSettings.Keys]
 * 与 [AnnouncementPrefs.ALL_KEYS]。
 */
@Composable
fun PersonalizationSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appSettings = koinInject<AppSettingsRepository>()
    val store = rememberWallpaperStore()
    val personalization = rememberPersonalizationSettings()
    val scope = rememberCoroutineScope()

    var pendingSlot by remember { mutableStateOf(WallpaperStore.Slot.PORTRAIT) }
    var neverShowAnnouncement by remember {
        mutableStateOf(AnnouncementPrefs.neverShow(appSettings))
    }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val output = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getExtraCompat<Uri>(UCrop.EXTRA_OUTPUT_URI)
        } else {
            null
        }
        if (output == null) {
            if (result.resultCode != Activity.RESULT_CANCELED) {
                toast(context, context.getString(R.string.personalize_wallpaper_crop_failed))
            }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val error = store.saveCropped(pendingSlot, output)
            toast(
                context,
                if (error == null) {
                    context.getString(R.string.personalize_wallpaper_saved)
                } else {
                    context.getString(R.string.personalize_wallpaper_save_failed, error)
                },
            )
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            toast(context, context.getString(R.string.personalize_wallpaper_pick_failed))
            return@rememberLauncherForActivityResult
        }
        val outputUri = createCropOutputUri(context)
        val portrait = pendingSlot == WallpaperStore.Slot.PORTRAIT
        val aspectX = if (portrait) 9f else 16f
        val aspectY = if (portrait) 16f else 9f
        val maxX = if (portrait) 1440 else 2560
        val maxY = if (portrait) 2560 else 1440
        val intent = Intent(context, BackgroundCropActivity::class.java).apply {
            putExtra(UCrop.EXTRA_INPUT_URI, uri)
            putExtra(UCrop.EXTRA_OUTPUT_URI, outputUri)
            putExtra(UCrop.EXTRA_ASPECT_RATIO_X, aspectX)
            putExtra(UCrop.EXTRA_ASPECT_RATIO_Y, aspectY)
            putExtra(UCrop.EXTRA_MAX_SIZE_X, maxX)
            putExtra(UCrop.EXTRA_MAX_SIZE_Y, maxY)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        cropLauncher.launch(intent)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentedColumn(title = stringResource(R.string.personalize_wallpaper_group)) {
            item {
                SettingsBaseWidget(
                    icon = Icons.TwoTone.Wallpaper,
                    title = stringResource(R.string.personalize_wallpaper_portrait),
                    description = slotSummary(store, WallpaperStore.Slot.PORTRAIT),
                    onClick = {
                        pendingSlot = WallpaperStore.Slot.PORTRAIT
                        pickLauncher.launch("image/*")
                    },
                )
            }
            item {
                SettingsBaseWidget(
                    icon = Icons.TwoTone.ScreenRotation,
                    title = stringResource(R.string.personalize_wallpaper_landscape),
                    description = slotSummary(store, WallpaperStore.Slot.LANDSCAPE),
                    onClick = {
                        pendingSlot = WallpaperStore.Slot.LANDSCAPE
                        pickLauncher.launch("image/*")
                    },
                )
            }
            item {
                SettingsChooseWidget(
                    icon = Icons.TwoTone.Contrast,
                    title = stringResource(R.string.personalize_wallpaper_dim),
                    description = stringResource(R.string.personalize_wallpaper_dim_summary),
                    items = levelLabels(),
                    selectedIndex = nearestIndex(DIM_LEVELS, store.dim),
                    onSelectedIndexChange = { store.updateDim(DIM_LEVELS[it]) },
                )
            }
            item {
                SettingsChooseWidget(
                    icon = Icons.TwoTone.BlurOn,
                    title = stringResource(R.string.personalize_wallpaper_blur),
                    description = stringResource(R.string.personalize_wallpaper_blur_summary),
                    items = levelLabels(),
                    selectedIndex = nearestIndex(BLUR_LEVELS, store.blur),
                    onSelectedIndexChange = { store.updateBlur(BLUR_LEVELS[it]) },
                )
            }
            item {
                SettingsBaseWidget(
                    icon = Icons.TwoTone.Opacity,
                    title = stringResource(R.string.personalize_wallpaper_visibility),
                    description = stringResource(R.string.personalize_wallpaper_visibility_summary),
                    descriptionColumnContent = {
                        KeyPointSlider(
                            value = store.visibility,
                            onValueChange = store::updateVisibility,
                            valueRange = 0.2f..1f,
                            keyPoints = listOf(0.5f, 0.75f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    trailingContent = { _ ->
                        Text(
                            "${(store.visibility * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
            item {
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.Palette,
                    title = stringResource(R.string.personalize_wallpaper_seed),
                    description = if (store.portraitKind == WallpaperStore.KIND_NONE) {
                        stringResource(R.string.personalize_wallpaper_seed_custom_required)
                    } else {
                        stringResource(R.string.personalize_wallpaper_seed_summary)
                    },
                    checked = store.seedEnabled,
                    enabled = store.portraitKind != WallpaperStore.KIND_NONE,
                    onCheckedChange = store::updateSeedEnabled,
                )
            }
            item {
                SettingsBaseWidget(
                    icon = Icons.TwoTone.Wallpaper,
                    title = stringResource(R.string.personalize_wallpaper_restore),
                    description = stringResource(R.string.personalize_wallpaper_restore_summary),
                    onClick = {
                        store.applyBuiltin(WallpaperStore.Slot.PORTRAIT)
                        store.applyBuiltin(WallpaperStore.Slot.LANDSCAPE)
                        toast(context, context.getString(R.string.personalize_wallpaper_saved))
                    },
                )
            }
            item {
                SettingsBaseWidget(
                    icon = Icons.TwoTone.Delete,
                    title = stringResource(R.string.personalize_wallpaper_remove),
                    description = stringResource(R.string.personalize_wallpaper_remove_summary),
                    onClick = { store.clearAll() },
                )
            }
        }

        SegmentedColumn(title = stringResource(R.string.personalize_effects_group)) {
            item {
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.AcUnit,
                    title = stringResource(R.string.personalize_effects_snow),
                    description = stringResource(R.string.personalize_effects_snow_summary),
                    checked = personalization.snowEnabled,
                    onCheckedChange = personalization::updateSnowEnabled,
                )
            }
            item(visible = personalization.snowEnabled) {
                SliderRow(
                    icon = Icons.TwoTone.AcUnit,
                    title = stringResource(R.string.personalize_effects_snow_size),
                    value = personalization.snowSize,
                    range = 0.4f..2.5f,
                    onValueChange = personalization::updateSnowSize,
                )
            }
            item(visible = personalization.snowEnabled) {
                SliderRow(
                    icon = Icons.TwoTone.AcUnit,
                    title = stringResource(R.string.personalize_effects_snow_speed),
                    value = personalization.snowSpeed,
                    range = 0.2f..3f,
                    onValueChange = personalization::updateSnowSpeed,
                )
            }
            item {
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.Grain,
                    title = stringResource(R.string.personalize_effects_troll),
                    description = stringResource(R.string.personalize_effects_troll_summary),
                    checked = personalization.trollEnabled,
                    onCheckedChange = personalization::updateTrollEnabled,
                )
            }
            item(visible = personalization.trollEnabled) {
                SliderRow(
                    icon = Icons.TwoTone.Grain,
                    title = stringResource(R.string.personalize_effects_troll_size),
                    value = personalization.trollSize,
                    range = 0.4f..2.5f,
                    onValueChange = personalization::updateTrollSize,
                )
            }
            item(visible = personalization.trollEnabled) {
                SliderRow(
                    icon = Icons.TwoTone.Grain,
                    title = stringResource(R.string.personalize_effects_troll_speed),
                    value = personalization.trollSpeed,
                    range = 0.2f..3f,
                    onValueChange = personalization::updateTrollSpeed,
                )
            }
            item {
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.Vibration,
                    title = stringResource(R.string.personalize_effects_gravity),
                    description = stringResource(R.string.personalize_effects_gravity_summary),
                    checked = personalization.gravityEnabled,
                    onCheckedChange = personalization::updateGravityEnabled,
                )
            }
            item(visible = personalization.gravityEnabled) {
                SettingsChooseWidget(
                    icon = Icons.TwoTone.Vibration,
                    title = stringResource(R.string.personalize_effects_gravity_calibration),
                    items = listOf(
                        stringResource(R.string.personalize_gravity_mode_default),
                        stringResource(R.string.personalize_gravity_mode_flip_x),
                        stringResource(R.string.personalize_gravity_mode_flip_y),
                        stringResource(R.string.personalize_gravity_mode_invert),
                    ),
                    selectedIndex = personalization.gravityMode,
                    onSelectedIndexChange = personalization::updateGravityMode,
                )
            }
        }

        SegmentedColumn(title = stringResource(R.string.personalize_ui_group)) {
            item {
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.Style,
                    title = stringResource(R.string.personalize_flat_ui),
                    description = stringResource(R.string.personalize_flat_ui_summary),
                    checked = personalization.flatUi,
                    onCheckedChange = personalization::updateFlatUi,
                )
            }
        }

        SegmentedColumn(title = stringResource(R.string.personalize_announcement_group)) {
            item {
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.Campaign,
                    title = stringResource(R.string.personalize_announcement_never_show),
                    description = stringResource(R.string.personalize_announcement_never_show_summary),
                    checked = neverShowAnnouncement,
                    onCheckedChange = { value ->
                        neverShowAnnouncement = value
                        AnnouncementPrefs.setNeverShow(appSettings, value)
                    },
                )
            }
        }

    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    SettingsBaseWidget(
        icon = icon,
        title = title,
        descriptionColumnContent = {
            KeyPointSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        trailingContent = { _ ->
            Text(
                "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
            )
        },
    )
}

@Composable
private fun slotSummary(store: WallpaperStore, slot: WallpaperStore.Slot): String = when (store.kind(slot)) {
    WallpaperStore.KIND_IMAGE -> stringResource(R.string.personalize_wallpaper_kind_image)
    WallpaperStore.KIND_BUILTIN -> stringResource(R.string.personalize_wallpaper_kind_builtin)
    else -> if (slot == WallpaperStore.Slot.LANDSCAPE) {
        stringResource(R.string.personalize_wallpaper_kind_none_landscape)
    } else {
        stringResource(R.string.personalize_wallpaper_kind_none)
    }
}

@Composable
private fun levelLabels(): List<String> = listOf(
    stringResource(R.string.personalize_level_off),
    stringResource(R.string.personalize_level_light),
    stringResource(R.string.personalize_level_medium),
    stringResource(R.string.personalize_level_strong),
)

private fun nearestIndex(levels: FloatArray, value: Float): Int {
    var best = 0
    var distance = Float.MAX_VALUE
    levels.forEachIndexed { index, level ->
        val current = kotlin.math.abs(level - value)
        if (current < distance) {
            best = index
            distance = current
        }
    }
    return best
}

private fun createCropOutputUri(context: Context): Uri {
    val output = File(context.cacheDir, "personalize_wallpaper_crop_${System.currentTimeMillis()}.jpg").apply {
        parentFile?.mkdirs()
        delete()
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private inline fun <reified T> Intent.getExtraCompat(name: String): T? = if (
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
) {
    getParcelableExtra(name, T::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(name)
}

private val DIM_LEVELS = floatArrayOf(0f, 0.2f, 0.45f, 0.7f)
private val BLUR_LEVELS = floatArrayOf(0f, 8f, 20f, 35f)
