package com.nekosu.ui.webui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebUIDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sanitizesDownloadFileNames() {
        assertEquals("download.bin", sanitizeWebUIDownloadFileName(null))
        assertEquals("download.bin", sanitizeWebUIDownloadFileName(""))
        assertEquals("download.bin", sanitizeWebUIDownloadFileName("   "))
        assertEquals("download.bin", sanitizeWebUIDownloadFileName("."))
        assertEquals("download.bin", sanitizeWebUIDownloadFileName(".."))
        assertEquals("download.bin", sanitizeWebUIDownloadFileName("../../"))
        assertEquals("download.bin", sanitizeWebUIDownloadFileName("/"))
        assertEquals("passwd", sanitizeWebUIDownloadFileName("../../etc/passwd"))
        assertEquals("file.apk", sanitizeWebUIDownloadFileName("/tmp/file.apk"))
        assertEquals("file.apk", sanitizeWebUIDownloadFileName("C:\\tmp\\file.apk"))
        assertEquals("模块.zip", sanitizeWebUIDownloadFileName(" 模块.zip "))
        assertEquals("name with space.bin", sanitizeWebUIDownloadFileName("name with space.bin"))
    }

    @Test
    fun uniqueNamesAvoidOverwritingExistingFiles() {
        val directory = temporaryFolder.newFolder("downloads")
        assertEquals("report.pdf", uniqueWebUIDownloadFile(directory, "report.pdf").name)
        File(directory, "report.pdf").writeText("one")
        assertEquals("report (1).pdf", uniqueWebUIDownloadFile(directory, "report.pdf").name)
        File(directory, "report (1).pdf").writeText("two")
        assertEquals("report (2).pdf", uniqueWebUIDownloadFile(directory, "report.pdf").name)
        File(directory, "download.bin").writeText("default")
        assertEquals("download (1).bin", uniqueWebUIDownloadFile(directory, null).name)
        File(directory, "notes").writeText("no extension")
        assertEquals("notes (1)", uniqueWebUIDownloadFile(directory, "notes").name)
    }

    @Test
    fun writesChunkedStreamAndReportsProgress() {
        val payload = ByteArray(160 * 1024 + 7) { index -> (index * 31 + 7).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        val written = writeWebUIDownload(ByteArrayInputStream(payload), output) {
            progress.add(it)
        }
        assertEquals(payload.size.toLong(), written)
        assertTrue(payload.contentEquals(output.toByteArray()))
        assertTrue(progress.isNotEmpty())
        assertEquals(payload.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun writesEmptyStreamWithoutProgress() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        val written = writeWebUIDownload(ByteArrayInputStream(ByteArray(0)), output) {
            progress.add(it)
        }
        assertEquals(0L, written)
        assertEquals(0, output.size())
        assertTrue(progress.isEmpty())
    }

    @Test
    fun writesDownloadToFileAndCreatesParents() {
        val target = File(temporaryFolder.root, "nested/download.bin")
        val payload = ByteArray(32 * 1024) { it.toByte() }
        val written = writeWebUIDownload(target, ByteArrayInputStream(payload))
        assertEquals(payload.size.toLong(), written)
        assertTrue(target.isFile)
        assertTrue(payload.contentEquals(target.readBytes()))
    }

    @Test
    fun resolvesMimeTypesFromFileNames() {
        assertEquals("application/zip", MimeUtil.getMimeFromFileName("module.zip"))
        assertEquals("application/wasm", MimeUtil.getMimeFromFileName("engine.WASM"))
        assertEquals("application/json", MimeUtil.getMimeFromFileName("update.json"))
        assertNull(MimeUtil.getMimeFromFileName(null))
        assertNull(MimeUtil.getMimeFromFileName("README"))
        assertNull(MimeUtil.getMimeFromFileName("file.unknownext"))
    }
}
