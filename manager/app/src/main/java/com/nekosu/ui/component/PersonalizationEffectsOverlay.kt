package com.nekosu.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nekosu.data.settings.rememberPersonalizationSettings

/**
 * 氛围特效根层入口：按设置叠加「下雪」「巨魔雨」。
 *
 * 由 lead 在 Root 层（页面内容之上、导航条之下或之上均可）挂载一次：
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     appContent()
 *     PersonalizationEffectsOverlay()
 * }
 * ```
 *
 * 两个特效都只在对应开关打开时参与组合，关闭时零开销；它们不拦截触摸（Canvas 默认不消费事件）。
 * 雪花/巨魔的下落方向共享「重力感应」开关与方向校准。
 */
@Composable
fun PersonalizationEffectsOverlay(modifier: Modifier = Modifier) {
    val settings = rememberPersonalizationSettings()
    if (!settings.snowEnabled && !settings.trollEnabled) return
    Box(modifier.fillMaxSize()) {
        if (settings.snowEnabled) {
            SnowfallOverlay(
                sizeScale = settings.snowSize,
                speedScale = settings.snowSpeed,
                tiltEnabled = settings.gravityEnabled,
                gravityMode = settings.gravityMode,
            )
        }
        if (settings.trollEnabled) {
            TrollRainOverlay(
                sizeScale = settings.trollSize,
                speedScale = settings.trollSpeed,
                tiltEnabled = settings.gravityEnabled,
                gravityMode = settings.gravityMode,
            )
        }
    }
}
