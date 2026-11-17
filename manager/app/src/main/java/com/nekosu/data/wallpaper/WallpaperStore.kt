package com.nekosu.data.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 个性化壁纸仓库：竖屏 / 横屏各一张，支持自定义图片（裁剪后落盘）、内置默认壁纸、
 * 恢复默认、移除、暗度、模糊、可见度与取色。
 *
 * 存储分两部分：
 * - 图片文件：`filesDir/personalize_wallpaper/<portrait|landscape>/wallpaper.jpg`
 * - 配置：NekoSU 的 [AppSettingsRepository]（DataStore），key 见 [Keys]。
 *
 * 状态用 Compose `mutableStateOf` 暴露，设置区与本仓库共用同一个进程级实例，
 * 修改后壁纸层会立即重组。根层挂载入口见
 * [com.nekosu.ui.component.PersonalizationWallpaperHost]。
 */
object WallpaperStore {

    enum class Slot(val dirName: String) {
        PORTRAIT("portrait"),
        LANDSCAPE("landscape"),
    }

    const val KIND_NONE = "none"
    const val KIND_IMAGE = "image"
    const val KIND_BUILTIN = "builtin"

    /** 存储 key（报告与迁移时使用）。 */
    object Keys {
        const val PORTRAIT_KIND = "personalize_wallpaper_portrait_kind"
        const val PORTRAIT_PATH = "personalize_wallpaper_portrait_path"
        const val LANDSCAPE_KIND = "personalize_wallpaper_landscape_kind"
        const val LANDSCAPE_PATH = "personalize_wallpaper_landscape_path"
        const val DIM = "personalize_wallpaper_dim"
        const val BLUR = "personalize_wallpaper_blur"
        const val VISIBILITY = "personalize_wallpaper_visibility"
        const val SEED_ENABLED = "personalize_wallpaper_seed_enabled"
        const val SEED_COLOR = "personalize_wallpaper_seed_color"
    }

    val ALL_KEYS: List<String> = listOf(
        Keys.PORTRAIT_KIND,
        Keys.PORTRAIT_PATH,
        Keys.LANDSCAPE_KIND,
        Keys.LANDSCAPE_PATH,
        Keys.DIM,
        Keys.BLUR,
        Keys.VISIBILITY,
        Keys.SEED_ENABLED,
        Keys.SEED_COLOR,
    )

    private var attached = false
    private lateinit var context: Context
    private lateinit var settings: AppSettingsRepository

    /** 修改计数：壁纸文件或类型变化时 +1，渲染侧据此重新解码。 */
    var revision by mutableIntStateOf(0)
        private set

    var portraitKind by mutableStateOf(KIND_NONE)
        private set
    var portraitPath by mutableStateOf("")
        private set
    var landscapeKind by mutableStateOf(KIND_NONE)
        private set
    var landscapePath by mutableStateOf("")
        private set

    /** 0f..0.9f，压暗背景方便阅读。 */
    var dim by mutableFloatStateOf(0.2f)
        private set

    /** 0f..40f（dp），Android 12+ 生效。 */
    var blur by mutableFloatStateOf(0f)
        private set

    /** 0.2f..1f，壁纸层透明度（越高越清晰）。 */
    var visibility by mutableFloatStateOf(1f)
        private set

    var seedEnabled by mutableStateOf(false)
        private set

    /** 从竖屏壁纸提取的主题种子色（0 = 尚未提取 / 不支持）。 */
    var seedColor by mutableIntStateOf(0)
        private set

    fun attach(appContext: Context, appSettings: AppSettingsRepository): WallpaperStore {
        if (!attached) {
            context = appContext.applicationContext
            settings = appSettings
            portraitKind = settings.getString(Keys.PORTRAIT_KIND, KIND_NONE) ?: KIND_NONE
            portraitPath = settings.getString(Keys.PORTRAIT_PATH, "") ?: ""
            landscapeKind = settings.getString(Keys.LANDSCAPE_KIND, KIND_NONE) ?: KIND_NONE
            landscapePath = settings.getString(Keys.LANDSCAPE_PATH, "") ?: ""
            dim = settings.getFloat(Keys.DIM, 0.2f).coerceIn(0f, 0.9f)
            blur = settings.getFloat(Keys.BLUR, 0f).coerceIn(0f, 40f)
            visibility = settings.getFloat(Keys.VISIBILITY, 1f).coerceIn(0.2f, 1f)
            seedEnabled = settings.getBoolean(Keys.SEED_ENABLED, false)
            seedColor = settings.getInt(Keys.SEED_COLOR, 0)
            attached = true
        }
        return this
    }

