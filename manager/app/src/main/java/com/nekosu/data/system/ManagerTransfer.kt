package com.nekosu.data.system

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Durable record of one Manager migration. Both the source and the freshly
 * installed Manager validate it; the token prevents a stale or forged
 * handover from uninstalling the wrong app.
 */
@Serializable
data class ManagerTransfer(
    val token: String,
    val source: String,
    val target: String,
    val sourceUid: Int,
    val targetUid: Int,
    val versionCode: Long,
    val versionName: String,
    val sourceCertificate: String,
    val targetCertificate: String,
    val hidden: Boolean,
    val createdAt: Long,
) {
    fun validate(nonce: String, currentPackage: String, currentUid: Int, now: Long) {
        require(Regex("[0-9a-f]{64}").matches(token) &&
            MessageDigest.isEqual(token.toByteArray(), nonce.toByteArray())) { "Invalid manager transfer" }
        require(ManagerManifest.validPackage(source) && ManagerManifest.validPackage(target) && source != target)
        require(target == currentPackage && targetUid == currentUid &&
            sourceUid in 10000..99999 && targetUid in 10000..99999 && sourceUid != targetUid)
        require(now - createdAt in 0..300_000L && versionCode > 0 && versionName.isNotEmpty()) {
            "Manager transfer expired"
        }
        require(listOf(sourceCertificate, targetCertificate).all { Regex("[0-9a-f]{64}").matches(it) })
    }

    fun identityRecord(certificateSize: Int): ByteArray =
        ManagerIdentity.encode(target, certificateSize, targetCertificate)
}
