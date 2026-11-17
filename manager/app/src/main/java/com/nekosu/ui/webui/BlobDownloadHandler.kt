package com.nekosu.ui.webui

import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 处理 WebUI 的 blob: 下载：JS 侧分块写入的字节先落到缓存临时文件，
 * WebView 请求内部地址时再以附件形式返回给下载监听器。
 *
 * 该实现移植自上游 KernelSU 的 BlobDownloadHandler，仅保留 NekoSU 所需能力。
 */
object BlobDownloadHandler {
    private const val BLOB_DOWNLOAD_HOST = "blob-download.kernelsu.internal"
    private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L

    private val pendingDownloads = ConcurrentHashMap<String, PendingDownload>()

    @Volatile
    private var lastCleanupTime = System.currentTimeMillis()

    private data class PendingDownload(
        val fileName: String,
        val mimeType: String?,
        val tempFile: File,
        val outputStream: OutputStream,
        val createdAt: Long = System.currentTimeMillis(),
        @Volatile var completed: Boolean = false,
    )

    /** 已完成、等待平台侧保存到下载目录的临时文件信息。 */
    internal data class SpooledDownload(
        val downloadId: String,
        val fileName: String,
        val mimeType: String?,
        val tempFile: File,
    )

    /** 判断 URL 是否为内部 blob 下载地址（用于下载监听器与请求拦截）。 */
    fun isBlobDownloadUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val uri = Uri.parse(url)
        return uri.scheme.equals("https", ignoreCase = true) && uri.host == BLOB_DOWNLOAD_HOST
    }

    /** 从内部 blob 下载地址中取出 downloadId；非内部地址返回 null。 */
    fun downloadIdFromUrl(url: String?): String? {
        if (!isBlobDownloadUrl(url)) return null
        return Uri.parse(url).path?.substring(1)?.takeIf { it.isNotEmpty() }
    }

    /**
     * 兼容 JS 侧把 [registerBlobDownload] 返回的内部 URL 当作 downloadId 传回的情况：
     * 统一还原为会话 UUID 作为表键。
     */
    private fun normalizeDownloadId(raw: String): String {
        if (!raw.contains(BLOB_DOWNLOAD_HOST)) return raw
        return Uri.parse(raw).path?.substring(1)?.takeIf { it.isNotEmpty() } ?: raw
    }

    /** 注册一个新的 blob 下载会话并返回内部 URL。 */
    fun registerBlobDownload(fileName: String, mimeType: String?, cacheDir: File): String {
        val downloadId = UUID.randomUUID().toString()
        val tempFile = File.createTempFile("nekosu-blob-", ".download", cacheDir)
        pendingDownloads[downloadId] = PendingDownload(
            fileName = fileName,
            mimeType = mimeType,
            tempFile = tempFile,
            outputStream = tempFile.outputStream().buffered(64 * 1024),
        )

        val now = System.currentTimeMillis()
        if (now - lastCleanupTime > STALE_THRESHOLD_MS) {
            lastCleanupTime = now
            cleanupStaleDownloads()
        }

        return "https://$BLOB_DOWNLOAD_HOST/$downloadId"
    }

    /** 写入一个 base64 编码分块。 */
    fun writeChunk(downloadId: String, base64Chunk: String): Boolean {
        val id = normalizeDownloadId(downloadId)
        val download = pendingDownloads[id] ?: return false
        return try {
            synchronized(download) {
                if (download.completed) return false
                download.outputStream.write(Base64.decode(base64Chunk, Base64.DEFAULT))
            }
            true
        } catch (e: Exception) {
            Log.e("BlobDownloadHandler", "Failed to write chunk for $id", e)
            closeDownload(id)
            false
        }
    }

    /** 标记下载完成，使缓存文件可被 WebView / 下载监听器读取。 */
    fun completeDownload(downloadId: String): Boolean {
        val id = normalizeDownloadId(downloadId)
        val download = pendingDownloads[id] ?: return false
        return try {
            synchronized(download) {
                if (download.completed) return false
                download.outputStream.flush()
                download.outputStream.close()
                download.completed = true
            }
            true
        } catch (e: Exception) {
            Log.e("BlobDownloadHandler", "Failed to complete download $id", e)
            closeDownload(id)
            false
        }
    }

    /**
     * 原子地取走一个已完成下载（从待处理表中移除）。返回的临时文件由调用方在
     * 保存完成后删除；重复调用只有第一次能取到，避免下载监听器与请求拦截重复保存。
     */
    internal fun takeCompletedDownload(downloadId: String): SpooledDownload? {
        val normalizedId = normalizeDownloadId(downloadId)
        val download = pendingDownloads[normalizedId] ?: return null
        if (!download.completed) return null
        if (!pendingDownloads.remove(normalizedId, download)) return null
        return SpooledDownload(
            downloadId = normalizedId,
            fileName = download.fileName,
            mimeType = download.mimeType,
            tempFile = download.tempFile,
        )
    }

    fun cancelDownload(downloadId: String) = closeDownload(normalizeDownloadId(downloadId))

    /** 拦截内部 blob 下载地址，在当前 WebView 中直接返回已完成文件。 */
    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.scheme != "https" || url.host != BLOB_DOWNLOAD_HOST) return null

        val downloadId = url.path?.substring(1)?.takeIf { it.isNotEmpty() }
            ?: return createErrorResponse("Invalid download ID")
        val download = pendingDownloads[downloadId]
            ?: return createErrorResponse("Download not found")

        if (!download.completed) return createErrorResponse("Download is not ready")
        val input = runCatching { FileInputStream(download.tempFile) }.getOrElse {
            closeDownload(downloadId)
            return createErrorResponse("Download not found")
        }

        // 注意：不能在流关闭时删除临时文件——WebView 的下载监听器随后还要读取它
        // 保存到下载目录；临时文件由 saveChunkedDownload/cancelDownload/过期清理负责删除。
        return WebResourceResponse(
            download.mimeType ?: "application/octet-stream",
            null,
            input,
        ).apply {
            responseHeaders = mapOf(
                "Content-Disposition" to "attachment; filename=\"${download.fileName}\"",
                "Access-Control-Allow-Origin" to "*",
            )
        }
    }

    private fun closeDownload(downloadId: String) {
        pendingDownloads.remove(downloadId)?.let { download ->
            runCatching { download.outputStream.close() }
            runCatching { download.tempFile.delete() }
        }
    }

    private fun createErrorResponse(message: String) = WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        emptyMap(),
        ByteArrayInputStream(message.toByteArray(Charsets.UTF_8)),
    )

    private fun cleanupStaleDownloads() {
        val now = System.currentTimeMillis()
        val staleIds = pendingDownloads.entries
            .filter { now - it.value.createdAt > TimeUnit.MINUTES.toMillis(5) }
            .map { it.key }
        staleIds.forEach { downloadId ->
            Log.w("BlobDownloadHandler", "Cleaning up stale download: $downloadId")
            closeDownload(downloadId)
        }
    }

    fun cleanup() = pendingDownloads.keys.toList().forEach(::closeDownload)
}
