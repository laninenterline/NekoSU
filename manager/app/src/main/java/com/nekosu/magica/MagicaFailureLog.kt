package com.nekosu.magica

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Dedicated failure log for the one-tap jailbreak (Magica).
 *
 * Kept separate from the kernel log so a failed attempt can be exported from
 * Settings -> Tools without pulling the whole bug report. The file lives in the
 * app's private files dir; ksud appends its own Magica diagnostics to the same
 * file through the KSU_MAGICA_LOG environment variable.
 */
object MagicaFailureLog {
    private const val FILE_NAME = "magica_failure.log"
    private const val MAX_BYTES = 256 * 1024L

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun read(context: Context): String {
        val f = file(context)
        return if (f.isFile) runCatching { f.readText() }.getOrDefault("") else ""
    }

    fun append(context: Context, message: String) {
        runCatching {
            val f = file(context)
            if (f.isFile && f.length() > MAX_BYTES) {
                // keep the newest half when rotating
                val text = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(text)
            }
            f.appendText("[${timestamp()}] $message\n")
        }
    }

    fun recordAttempt(context: Context) {
        val sb = StringBuilder()
        sb.appendLine("=== jailbreak attempt ===")
        sb.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("android: ${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        sb.appendLine("fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine("build type: ${Build.TYPE} tags=${Build.TAGS}")
        sb.appendLine("risky vendor: ${MagicaCompat.isRiskyDevice()}")
        for (prop in listOf(
            "ro.debuggable", "ro.adb.secure", "ro.boot.verifiedbootstate",
            "ro.build.type", "ro.boot.selinux", "ro.product.manufacturer",
        )) {
            sb.appendLine("$prop=${getprop(prop)}")
        }
        append(context, sb.toString().trimEnd())
    }

    fun recordTimeout(context: Context) =
        append(context, "jailbreak timeout (30s): manager was not restarted, attempt likely failed")

    fun recordRebootDetected(context: Context) =
        append(context, "reboot detected after attempt; one-tap jailbreak disabled until root is available")

    fun recordRootAvailable(context: Context) =
        append(context, "temporary root detected, attempt finished")

    private fun getprop(name: String): String = runCatching {
        val p = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        p.waitFor(2, TimeUnit.SECONDS)
        p.inputStream.bufferedReader().readText().trim()
    }.getOrDefault("?")

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
