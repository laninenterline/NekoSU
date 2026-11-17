package com.nekosu.data.system

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary identity record shared by the Manager, ksud and the kernel.
 *
 * Layout (little endian, 333 bytes):
 *   u32 magic | u32 version | u32 certificate_size | char package[256] |
 *   char certificate_sha256[65]
 */
object ManagerIdentity {
    const val MAGIC = 0x4d534849
    const val VERSION = 1
    const val RECORD_SIZE = 12 + 256 + 65
    const val MAX_CERTIFICATE_SIZE = 1024

    fun encode(packageName: String, certificateSize: Int, certificateSha256: String): ByteArray {
        require(ManagerManifest.validPackage(packageName))
        require(certificateSize in 1..MAX_CERTIFICATE_SIZE)
        require(Regex("[0-9a-f]{64}").matches(certificateSha256))
        return ByteBuffer.allocate(RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(MAGIC)
            putInt(VERSION)
            putInt(certificateSize)
            put(packageName.toByteArray(Charsets.US_ASCII))
            position(12 + 256)
            put(certificateSha256.toByteArray(Charsets.US_ASCII))
        }.array()
    }
}
