package com.nekosu.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ManagerTransferTest {
    private val token = "0123456789abcdef".repeat(4)
    private val certificate = "ab".repeat(32)
    private val now = 1_700_000_000_000L

    private fun transfer(sourceCertificate: String = certificate): ManagerTransfer =
        ManagerTransfer(
            token = token,
            source = "com.nekosu",
            target = "com.hidden.manager",
            sourceUid = 10200,
            targetUid = 10300,
            versionCode = 40001L,
            versionName = "1.0.0",
            sourceCertificate = sourceCertificate,
            targetCertificate = certificate,
            hidden = true,
            createdAt = now,
        )

    @Test
    fun acceptsMatchingHandoverRecord() {
        val record = transfer()
        record.validate(token, record.target, record.targetUid, now)
        record.validate(token, record.target, record.targetUid, now + 300_000L)
        assertEquals(333, record.identityRecord(512).size)
    }

    @Test
    fun rejectsMismatchedTokenOrTarget() {
        val record = transfer()
        assertThrows(IllegalArgumentException::class.java) {
            record.validate("f".repeat(64), record.target, record.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record.validate("not-a-token", record.target, record.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record.validate(token, "com.other.package", record.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record.validate(token, record.target, record.targetUid + 1, now)
        }
    }

    @Test
    fun rejectsExpiredOrFutureRecords() {
        val record = transfer()
        assertThrows(IllegalArgumentException::class.java) {
            record.validate(token, record.target, record.targetUid, now - 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record.validate(token, record.target, record.targetUid, now + 300_001L)
        }
    }

    @Test
    fun rejectsInconsistentIdentityFields() {
        val base = transfer()
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(source = base.target)
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(source = "NekoSU")
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(sourceUid = 9999)
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(targetUid = 100_000)
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(sourceUid = base.targetUid)
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(versionCode = 0L)
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(versionName = "")
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(targetCertificate = "XYZ")
                .validate(token, base.target, base.targetUid, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(sourceCertificate = "AB".repeat(32))
                .validate(token, base.target, base.targetUid, now)
        }
    }
}
