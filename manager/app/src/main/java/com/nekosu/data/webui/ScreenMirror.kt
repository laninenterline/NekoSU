package com.nekosu.data.webui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Live screen mirroring for the WebUI: the browser shows the real Manager UI (not a
 * re-implementation), so the web version is pixel-identical to the on-device UI.
 * Frames are JPEG-encoded in a background thread and consumed by WebUiHttpServer as MJPEG.
 */
object ScreenMirror {
    private const val TAG = "NekoSU-Mirror"
    private const val TARGET_WIDTH = 1080
    private const val JPEG_QUALITY = 62

    @Volatile var frame: ByteArray? = null
        private set
    @Volatile var frameWidth = 0
        private set
    @Volatile var frameHeight = 0
        private set
    @Volatile var running = false
        private set

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var conversionHandler: Handler? = null

    fun start(context: Context, resultCode: Int, data: android.content.Intent) {
        if (running) return
        val manager = context.getSystemService(MediaProjectionManager::class.java) ?: return
        projection = manager.getMediaProjection(resultCode, data) ?: return
        val metrics = context.resources.displayMetrics
        val srcW = metrics.widthPixels
        val srcH = metrics.heightPixels
        val density = metrics.densityDpi
        val scale = TARGET_WIDTH.toFloat() / srcW
        val w = TARGET_WIDTH
        val h = (srcH * scale).toInt().coerceAtLeast(2)
        frameWidth = w
        frameHeight = h

        thread = HandlerThread("neko-mirror").also { it.start() }
        conversionHandler = Handler(thread!!.looper)
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).also { imageReader ->
            imageReader.setOnImageAvailableListener({ r -> consume(r) }, conversionHandler)
        }

        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }, conversionHandler)

        display = projection!!.createVirtualDisplay(
            "neko-webui-mirror",
            w, h, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface,
            null,
            conversionHandler,
        )
        running = true
        Log.i(TAG, "mirror started ${w}x${h} (source ${srcW}x${srcH})")
    }

    private fun consume(reader: ImageReader) {
        if (!running) return
        val image: Image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            encode(image)
        } finally {
            image.close()
        }
    }

    private fun encode(image: Image) {
        val plane = image.planes.firstOrNull() ?: return
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val bmp = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        val cropped = if (paddedWidth == image.width) bmp else Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
        val out = ByteArrayOutputStream(256 * 1024)
        cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        frame = out.toByteArray()
        if (cropped !== bmp) cropped.recycle()
        bmp.recycle()
    }

    fun stop() {
        running = false
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
        thread?.quitSafely()
        thread = null
        conversionHandler = null
        frame = null
        frameWidth = 0
        frameHeight = 0
        Log.i(TAG, "mirror stopped")
    }
}

/** Bridge the HTTP layer uses for the mirror page and input injection. */
interface WebUiScreenSource {
    fun frame(): ByteArray? = ScreenMirror.frame
    fun size(): IntArray = intArrayOf(ScreenMirror.frameWidth, ScreenMirror.frameHeight)
    fun tap(nx: Float, ny: Float)
    fun swipe(nx1: Float, ny1: Float, nx2: Float, ny2: Float)
}
