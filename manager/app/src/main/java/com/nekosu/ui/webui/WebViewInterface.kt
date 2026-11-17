package com.nekosu.ui.webui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.Window
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nekosu.R
import com.nekosu.data.download.DownloadService
import com.nekosu.data.packageinfo.InstalledPackageRepository
import com.nekosu.data.webui.WebUiRepository
import com.nekosu.ui.webui.file.KsuIOSession
import com.nekosu.ui.webui.pm.KsuPm
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

@Suppress("unused")
class WebViewInterface(
    private val state: WebUIState,
    private val packageRepository: InstalledPackageRepository,
    private val webUiRepository: WebUiRepository,
) {
    private val webView get() = state.webView!!
    private val modDir get() = state.modDir

    /**
     * Per-Activity `ksu.io` state: its own root shell + stream tables. Stacked module WebUIs get
     * independent file APIs, so a slow read in one page can never block another page and disposing
     * one WebUI only closes its own streams/shell.
     */
    private val ioSession = KsuIOSession { webUiRepository.createFileShell() }

    init {
        // ksu.pm data is process-wide, so it only needs the shared package inventory.
        KsuPm.initialize(packageRepository)
    }

    @JavascriptInterface
    fun exec(cmd: String): String = webUiRepository.execute(cmd).stdout

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String) {
        exec(cmd, null, callbackFunc)
    }

    private fun processOptions(sb: StringBuilder, options: String?) {
        val opts = if (options == null) {
            JSONObject()
        } else {
            JSONObject(options)
        }

        val cwd = opts.optString("cwd")
        if (!TextUtils.isEmpty(cwd)) {
            sb.append("cd $cwd;")
        }

        opts.optJSONObject("env")?.let { env ->
            env.keys().forEach { key ->
                sb.append("export $key=${env.getString(key)};")
            }
        }
    }

    @JavascriptInterface
    fun exec(
        cmd: String,
        options: String?,
        callbackFunc: String,
    ) {
        val finalCommand = StringBuilder()
        processOptions(finalCommand, options)
        finalCommand.append(cmd)

        val result = webUiRepository.execute(finalCommand.toString())
        val stdout = result.stdout
        val stderr = result.stderr

        val jsCode =
            "javascript: (function() { try { $callbackFunc(${result.code}, ${
                JSONObject.quote(
                    stdout,
                )
            }, ${JSONObject.quote(stderr)}); } catch(e) { console.error(e); } })();"
        webView.post {
            webView.loadUrl(jsCode)
        }
    }

    @JavascriptInterface
    fun spawn(command: String, args: String, options: String?, callbackFunc: String) {
        val finalCommand = StringBuilder()

        processOptions(finalCommand, options)

        if (!TextUtils.isEmpty(args)) {
            finalCommand.append(command).append(" ")
            JSONArray(args).let { argsArray ->
                for (i in 0 until argsArray.length()) {
                    finalCommand.append(argsArray.getString(i))
                    finalCommand.append(" ")
                }
            }
        } else {
            finalCommand.append(command)
        }

        val emitData = fun(name: String, data: String) {
            val jsCode =
                "javascript: (function() { try { $callbackFunc.$name.emit('data', ${
                    JSONObject.quote(
                        data,
                    )
                }); } catch(e) { console.error('emitData', e); } })();"
            webView.post {
                webView.loadUrl(jsCode)
            }
        }

        val process = webUiRepository.spawn(finalCommand.toString(), true)
        process.start(
            onStdout = { emitData("stdout", it) },
            onStderr = { emitData("stderr", it) },
            onComplete = { result ->
                val emitExitCode =
                    "javascript: (function() { try { $callbackFunc.emit('exit', ${result.code}); } catch(e) { console.error(`emitExit error: \${e}`); } })();"
                webView.post {
                    webView.loadUrl(emitExitCode)
                }

                if (result.code != 0) {
                    val emitErrCode =
                        "javascript: (function() { try { var err = new Error(); err.exitCode = ${result.code}; err.message = ${
                            JSONObject.quote(
                                result.stderr,
                            )
                        };$callbackFunc.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"
                    webView.post {
                        webView.loadUrl(emitErrCode)
                    }
                }
            },
        )
    }

    @JavascriptInterface
    fun toast(msg: String) {
        webView.post {
            Toast.makeText(webView.context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun fullScreen(enable: Boolean) {
        val context = webView.context
        if (context is Activity) {
            Handler(Looper.getMainLooper()).post {
                if (enable) {
                    hideSystemUI(context.window)
                } else {
                    showSystemUI(context.window)
                }
            }
        }
        enableEdgeToEdge(enable)
    }

    @JavascriptInterface
    fun enableInsets(enable: Boolean = true) {
        enableEdgeToEdge(enable)
    }

    @JavascriptInterface
    fun enableEdgeToEdge(enable: Boolean = true) {
        state.isInsetsEnabled = enable
    }

    @JavascriptInterface
    fun moduleInfo(): String {
        val moduleInfos = JSONArray(webUiRepository.listModules())
        val currentModuleInfo = JSONObject()
        currentModuleInfo.put("moduleDir", modDir)
        val moduleId = File(modDir).name
        for (i in 0 until moduleInfos.length()) {
            val currentInfo = moduleInfos.getJSONObject(i)

            if (currentInfo.getString("id") != moduleId) {
                continue
            }

            val keys = currentInfo.keys()
            for (key in keys) {
                currentModuleInfo.put(key, currentInfo.get(key))
            }
            break
        }
        return currentModuleInfo.toString()
    }

    @Deprecated("Use ksu.pm().listPackages() instead")
    @JavascriptInterface
    fun listPackages(type: String): String {
        val packageNames = packageRepository.packages.value
            .filter { packageInfo ->
                when (type.lowercase()) {
                    "system" -> packageInfo.isSystem
                    "user" -> !packageInfo.isSystem
                    else -> true
                }
            }
            .map { it.packageName }
            .sorted()

        val jsonArray = JSONArray()
        for (pkgName in packageNames) {
            jsonArray.put(pkgName)
        }
        return jsonArray.toString()
    }

    @Deprecated("Use ksu.pm().getPackagesInfo() instead")
    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val jsonArray = JSONArray()
        val appMap = packageRepository.packages.value.associateBy { it.packageName }
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            val pkg = appMap[pkgName]
            if (pkg != null) {
                val obj = JSONObject()
                obj.put("packageName", pkg.packageName)
                obj.put("versionName", pkg.versionName)
                obj.put("versionCode", pkg.versionCode)
                obj.put("appLabel", pkg.appLabel)
                obj.put("isSystem", pkg.isSystem)
                obj.put("uid", pkg.uid)
                jsonArray.put(obj)
            } else {
                val obj = JSONObject()
                obj.put("packageName", pkgName)
                obj.put("error", "Package not found or inaccessible")
                jsonArray.put(obj)
            }
        }
        return jsonArray.toString()
    }

    @JavascriptInterface
    fun exit() {
        state.requestExit()
    }

    @JavascriptInterface
    fun io() = ioSession

    @JavascriptInterface
    fun pm() = KsuPm

    fun destroy() {
        ioSession.destroy()
    }
}

