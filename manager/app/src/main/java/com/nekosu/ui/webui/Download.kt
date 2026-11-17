package com.nekosu.ui.webui

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val DEFAULT_WEBUI_DOWNLOAD_NAME = "download.bin"

internal fun resolveWebUIDownloadFile(downloadsDir: File, requestedFileName: String?): File {
    val fileName = sanitizeWebUIDownloadFileName(requestedFileName)
    return File(downloadsDir, fileName)
}

internal fun sanitizeWebUIDownloadFileName(requestedFileName: String?): String {
    val trimmed = requestedFileName?.trim().orEmpty()
    if (trimmed.isEmpty()) return DEFAULT_WEBUI_DOWNLOAD_NAME

    val candidate = trimmed
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()

    if (candidate.isEmpty() || candidate == "." || candidate == "..") {
        return DEFAULT_WEBUI_DOWNLOAD_NAME
    }

    return candidate
}

internal fun writeWebUIDownload(
    target: File,
    source: InputStream,
    outputStreamFactory: (File) -> OutputStream = ::FileOutputStream,
    onBytesWritten: (Long) -> Unit = {},
): Long {
    target.parentFile?.mkdirs()
    return outputStreamFactory(target).use { output ->
        writeWebUIDownload(source, output, onBytesWritten)
    }
}

internal fun writeWebUIDownload(
    source: InputStream,
    output: OutputStream,
    onBytesWritten: (Long) -> Unit = {},
): Long {
    output.buffered(64 * 1024).use { bufferedOutput ->
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read == -1) {
                break
            }
            bufferedOutput.write(buffer, 0, read)
            total += read
            onBytesWritten(total)
        }
        bufferedOutput.flush()
        return total
    }
}

/** 在 [directory] 中生成不会覆盖已有文件的路径：`name.ext`、`name (1).ext`…… */
internal fun uniqueWebUIDownloadFile(directory: File, requestedFileName: String?): File {
    directory.mkdirs()
    val candidate = resolveWebUIDownloadFile(directory, requestedFileName)
    if (!candidate.exists()) return candidate

    val baseName = candidate.nameWithoutExtension.ifEmpty { DEFAULT_WEBUI_DOWNLOAD_NAME }
    val extension = candidate.extension
    var index = 1
    while (true) {
        val name = if (extension.isEmpty()) {
            "$baseName ($index)"
        } else {
            "$baseName ($index).$extension"
        }
        val file = File(directory, name)
        if (!file.exists()) return file
        index++
    }
}
