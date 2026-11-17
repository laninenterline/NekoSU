package com.nekosu.data.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nekosu.data.AppSettingsRepository
import org.koin.compose.koinInject

/**
 * 氛围特效 / 扁平化开关的持久化与响应式状态。
 *
 * 与壁纸仓库一样是进程级单例：设置区写入后，根层挂载的
 * [com.nekosu.ui.component.PersonalizationEffectsOverlay] 会立即重组生效。
 */
object PersonalizationSettings {

    /** 存储 key（报告与迁移时使用）。 */
    object Keys {
        const val SNOW_ENABLED = "personalize_snow_enabled"
        const val SNOW_SIZE = "personalize_snow_size"
        const val SNOW_SPEED = "personalize_snow_speed"
        const val TROLL_ENABLED = "personalize_troll_rain_enabled"
        const val TROLL_SIZE = "personalize_troll_rain_size"
        const val TROLL_SPEED = "personalize_troll_rain_speed"
        const val GRAVITY_ENABLED = "personalize_gravity_enabled"
        const val GRAVITY_MODE = "personalize_gravity_mode"
        const val FLAT_UI = "personalize_flat_ui"
    }

    val ALL_KEYS: List<String> = listOf(
        Keys.SNOW_ENABLED,
        Keys.SNOW_SIZE,
        Keys.SNOW_SPEED,
        Keys.TROLL_ENABLED,
        Keys.TROLL_SIZE,
        Keys.TROLL_SPEED,
        Keys.GRAVITY_ENABLED,
        Keys.GRAVITY_MODE,
        Keys.FLAT_UI,
    )

    private var attached = false
    private lateinit var settings: AppSettingsRepository

    var snowEnabled by mutableStateOf(false)
        private set
    var snowSize by mutableFloatStateOf(1f)
        private set
    var snowSpeed by mutableFloatStateOf(1f)
        private set
    var trollEnabled by mutableStateOf(false)
        private set
    var trollSize by mutableFloatStateOf(1f)
        private set
    var trollSpeed by mutableFloatStateOf(1f)
        private set
    var gravityEnabled by mutableStateOf(false)
        private set

    /** 0 = 默认，1 = 左右翻转，2 = 上下翻转，3 = 180° 反转。 */
    var gravityMode by mutableIntStateOf(0)
        private set
    var flatUi by mutableStateOf(false)
        private set

    fun attach(appSettings: AppSettingsRepository): PersonalizationSettings {
        if (!attached) {
            settings = appSettings
            snowEnabled = settings.getBoolean(Keys.SNOW_ENABLED, false)
            snowSize = settings.getFloat(Keys.SNOW_SIZE, 1f).coerceIn(0.4f, 2.5f)
            snowSpeed = settings.getFloat(Keys.SNOW_SPEED, 1f).coerceIn(0.2f, 3f)
            trollEnabled = settings.getBoolean(Keys.TROLL_ENABLED, false)
            trollSize = settings.getFloat(Keys.TROLL_SIZE, 1f).coerceIn(0.4f, 2.5f)
            trollSpeed = settings.getFloat(Keys.TROLL_SPEED, 1f).coerceIn(0.2f, 3f)
            gravityEnabled = settings.getBoolean(Keys.GRAVITY_ENABLED, false)
            gravityMode = settings.getInt(Keys.GRAVITY_MODE, 0).coerceIn(0, 3)
            flatUi = settings.getBoolean(Keys.FLAT_UI, false)
            attached = true
        }
        return this
    }

    /** 在设置仓库完成 preload 后重新读一次落盘值（冷启动首次组合时调用）。 */
    fun refresh() {
        if (!attached) return
        snowEnabled = settings.getBoolean(Keys.SNOW_ENABLED, false)
        snowSize = settings.getFloat(Keys.SNOW_SIZE, 1f).coerceIn(0.4f, 2.5f)
        snowSpeed = settings.getFloat(Keys.SNOW_SPEED, 1f).coerceIn(0.2f, 3f)
        trollEnabled = settings.getBoolean(Keys.TROLL_ENABLED, false)
        trollSize = settings.getFloat(Keys.TROLL_SIZE, 1f).coerceIn(0.4f, 2.5f)
        trollSpeed = settings.getFloat(Keys.TROLL_SPEED, 1f).coerceIn(0.2f, 3f)
        gravityEnabled = settings.getBoolean(Keys.GRAVITY_ENABLED, false)
        gravityMode = settings.getInt(Keys.GRAVITY_MODE, 0).coerceIn(0, 3)
        flatUi = settings.getBoolean(Keys.FLAT_UI, false)
    }

    fun updateSnowEnabled(value: Boolean) {
        snowEnabled = value
        settings.putBoolean(Keys.SNOW_ENABLED, value)
    }

    fun updateSnowSize(value: Float) {
        snowSize = value.coerceIn(0.4f, 2.5f)
        settings.putFloat(Keys.SNOW_SIZE, snowSize)
    }

    fun updateSnowSpeed(value: Float) {
        snowSpeed = value.coerceIn(0.2f, 3f)
        settings.putFloat(Keys.SNOW_SPEED, snowSpeed)
    }

    fun updateTrollEnabled(value: Boolean) {
        trollEnabled = value
        settings.putBoolean(Keys.TROLL_ENABLED, value)
    }

    fun updateTrollSize(value: Float) {
        trollSize = value.coerceIn(0.4f, 2.5f)
        settings.putFloat(Keys.TROLL_SIZE, trollSize)
    }

    fun updateTrollSpeed(value: Float) {
        trollSpeed = value.coerceIn(0.2f, 3f)
        settings.putFloat(Keys.TROLL_SPEED, trollSpeed)
    }

    fun updateGravityEnabled(value: Boolean) {
        gravityEnabled = value
        settings.putBoolean(Keys.GRAVITY_ENABLED, value)
    }

    fun updateGravityMode(value: Int) {
        gravityMode = value.coerceIn(0, 3)
        settings.putInt(Keys.GRAVITY_MODE, gravityMode)
    }

    fun updateFlatUi(value: Boolean) {
        flatUi = value
        settings.putBoolean(Keys.FLAT_UI, value)
    }
}

@Composable
fun rememberPersonalizationSettings(): PersonalizationSettings {
    val settings = koinInject<AppSettingsRepository>()
    LaunchedEffect(Unit) { PersonalizationSettings.refresh() }
    return remember(settings) { PersonalizationSettings.attach(settings) }
}

/** 供 Root 层消费「界面扁平化」开关的只读入口。 */
@Composable
fun rememberFlatUiEnabled(): Boolean = rememberPersonalizationSettings().flatUi