fun hideSystemUI(window: Window) = WindowInsetsControllerCompat(window, window.decorView).let { controller ->
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

fun showSystemUI(window: Window) = WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())

/**
 * WebUI 的下载与外部链接 JS 接口，注册名为 `ksu_download`。
 *
 * 移植自上游 KernelSU 的 WebUIDownloadInterface，并按 NekoSU 的下载目录适配：
 * - API 29+ 写入系统下载目录（MediaStore.Downloads，无需存储权限）；
 * - API 26~28 在获得 WRITE_EXTERNAL_STORAGE 后写入公共 Download 目录（与 29+ 行为一致），
 *   权限缺失或外置存储不可用时回退到应用专属外部下载目录；
 * - 下载开始前按需请求运行时权限（API 33+ 的通知权限、API 26~28 的存储权限），
 *   请求通过 [WebUIState.permissionRequester] 交给 WebUIActivity 的 launcher 处理，
 *   不在 WebView JS 桥线程上阻塞等待。
 *
 * 下载进度 / 完成 / 失败通知复用 NekoSU 的 [DownloadService.CHANNEL_ID] 通知渠道。
 */
@SuppressLint("MissingPermission")
class WebUIDownloadInterface(private val state: WebUIState) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val webView get() = state.webView
    private val notificationIdCounter = AtomicInteger(0)

    private companion object {
        /** MediaScanner 回调最长等待时间，避免扫描异常时挂住下载协程。 */
        const val SCAN_TIMEOUT_MS = 5_000L
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        val currentWebView = webView ?: return
        val uri = url.toUri()
        if (uri.scheme != "http" && uri.scheme != "https") return

        runCatching {
            currentWebView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure { throwable ->
            Log.e("WebUIDownload", "Failed to open $url", throwable)
        }
    }

    /**
     * 由注入的 download.js 在“点击下载”时调用：先按需弹出运行时权限（API 33+ 通知权限、
     * API 26~28 存储权限），真正的保存动作会复用这次请求或在其结果后继续。
     */
    @JavascriptInterface
    fun requestPermissions() {
        val currentWebView = webView ?: return
        val permissions = requiredDownloadPermissions(currentWebView.context)
        if (permissions.isEmpty()) return
        // 空动作：这里只触发权限请求；save()/saveChunkedDownload() 会把真正的保存动作
        // 排进同一个队列，等权限结果后继续。
        state.runDownloadWithPermissions(permissions) { }
    }

    @JavascriptInterface
    fun save(base64: String, fileName: String?) {
        val currentWebView = webView ?: return
        val context = currentWebView.context
        val displayName = sanitizeWebUIDownloadFileName(fileName)
        withDownloadPermissions(context) {
            scope.launch {
                val decoded = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrElse { throwable ->
                    Log.e("WebUIDownload", "Failed to decode download $displayName", throwable)
                    postToast(context.getString(R.string.download_failed_content, displayName))
                    return@launch
                }
                saveToDownloads(
                    context = context,
                    displayName = displayName,
                    mimeType = resolveMimeType(displayName, null),
                    totalBytes = decoded.size.toLong(),
                    source = { ByteArrayInputStream(decoded) },
                )
            }
        }
    }

    @JavascriptInterface
    fun startChunkedDownload(fileName: String?, mimeType: String?): String {
        val currentWebView = webView ?: return ""
        val sanitizedFileName = sanitizeWebUIDownloadFileName(fileName)
        return runCatching {
            BlobDownloadHandler.registerBlobDownload(
                sanitizedFileName,
                mimeType,
                currentWebView.context.cacheDir,
            )
        }.getOrElse {
            Log.e("WebUIDownload", "Failed to start chunked download", it)
            ""
        }
    }

    @JavascriptInterface
    fun writeDownloadChunk(downloadId: String, base64Chunk: String): Boolean {
        return BlobDownloadHandler.writeChunk(downloadId, base64Chunk)
    }

    @JavascriptInterface
    fun completeChunkedDownload(downloadId: String): Boolean {
        return BlobDownloadHandler.completeDownload(downloadId)
    }

    @JavascriptInterface
    fun cancelChunkedDownload(downloadId: String) {
        BlobDownloadHandler.cancelDownload(downloadId)
    }

    /**
     * 供 WebView 下载监听器调用：把已 spool 完成的 chunked 下载保存到系统下载目录。
     * 失败时静默（上游行为）并在日志中记录。
     */
    fun saveChunkedDownload(downloadId: String) {
        val currentWebView = webView ?: return
        val context = currentWebView.context
        // 权限缺失时先请求，保存动作在权限回调后继续（不阻塞 WebView JS 桥线程）。
        withDownloadPermissions(context) {
            // takeCompletedDownload 保证同一下载只保存一次（下载监听器与请求拦截可能都触发）。
            val spooled = BlobDownloadHandler.takeCompletedDownload(downloadId) ?: return@withDownloadPermissions
            scope.launch {
                try {
                    saveToDownloads(
                        context = context,
                        displayName = spooled.fileName,
                        mimeType = resolveMimeType(spooled.fileName, spooled.mimeType),
                        totalBytes = spooled.tempFile.length(),
                        source = { FileInputStream(spooled.tempFile) },
                    )
                } finally {
                    runCatching { spooled.tempFile.delete() }
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
        BlobDownloadHandler.cleanup()
    }

    /** 下载保存前需要但尚未授予的运行时权限。 */
    private fun requiredDownloadPermissions(context: Context): Array<String> {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        // API 26~28 的公共 Download 目录需要写外部存储权限；用户拒绝时回退到应用专属目录。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return missing.toTypedArray()
    }

    private fun withDownloadPermissions(context: Context, start: () -> Unit) {
        state.runDownloadWithPermissions(requiredDownloadPermissions(context), start)
    }

    private fun canWritePublicDownloads(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    /** API 29+：通过 MediaStore 发布到公共 Download 目录，无需存储权限。 */
    @SuppressLint("NewApi")
    private fun saveToMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        source: () -> java.io.InputStream,
        onBytesWritten: (Long) -> Unit,
        onPendingUri: (Uri) -> Unit,
    ): Uri {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: error("Failed to create download")
        onPendingUri(uri)
        source().use { input ->
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeWebUIDownload(input, output, onBytesWritten)
            } ?: error("Failed to open download")
        }
        val published = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        if (context.contentResolver.update(uri, published, null, null) != 1) {
            error("Failed to publish download")
        }
        return uri
    }

    /**
     * API 26~28 且已授予 WRITE_EXTERNAL_STORAGE：与 29+ 对齐，写入公共 Download 目录，
     * 再用 MediaScanner 拿到 MediaStore content Uri（用于完成通知的“打开”动作）。
     * 返回 null 表示文件已写入但扫描没有返回 Uri。
     */
    @Suppress("DEPRECATION")
    @SuppressLint("ScopedStorage")
    private suspend fun saveToPublicDownloads(
        context: Context,
        displayName: String,
        mimeType: String,
        source: () -> java.io.InputStream,
        onBytesWritten: (Long) -> Unit,
    ): Uri? {
        check(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            "External storage is not mounted"
        }
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = uniqueWebUIDownloadFile(directory, displayName)
        writeWebUIDownload(target, source(), { file -> java.io.FileOutputStream(file) }, onBytesWritten)
        return withTimeoutOrNull(SCAN_TIMEOUT_MS) { scanIntoMediaStore(context, target, mimeType) }
    }

    /** 回退（无公共目录写权限或存储不可用）：应用专属外部下载目录。 */
    private fun saveToAppDownloads(
        context: Context,
        displayName: String,
        source: () -> java.io.InputStream,
        onBytesWritten: (Long) -> Unit,
    ): Uri {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Downloads directory unavailable")
        val target = uniqueWebUIDownloadFile(directory, displayName)
        writeWebUIDownload(target, source(), { file -> java.io.FileOutputStream(file) }, onBytesWritten)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
    }

    private suspend fun scanIntoMediaStore(context: Context, file: File, mimeType: String): Uri? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mimeType),
                ) { _, uri ->
                    if (continuation.isActive) continuation.resume(uri)
                }
            }.onFailure { throwable ->
                Log.w("WebUIDownload", "Media scan failed for ${file.absolutePath}", throwable)
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private suspend fun saveToDownloads(
        context: Context,
        displayName: String,
        mimeType: String,
        totalBytes: Long,
        source: () -> java.io.InputStream,
    ): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationId = nextNotificationId()
        if (notificationManager != null) {
            ensureNotificationChannel(notificationManager, context)
            notifyProgress(notificationManager, context, notificationId, displayName, 0)
        }

        var pendingUri: Uri? = null
        // API 26~28 的 Public Downloads 在 MediaScanner 未返回 Uri（含 5s 超时）时也算保存成功：
        // saved 只表示文件已写入，savedUri 为 null 时完成通知不提供“打开”按钮。
        var saved = false
        var lastProgress = 0
        val onBytesWritten: (Long) -> Unit = { written ->
            if (notificationManager != null) {
                val progress = progressPercent(written, totalBytes)
                if (progress == 100 || progress / 10 != lastProgress / 10) {
                    lastProgress = progress
                    notifyProgress(notificationManager, context, notificationId, displayName, progress)
                }
            }
        }

        val savedUri: Uri? = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToMediaStore(
                    context = context,
                    displayName = displayName,
                    mimeType = mimeType,
                    source = source,
                    onBytesWritten = onBytesWritten,
                    onPendingUri = { pendingUri = it },
                ).also { saved = true }

                canWritePublicDownloads(context) -> saveToPublicDownloads(
                    context = context,
                    displayName = displayName,
                    mimeType = mimeType,
                    source = source,
                    onBytesWritten = onBytesWritten,
                ).also { saved = true }

                else -> saveToAppDownloads(
                    context = context,
                    displayName = displayName,
                    source = source,
                    onBytesWritten = onBytesWritten,
                ).also { saved = true }
            }
        } catch (throwable: Throwable) {
            pendingUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            Log.e("WebUIDownload", "Failed to save $displayName", throwable)
            if (notificationManager != null) {
                notifyFailure(notificationManager, context, notificationId, displayName)
            }
            postToast(context.getString(R.string.download_failed_content, displayName))
            return false
        }

        if (!saved) return false
        if (notificationManager != null) {
            notifyCompletion(notificationManager, context, notificationId, displayName, savedUri, mimeType)
        }
        postToast(context.getString(R.string.download_complete_content, displayName))
        return true
    }

    private fun nextNotificationId(): Int =
        200000 + notificationIdCounter.incrementAndGet() % 100000

    private fun progressPercent(written: Long, total: Long): Int =
        if (total <= 0L) 0 else ((written * 100L) / total).toInt().coerceIn(0, 100)

    private fun resolveMimeType(fileName: String, providedMimeType: String?): String =
        providedMimeType?.trim()?.takeIf { it.isNotEmpty() }
            ?: MimeUtil.getMimeFromFileName(fileName)
            ?: "application/octet-stream"

    private fun postToast(message: String) {
        webView?.let { currentWebView ->
            currentWebView.post {
                Toast.makeText(currentWebView.context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureNotificationChannel(
        notificationManager: NotificationManager,
        context: Context,
    ) {
        runCatching {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    DownloadService.CHANNEL_ID,
                    context.getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun notifyProgress(
        notificationManager: NotificationManager,
        context: Context,
        notificationId: Int,
        fileName: String,
        progress: Int,
    ) {
        val notification = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.download_progress_title, fileName))
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        runCatching { notificationManager.notify(notificationId, notification) }
    }

    private fun notifyCompletion(
        notificationManager: NotificationManager,
        context: Context,
        notificationId: Int,
        fileName: String,
        contentUri: Uri?,
        mimeType: String,
    ) {
        val builder = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.download_complete_title))
            .setContentText(context.getString(R.string.download_complete_content, fileName))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
        if (contentUri != null) {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                Intent.createChooser(openIntent, context.getString(R.string.download_open)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentIntent(pendingIntent)
        }
        runCatching { notificationManager.notify(notificationId, builder.build()) }
    }

    private fun notifyFailure(
        notificationManager: NotificationManager,
        context: Context,
        notificationId: Int,
        fileName: String,
    ) {
        val notification: Notification = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.download_failed_title))
            .setContentText(context.getString(R.string.download_failed_content, fileName))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        runCatching { notificationManager.notify(notificationId, notification) }
    }
}
