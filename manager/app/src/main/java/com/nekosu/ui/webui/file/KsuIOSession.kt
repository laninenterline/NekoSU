package com.nekosu.ui.webui.file

import android.webkit.JavascriptInterface
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

internal const val TAG = "KsuIO"

/**
 * Per-WebUI-Activity `ksu.io` session.
 *
 * NekoSU can have more than one module WebUI Activity alive (one per module), and each Activity has
 * its own JS bridge and its own `ksu.io()` object. Keeping the root shell and the open-stream tables
 * in this instance (instead of a process-wide singleton) means:
 * - a slow file operation in one module WebUI can never block another WebUI's file API;
 * - disposing one WebUI closes only that WebUI's streams and shell;
 * - the shell is still dedicated to this session (created through [rootShellProvider], never the
 *   manager's shared global-mount shell), so libsu's per-shell command queue stays isolated from
 *   exec/background work as well.
 */
class KsuIOSession(
    private val rootShellProvider: () -> Shell,
) {
    private companion object {
        const val KSU_IO_VERSION = 1
        const val MAX_OPEN_STREAMS = 50
    }

    internal val openInputStreams = ConcurrentHashMap<String, ManagedInputStream>()
    internal val openOutputStreams = ConcurrentHashMap<String, OutputStream>()

    /** Dedicated libsu shell owned by this session; created on first use and closed in [destroy]. */
    @Volatile
    private var fileShell: Shell? = null

    private val fileOutputStream = FileOutputStreamInterface(this)
    private val fileInputStream = FileInputStreamInterface(this)
    private val randomAccessFile = RandomAccessFileInterface(this)

    internal fun rootShell(): Shell {
        fileShell?.let { shell -> if (shell.isAlive) return shell }
        synchronized(this) {
            fileShell?.let { shell -> if (shell.isAlive) return shell }
            val shell = rootShellProvider()
            fileShell = shell
            return shell
        }
    }

    internal fun suFile(path: String): SuFile = SuFile(path).apply { shell = rootShell() }

    @JavascriptInterface
    fun version(): Int = KSU_IO_VERSION

    @JavascriptInterface
    fun File(path: String): FileInterface = FileInterface(this, path)

    @JavascriptInterface
    fun FileInputStream(): FileInputStreamInterface = fileInputStream

    @JavascriptInterface
    fun FileOutputStream(): FileOutputStreamInterface = fileOutputStream

    @JavascriptInterface
    fun RandomAccessFile(): RandomAccessFileInterface = randomAccessFile

    /**
     * Check if we can open a new stream without exceeding resource limits.
     * Returns true if under limit, false otherwise.
     */
    internal fun canOpenStream(): Boolean {
        return openInputStreams.size + openOutputStreams.size < MAX_OPEN_STREAMS
    }

    fun destroy() {
        fileInputStream.closeAll()
        fileOutputStream.closeAll()
        randomAccessFile.closeAll()
        synchronized(this) {
            runCatching { fileShell?.close() }
            fileShell = null
        }
    }
}
