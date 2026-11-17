package com.nekosu.data.update

import android.app.Application
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.nekosu.BuildConfig
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.network.NetworkRequestRepository
import com.nekosu.domain.model.ManagerApkSource
import com.nekosu.domain.model.ManagerUpdateChannel
import com.nekosu.domain.model.ManagerUpdateChecksumState
import com.nekosu.domain.model.ManagerUpdateInfo
import com.nekosu.domain.model.ManagerUpdateSourceStatus
import org.json.JSONObject

class ManagerUpdateRepository(
    private val application: Application,
    private val networkRequestRepository: NetworkRequestRepository,
    private val appSettingsRepository: AppSettingsRepository,
) {
    private companion object {
        const val UPDATE_CALL_TIMEOUT_SECONDS = 8L
        const val TAG = "ManagerUpdateRepository"
        const val WORKFLOW_FILE = "build-manager.yml"
        const val BRANCH = "main"
        const val RELEASE_ARTIFACT = "Manager-release"
        val GITHUB_HEADERS = mapOf("Accept" to "application/vnd.github+json")

        // sync with build.gradle.kts
        const val CI_MANAGER_VERSION_CODE_OFFSET = 30000 + 700
        const val SHORT_SHA_LENGTH = 7
        const val UNIVERSAL_ABI = "universal"
        const val SHA256SUMS_ASSET = "SHA256SUMS"
        const val CUSTOM_REPOSITORY_KEY = "manager_update_repository"
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        val REPOSITORY_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*")
    }

    private val mutableSourceStatus = MutableStateFlow(
        ManagerUpdateSourceStatus(
            repository = effectiveRepository,
            customRepository = customRepository,
        ),
    )

    /** Current update source plus the SHA256SUMS state of the last stable check. */
    val sourceStatus: StateFlow<ManagerUpdateSourceStatus> = mutableSourceStatus.asStateFlow()

    val defaultRepository: String get() = BuildConfig.MANAGER_UPDATE_REPOSITORY

    val customRepository: String?
        get() = appSettingsRepository.getString(CUSTOM_REPOSITORY_KEY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && isValidRepository(it) }

    val effectiveRepository: String get() = customRepository ?: defaultRepository

    val isHiddenManager: Boolean get() = application.packageName != BuildConfig.APPLICATION_ID

    /**
     * Persist a custom GitHub repository (owner/repo) used for Manager update
     * checks; null/blank restores the compiled-in default. Nightly/beta updates
     * stay disabled for a hidden Manager (the OS nightly artifacts are not
     * release-signed and have no SHA256SUMS).
     */
    fun setCustomRepository(value: String?): Result<Unit> = runCatching {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null) {
            require(isValidRepository(normalized)) {
                application.getString(R.string.manager_update_source_invalid)
            }
            appSettingsRepository.putString(CUSTOM_REPOSITORY_KEY, normalized)
        } else {
            appSettingsRepository.remove(CUSTOM_REPOSITORY_KEY)
        }
        updateSourceStatus(ManagerUpdateChecksumState.NOT_CHECKED, 0, null)
    }

    private fun isValidRepository(value: String): Boolean = REPOSITORY_PATTERN.matches(value)

    private fun updateSourceStatus(
        checksumState: ManagerUpdateChecksumState,
        entries: Int,
        channel: ManagerUpdateChannel?,
    ) {
        mutableSourceStatus.value = ManagerUpdateSourceStatus(
            repository = effectiveRepository,
            customRepository = customRepository,
            channel = channel,
            checksumState = checksumState,
            checksumEntries = entries,
        )
    }

    private val repository: String get() = effectiveRepository
    private val apiBase: String get() = BuildConfig.MANAGER_UPDATE_API_BASE.trimEnd('/')
    private val nightlyBase: String get() = BuildConfig.MANAGER_UPDATE_NIGHTLY_BASE.trimEnd('/')

    private val managerApkPattern = Regex(
        "^NekoSU_(.+)_(\\d+)-(arm64-v8a|armeabi-v7a|x86_64|universal)-release\\.apk$",
    )
    private val commitCountLinkPattern = Regex("""[?&]page=(\d+)>; rel="last"""")

    suspend fun checkStableUpdate(): ManagerUpdateInfo? = withContext(Dispatchers.IO) {
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val release = requestJson("$apiBase/repos/$repository/releases/latest")
            ?: return@withContext null
        val changelog = release.optString("body")
        val assets = release.optJSONArray("assets") ?: return@withContext null
        val checksums = releaseChecksums(assets)
        val checksumState = when {
            checksums == null -> ManagerUpdateChecksumState.MISSING
            checksums.isEmpty() -> ManagerUpdateChecksumState.FAILED
            else -> ManagerUpdateChecksumState.PROVIDED
        }
        updateSourceStatus(checksumState, checksums?.size ?: 0, ManagerUpdateChannel.STABLE)
        if (isHiddenManager && checksums == null) {
            // A hidden Manager must only install release-signed, checksummed
            // updates that can be re-signed with its local key.
            Log.i(TAG, "hidden Manager requires a release with $SHA256SUMS_ASSET; stable update skipped")
            return@withContext null
        }
        val candidates = mutableListOf<ManagerApkCandidate>()

        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val parsed = parseApkName(asset.optString("name")) ?: continue
            val downloadUrl = asset.optString("browser_download_url")
            if (downloadUrl.isBlank()) continue

            candidates += ManagerApkCandidate(
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                abi = parsed.abi,
                fileName = parsed.fileName,
                source = ManagerApkSource.DirectApk(downloadUrl),
                sha256 = checksums?.get(parsed.fileName),
            )
        }

        val selected = selectCandidate(candidates, supportedAbis) ?: return@withContext null
        if (checksums != null && checksums[selected.fileName] == null) {
            // The release ships SHA256SUMS but not for the selected APK: do not
            // offer an unverifiable download.
            Log.e(TAG, "SHA256SUMS has no entry for ${selected.fileName}; update skipped")
            updateSourceStatus(ManagerUpdateChecksumState.FAILED, checksums.size, ManagerUpdateChannel.STABLE)
            return@withContext null
        }
        selected.toUpdateInfo(
            channel = ManagerUpdateChannel.STABLE,
            changelog = changelog,
            checksums = checksums.orEmpty(),
        )
    }

    suspend fun checkBetaUpdate(): ManagerUpdateInfo? = withContext(Dispatchers.IO) {
        if (isHiddenManager) {
            Log.i(TAG, "nightly/beta updates are disabled for a hidden Manager")
            updateSourceStatus(ManagerUpdateChecksumState.NIGHTLY, 0, ManagerUpdateChannel.BETA)
            return@withContext null
        }
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val currentVersionCode = BuildConfig.VERSION_CODE
        val workflowRuns = requestJson(
            "$apiBase/repos/$repository/actions/workflows/$WORKFLOW_FILE/runs" +
                "?branch=$BRANCH&status=success&per_page=1&event=push",
        )?.optJSONArray("workflow_runs") ?: return@withContext null
        val run = workflowRuns.optJSONObject(0) ?: return@withContext null
        val runId = run.optLong("id", -1L)
        val headSha = run.optString("head_sha")
        if (runId <= 0L || headSha.isBlank()) return@withContext null

        val commitCount = requestCommitCount(headSha) ?: return@withContext null
        val versionCode = CI_MANAGER_VERSION_CODE_OFFSET + commitCount
        if (versionCode <= currentVersionCode) return@withContext null

        val preferredAbi = supportedAbis.firstOrNull() ?: UNIVERSAL_ABI
        updateSourceStatus(ManagerUpdateChecksumState.NIGHTLY, 0, ManagerUpdateChannel.BETA)
        return@withContext ManagerUpdateInfo(
            channel = ManagerUpdateChannel.BETA,
            versionCode = versionCode,
            versionName = headSha.take(SHORT_SHA_LENGTH),
            abi = preferredAbi,
            fileName = "NekoSU_${headSha.take(SHORT_SHA_LENGTH)}_" +
                "$versionCode-$preferredAbi-release.apk",
            source = ManagerApkSource.NightlyArtifact(
                url = "$nightlyBase/$repository/actions/runs/$runId/$RELEASE_ARTIFACT.zip",
                preferredAbi = preferredAbi,
                expectedVersionCode = versionCode,
            ),
            changelog = run.optJSONObject("head_commit")?.optString("message").orEmpty(),
        )
    }

    fun findNightlyApkEntry(
        entries: List<ZipEntryMetadata>,
        expectedVersionCode: Int,
        preferredAbi: String,
    ): ZipEntryMetadata? {
        val candidates = entries.mapNotNull { entry ->
            val parsed = parseApkName(entry.name.substringAfterLast('/')) ?: return@mapNotNull null
            if (parsed.versionCode == expectedVersionCode) entry to parsed else null
        }

        candidates.firstOrNull { (_, parsed) -> parsed.abi == preferredAbi }
            ?.first
            ?.let { return it }
        return candidates.firstOrNull { (_, parsed) -> parsed.abi == UNIVERSAL_ABI }?.first
    }

    private suspend fun requestJson(url: String): JSONObject? = networkRequestRepository
        .fetch(
            url = url,
            headers = GITHUB_HEADERS,
            forceNetwork = true,
            callTimeoutSeconds = UPDATE_CALL_TIMEOUT_SECONDS,
        )
        .onFailure { Log.w(TAG, "GitHub update request failed", it) }
        .mapCatching { JSONObject(it) }
        .getOrNull()

    private suspend fun requestCommitCount(commitSha: String): Int? {
        val url = "$apiBase/repos/$repository/commits?sha=$commitSha&per_page=1"
        val response = networkRequestRepository.request(
            url = url,
            headers = GITHUB_HEADERS,
            forceNetwork = true,
            callTimeoutSeconds = UPDATE_CALL_TIMEOUT_SECONDS,
        ).getOrElse {
            Log.w(TAG, "GitHub commit history request failed", it)
            return null
        }
        return parseCommitCount(response.header("Link")) ?: 1
    }

    /**
     * @return null when the release does not ship SHA256SUMS at all (checksum
     * verification is recorded as unavailable); a possibly empty map when it
     * does (an unreadable/empty file still forces verification, so callers must
     * not offer the update unless the selected APK has an entry).
     */
    private suspend fun releaseChecksums(assets: org.json.JSONArray): Map<String, String>? {
        val asset = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").equals(SHA256SUMS_ASSET, ignoreCase = true) }
            ?: run {
                Log.i(TAG, "Release has no $SHA256SUMS_ASSET asset; manager update checksums are not verified")
                return null
            }
        val url = asset.optString("browser_download_url")
        if (url.isBlank()) {
            Log.e(TAG, "$SHA256SUMS_ASSET asset has no download URL; update will be skipped")
            return emptyMap()
        }
        val text = requestText(url)
        if (text == null) {
            Log.e(TAG, "cannot read $SHA256SUMS_ASSET; update will be skipped")
            return emptyMap()
        }
        return parseChecksums(text).also {
            Log.i(TAG, "Release $SHA256SUMS_ASSET provides ${it.size} entries")
        }
    }

    private suspend fun requestText(url: String): String? = networkRequestRepository
        .fetch(
            url = url,
            headers = GITHUB_HEADERS,
            forceNetwork = true,
            callTimeoutSeconds = UPDATE_CALL_TIMEOUT_SECONDS,
        )
        .onFailure { Log.w(TAG, "SHA256SUMS request failed", it) }
        .getOrNull()

    internal fun parseChecksums(text: String): Map<String, String> = text.lineSequence()
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) return@mapNotNull null
            val digest = parts.first().lowercase()
            if (!SHA256_PATTERN.matches(digest)) return@mapNotNull null
            val name = parts.last().removePrefix("*").substringAfterLast('/')
            if (name.isEmpty()) null else name to digest
        }
        .toMap()

    internal fun parseCommitCount(linkHeader: String?): Int? = commitCountLinkPattern.find(linkHeader.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    private fun parseApkName(fileName: String): ParsedApkName? {
        val match = managerApkPattern.matchEntire(fileName) ?: return null
        val versionCode = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedApkName(
            versionName = match.groupValues[1],
            versionCode = versionCode,
            abi = match.groupValues[3],
            fileName = fileName,
        )
    }

    private fun selectCandidate(
        candidates: List<ManagerApkCandidate>,
        supportedAbis: List<String>,
    ): ManagerApkCandidate? {
        val latestVersionCode = candidates.maxOfOrNull { it.versionCode } ?: return null
        if (latestVersionCode <= BuildConfig.VERSION_CODE) return null

        val latestCandidates = candidates.filter { it.versionCode == latestVersionCode }
        supportedAbis.forEach { abi ->
            latestCandidates.firstOrNull { it.abi == abi }?.let { return it }
        }
        return latestCandidates.firstOrNull { it.abi == "universal" }
    }

    private data class ParsedApkName(
        val versionName: String,
        val versionCode: Int,
        val abi: String,
        val fileName: String,
    )

    private data class ManagerApkCandidate(
        val versionName: String,
        val versionCode: Int,
        val abi: String,
        val fileName: String,
        val source: ManagerApkSource,
        val sha256: String? = null,
    ) {
        fun toUpdateInfo(
            channel: ManagerUpdateChannel,
            changelog: String = "",
            checksums: Map<String, String> = emptyMap(),
        ) = ManagerUpdateInfo(
            channel = channel,
            versionCode = versionCode,
            versionName = versionName,
            abi = abi,
            fileName = fileName,
            source = source,
            changelog = changelog,
            sha256 = sha256 ?: checksums[fileName],
        )
    }
}