    /** 在设置仓库完成 preload 后重新读一次落盘值（冷启动首次组合时调用）。 */
    fun refresh() {
        if (!attached) return
        portraitKind = settings.getString(Keys.PORTRAIT_KIND, KIND_NONE) ?: KIND_NONE
        portraitPath = settings.getString(Keys.PORTRAIT_PATH, "") ?: ""
        landscapeKind = settings.getString(Keys.LANDSCAPE_KIND, KIND_NONE) ?: KIND_NONE
        landscapePath = settings.getString(Keys.LANDSCAPE_PATH, "") ?: ""
        dim = settings.getFloat(Keys.DIM, 0.2f).coerceIn(0f, 0.9f)
        blur = settings.getFloat(Keys.BLUR, 0f).coerceIn(0f, 40f)
        visibility = settings.getFloat(Keys.VISIBILITY, 1f).coerceIn(0.2f, 1f)
        seedEnabled = settings.getBoolean(Keys.SEED_ENABLED, false)
        seedColor = settings.getInt(Keys.SEED_COLOR, 0)
    }

    fun kind(slot: Slot): String = if (slot == Slot.PORTRAIT) portraitKind else landscapeKind

    fun path(slot: Slot): String = if (slot == Slot.PORTRAIT) portraitPath else landscapePath

    fun builtinRes(slot: Slot): Int = if (slot == Slot.PORTRAIT) {
        R.drawable.wallpaper_default_portrait
    } else {
        R.drawable.wallpaper_default_landscape
    }

    /** 横屏未单独设置时回退到竖屏那份。 */
    fun effectiveSlot(landscape: Boolean): Slot =
        if (landscape && kind(Slot.LANDSCAPE) != KIND_NONE) Slot.LANDSCAPE else Slot.PORTRAIT

    fun effectiveKind(landscape: Boolean): String = kind(effectiveSlot(landscape))

    /** 当前朝向自己有没有可渲染的壁纸（设置里声明 + 图片文件可读）。 */
    fun hasOwn(slot: Slot): Boolean {
        if (kind(slot) == KIND_NONE) return false
        if (kind(slot) == KIND_BUILTIN) return true
        return isUsable(File(path(slot)))
    }

    fun fileOf(slot: Slot): File? {
        val value = path(slot)
        if (value.isEmpty()) return null
        return File(value)
    }

    suspend fun saveCropped(slot: Slot, uri: Uri): String? {
        val result = withContext(Dispatchers.IO) { copyIntoSlot(slot, uri) }
        val path = result.path
        if (result.error != null || path == null) return result.error
        if (slot == Slot.PORTRAIT) {
            portraitKind = KIND_IMAGE
            portraitPath = path
            settings.putString(Keys.PORTRAIT_KIND, KIND_IMAGE)
            settings.putString(Keys.PORTRAIT_PATH, path)
            seedColor = withContext(Dispatchers.IO) { extractSeedColorFromFile(File(path)) }
            settings.putInt(Keys.SEED_COLOR, seedColor)
        } else {
            landscapeKind = KIND_IMAGE
            landscapePath = path
            settings.putString(Keys.LANDSCAPE_KIND, KIND_IMAGE)
            settings.putString(Keys.LANDSCAPE_PATH, path)
        }
        revision++
        return null
    }

    fun applyBuiltin(slot: Slot) {
        runCatching {
            dirOf(slot).listFiles()?.forEach { it.delete() }
        }
        if (slot == Slot.PORTRAIT) {
            portraitKind = KIND_BUILTIN
            portraitPath = ""
            settings.putString(Keys.PORTRAIT_KIND, KIND_BUILTIN)
            settings.putString(Keys.PORTRAIT_PATH, "")
            seedColor = extractSeedColorFromResource(builtinRes(slot))
            settings.putInt(Keys.SEED_COLOR, seedColor)
        } else {
            landscapeKind = KIND_BUILTIN
            landscapePath = ""
            settings.putString(Keys.LANDSCAPE_KIND, KIND_BUILTIN)
            settings.putString(Keys.LANDSCAPE_PATH, "")
        }
        revision++
    }

    fun clear(slot: Slot) {
        runCatching { dirOf(slot).deleteRecursively() }
        if (slot == Slot.PORTRAIT) {
            portraitKind = KIND_NONE
            portraitPath = ""
            settings.putString(Keys.PORTRAIT_KIND, KIND_NONE)
            settings.putString(Keys.PORTRAIT_PATH, "")
            seedColor = 0
            settings.putInt(Keys.SEED_COLOR, 0)
        } else {
            landscapeKind = KIND_NONE
            landscapePath = ""
            settings.putString(Keys.LANDSCAPE_KIND, KIND_NONE)
            settings.putString(Keys.LANDSCAPE_PATH, "")
        }
        revision++
    }

    fun clearAll() {
        clear(Slot.PORTRAIT)
        clear(Slot.LANDSCAPE)
    }

    fun updateDim(value: Float) {
        dim = value.coerceIn(0f, 0.9f)
        settings.putFloat(Keys.DIM, dim)
    }

    fun updateBlur(value: Float) {
        blur = value.coerceIn(0f, 40f)
        settings.putFloat(Keys.BLUR, blur)
    }

    fun updateVisibility(value: Float) {
        visibility = value.coerceIn(0.2f, 1f)
        settings.putFloat(Keys.VISIBILITY, visibility)
    }

    fun updateSeedEnabled(value: Boolean) {
        seedEnabled = value
        settings.putBoolean(Keys.SEED_ENABLED, value)
    }

