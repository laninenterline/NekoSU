package com.nekosu.ui.component

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nekosu.data.theme.ThemeRepository
import com.nekosu.data.wallpaper.WallpaperStore
import com.nekosu.data.wallpaper.rememberWallpaperStore
import com.nekosu.ui.theme.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 个性化壁纸根层入口：在 [content] 底下画一层壁纸（自定义图片 / 内置默认），再叠加
 * 暗度、模糊、可见度，最后才是界面内容。
 *
 * 由 lead 在 Root 层挂载一次（建议包住导航容器）：
 * ```kotlin
 * PersonalizationWallpaperHost {
 *     appContent()
 * }
 * ```
 *
 * - 竖屏用竖屏壁纸；横屏优先用横屏壁纸，未设置时回退竖屏。
 * - 「使用壁纸取色」打开且提取到主色时，会写回 NekoSU 现有主题种子色
 *   （`ThemeRepository.saveSeedColor` + `ThemeConfig.seedColor`）。
 * - 没有壁纸时直接调用 [content]，零开销。
 */
@Composable
fun PersonalizationWallpaperHost(content: @Composable () -> Unit) {
    val store = rememberWallpaperStore()
    val themeConfig = koinInject<ThemeConfig>()
    val themeRepository = koinInject<ThemeRepository>()
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val slot = store.effectiveSlot(landscape)
    val kind = store.kind(slot)

    if (kind == WallpaperStore.KIND_NONE) {
        content()
        return
    }

    val revision = store.revision
    val decoded by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = revision,
        key2 = slot,
    ) {
        value = if (kind == WallpaperStore.KIND_IMAGE) {
            withContext(Dispatchers.IO) { store.loadImage(slot) }
        } else {
            null
        }
    }

    LaunchedEffect(store.seedEnabled, store.seedColor) {
        if (store.seedEnabled && store.seedColor != 0) {
            themeRepository.saveSeedColor(store.seedColor)
            themeConfig.seedColor = store.seedColor
        }
    }

    val blurModifier = if (store.blur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(store.blur.dp, BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }
    val imageModifier = Modifier
        .fillMaxSize()
        .then(blurModifier)
        .alpha(store.visibility.coerceIn(0.2f, 1f))

    Box(Modifier.fillMaxSize()) {
        val bitmap = decoded
        if (kind == WallpaperStore.KIND_IMAGE && bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            // 内置默认壁纸；自定义文件读不出来时也退到这一级（永远画得出东西）。
            Image(
                painter = painterResource(store.builtinRes(slot)),
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )
        }
        if (store.dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = store.dim.coerceIn(0f, 0.9f))),
            )
        }
        content()
    }
}
