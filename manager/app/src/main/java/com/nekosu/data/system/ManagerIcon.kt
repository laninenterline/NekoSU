package com.nekosu.data.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.TypedValue
import java.io.ByteArrayOutputStream

object ManagerIcon {
    private const val MAX_INPUT = 16 * 1024 * 1024
    private const val EDGE = 432

    fun default(context: Context): Bitmap =
        Bitmap.createBitmap(EDGE, EDGE, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(240, 242, 244))
            requireNotNull(context.getDrawable(android.R.drawable.ic_menu_manage)).mutate().apply {
                setTint(Color.rgb(76, 85, 96))
                setBounds(102, 102, EDGE - 102, EDGE - 102)
                draw(canvas)
            }
        }

    fun decode(context: Context, uri: Uri): Bitmap {
        require(uri.scheme == "content") { "Select an image from the image picker" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_INPUT) { "The selected image is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("The selected image cannot be read")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0 && width.toLong() * height <= 64_000_000L &&
            width <= 16_384 && height <= 16_384) { "The selected image dimensions are too large" }
        var sample = 1
        while (width / sample > 1024 || height / sample > 1024) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IllegalArgumentException("The selected image cannot be decoded")
        try {
            val side = minOf(bitmap.width, bitmap.height)
            val left = (bitmap.width - side) / 2
            val top = (bitmap.height - side) / 2
            return Bitmap.createBitmap(EDGE, EDGE, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(
                    bitmap,
                    Rect(left, top, left + side, top + side),
                    Rect(0, 0, EDGE, EDGE),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun png(context: Context, uri: Uri?): ByteArray {
        val bitmap = uri?.let { decode(context, it) } ?: default(context)
        return try {
            ByteArrayOutputStream().also {
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Resolve the APK entry that provides the launcher icon. NekoSU uses an
     * adaptive icon whose foreground is a vector drawable; replacing that entry
     * with the selected PNG turns the foreground into the chosen bitmap while
     * keeping the existing adaptive background.
     */
    fun entry(context: Context, application: ApplicationInfo): String {
        val manager = context.packageManager
        val resources = manager.getResourcesForApplication(application)
        val iconId = application.icon.takeIf { it != 0 }
            ?: resources.getIdentifier("ic_launcher", "mipmap", application.packageName)
        val resourcePackage = if (iconId != 0) {
            resources.getResourcePackageName(iconId)
        } else {
            application.packageName
        }
        fun pathOf(id: Int): String? {
            if (id == 0) return null
            val value = TypedValue()
            resources.getValue(id, value, true)
            return value.string?.toString()?.takeIf {
                it.startsWith("res/") && it.split('/').none { part -> part == ".." }
            }
        }
        return pathOf(resources.getIdentifier("ic_launcher_foreground", "drawable", resourcePackage))
            ?: pathOf(iconId)
            ?: throw IllegalArgumentException("The Manager icon resource is missing")
    }
}
