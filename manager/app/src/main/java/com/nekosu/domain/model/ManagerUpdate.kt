package com.nekosu.domain.model

enum class ManagerUpdateChannel {
    STABLE,
    BETA,
}

sealed interface ManagerApkSource {
    val url: String

    data class DirectApk(override val url: String) : ManagerApkSource

    data class NightlyArtifact(
        override val url: String,
        val preferredAbi: String,
        val expectedVersionCode: Int,
    ) : ManagerApkSource
}

data class ManagerUpdateInfo(
    val channel: ManagerUpdateChannel,
    val versionCode: Int,
    val versionName: String,
    val abi: String,
    val fileName: String,
    val source: ManagerApkSource,
    val changelog: String = "",
    /** SHA-256 from the release SHA256SUMS asset, when the release provides one. */
    val sha256: String? = null,
)

/** SHA-256 verification state of the Manager update source. */
enum class ManagerUpdateChecksumState {
    NOT_CHECKED,
    PROVIDED,
    MISSING,
    FAILED,
    NIGHTLY,
}

data class ManagerUpdateSourceStatus(
    val repository: String = "",
    val customRepository: String? = null,
    val channel: ManagerUpdateChannel? = null,
    val checksumState: ManagerUpdateChecksumState = ManagerUpdateChecksumState.NOT_CHECKED,
    val checksumEntries: Int = 0,
)