    /** 解码自定义壁纸（仅 image 类型）；内置壁纸请直接用 [builtinRes]。 */
    fun loadImage(slot: Slot): ImageBitmap? {
        if (kind(slot) != KIND_IMAGE) return null
        val file = fileOf(slot) ?: return null
        if (!isUsable(file)) return null
        return decodeFile(file)
    }

    private fun dirOf(slot: Slot): File = File(context.filesDir, "personalize_wallpaper/${slot.dirName}")

    private fun targetFile(slot: Slot): File = File(dirOf(slot), "wallpaper.jpg")

    private data class CopyResult(val error: String?, val path: String? = null)

    private fun copyIntoSlot(slot: Slot, uri: Uri): CopyResult = runCatching {
        val dir = dirOf(slot)
        if (!dir.isDirectory && !dir.mkdirs()) return@runCatching CopyResult("storage unavailable")
        val target = targetFile(slot)
        val temp = File(dir, "wallpaper.jpg.part")
        val input = context.contentResolver.openInputStream(uri)
            ?: return@runCatching CopyResult("cannot open image")
        input.use { source -> temp.outputStream().use { sink -> source.copyTo(sink) } }
        if (!isUsable(temp)) {
            temp.delete()
            return@runCatching CopyResult("copied file is not readable")
        }
        if (target.exists() && !target.delete()) {
            temp.delete()
            return@runCatching CopyResult("cannot replace old wallpaper")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            return@runCatching CopyResult("cannot store wallpaper")
        }
        dir.listFiles()?.forEach { candidate ->
            if (candidate.name.startsWith("wallpaper.") && candidate.name != target.name) candidate.delete()
        }
        CopyResult(error = null, path = target.absolutePath)
    }.getOrElse { error -> CopyResult(error.message ?: error.javaClass.simpleName) }

    private fun isUsable(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return runCatching { java.io.FileInputStream(file).use { it.read() } }.isSuccess
    }

    private fun decodeFile(file: File): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
    }.getOrNull()

    private fun extractSeedColorFromFile(file: File?): Int {
        if (file == null || !isUsable(file)) return 0
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / sample > SEED_SAMPLE_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()?.let { pickSeedColor(it) } ?: 0
    }

    private fun extractSeedColorFromResource(resId: Int): Int = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > SEED_SAMPLE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeResource(context.resources, resId, options)
    }.getOrNull()?.let { pickSeedColor(it) } ?: 0

    /**
     * 从图片里挑一个鲜艳度 + 亮度加权的平均色；跳过全黑/全白/过灰像素。
     * 算法：按色相/亮度量化后做加权投票（量化 + 加权平均）。
     */
    private class SeedBucket(
        var weight: Float = 0f,
        var r: Float = 0f,
        var g: Float = 0f,
        var b: Float = 0f,
        var count: Int = 0,
    )

    private fun pickSeedColor(bitmap: Bitmap): Int {
        val buckets = HashMap<Int, SeedBucket>()
        val hsv = FloatArray(3)
        val stepX = (bitmap.width / 64).coerceAtLeast(1)
        val stepY = (bitmap.height / 64).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val alpha = (color ushr 24) and 0xff
                if (alpha >= 200) {
                    val r = (color shr 16) and 0xff
                    val g = (color shr 8) and 0xff
                    val b = color and 0xff
                    android.graphics.Color.RGBToHSV(r, g, b, hsv)
                    val hue = hsv[0]
                    val saturation = hsv[1]
                    val brightness = hsv[2]
                    val usable = brightness >= 0.12f &&
                        saturation >= 0.10f &&
                        !(brightness > 0.96f && saturation < 0.15f)
                    if (usable) {
                        val weight = (saturation * 2.2f + (1f - kotlin.math.abs(brightness - 0.62f))) *
                            (0.4f + saturation)
                        val key = ((hue / 20f).toInt() shl 16) or ((r shr 5) shl 10) or
                            ((g shr 5) shl 5) or (b shr 5)
                        val bucket = buckets.getOrPut(key) { SeedBucket() }
                        bucket.weight += weight
                        bucket.r += r.toFloat()
                        bucket.g += g.toFloat()
                        bucket.b += b.toFloat()
                        bucket.count++
                    }
                }
                x += stepX
            }
            y += stepY
        }
        val best = buckets.values.maxByOrNull { it.weight } ?: return 0
        if (best.count == 0) return 0
        return android.graphics.Color.rgb(
            (best.r / best.count).toInt().coerceIn(0, 255),
            (best.g / best.count).toInt().coerceIn(0, 255),
            (best.b / best.count).toInt().coerceIn(0, 255),
        )
    }

    private const val MAX_SIDE = 2400
    private const val SEED_SAMPLE_SIDE = 64
}

@Composable
fun rememberWallpaperStore(): WallpaperStore {
    val context = LocalContext.current.applicationContext
    val settings = koinInject<AppSettingsRepository>()
    LaunchedEffect(Unit) { WallpaperStore.refresh() }
    return remember(context, settings) { WallpaperStore.attach(context, settings) }
}
