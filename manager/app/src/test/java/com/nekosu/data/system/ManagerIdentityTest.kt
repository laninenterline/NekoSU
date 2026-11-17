package com.nekosu.data.system

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManagerIdentityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun encodesLittleEndianIdentityRecord() {
        val certificateSha256 = "ab".repeat(32)
        val record = ManagerIdentity.encode("com.nekosu.hidden", 512, certificateSha256)
        assertEquals(333, record.size)
        val buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(ManagerIdentity.MAGIC, buffer.getInt(0))
        assertEquals(ManagerIdentity.VERSION, buffer.getInt(4))
        assertEquals(512, buffer.getInt(8))
        val packageName = String(record.copyOfRange(12, 12 + 256), Charsets.US_ASCII)
            .trimEnd('\u0000')
        assertEquals("com.nekosu.hidden", packageName)
        val certificate = String(record.copyOfRange(12 + 256, record.size), Charsets.US_ASCII)
            .trimEnd('\u0000')
        assertEquals(certificateSha256, certificate)
    }

    @Test
    fun rejectsInvalidIdentityInputs() {
        val certificateSha256 = "ab".repeat(32)
        assertThrows(IllegalArgumentException::class.java) {
            ManagerIdentity.encode("NekoSU", 512, certificateSha256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerIdentity.encode("com.nekosu", 0, certificateSha256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerIdentity.encode("com.nekosu", 1025, certificateSha256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerIdentity.encode("com.nekosu", 512, "AB".repeat(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerIdentity.encode("com.nekosu", 512, "ab".repeat(31))
        }
    }

    @Test
    fun certificateHashIsStableSha256Fingerprint() {
        val key = File(temporaryFolder.root, "first.p12")
        ManagerApk.createKey(key)
        val certificate = ManagerApk.certificate(key)
        val hash = ManagerApk.certificateHash(certificate)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        val expected = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, hash)
        assertEquals(hash, ManagerApk.certificateHash(ManagerApk.certificate(key)))
    }

    @Test
    fun everyGeneratedKeyHasItsOwnCertificate() {
        val first = File(temporaryFolder.root, "one.p12")
        val second = File(temporaryFolder.root, "two.p12")
        ManagerApk.createKey(first)
        ManagerApk.createKey(second)
        assertNotEquals(
            ManagerApk.certificateHash(ManagerApk.certificate(first)),
            ManagerApk.certificateHash(ManagerApk.certificate(second)),
        )
    }

    @Test
    fun hiddenIconEntryRewritesXmlDrawablesOnly() {
        assertEquals(
            "res/mipmap/ic_launcher.png",
            ManagerApk.hiddenIconEntry("res/mipmap/ic_launcher.xml"),
        )
        assertEquals(
            "res/mipmap/ic_launcher.png",
            ManagerApk.hiddenIconEntry("res/mipmap/ic_launcher.png"),
        )
        assertEquals(
            "res/drawable/icon.XML",
            ManagerApk.hiddenIconEntry("res/drawable/icon.XML"),
        )
    }

    @Test
    fun randomPackageIsAValidHiddenIdentity() {
        repeat(16) {
            val packageName = ManagerApk.randomPackage()
            assertTrue("invalid package: $packageName", ManagerManifest.validPackage(packageName))
            assertTrue(
                "hidden keyword leaked: $packageName",
                ManagerAppearance.isHiddenTextAllowed(packageName),
            )
        }
    }
}
