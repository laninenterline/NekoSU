package com.nekosu.data.application

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.nekosu.BuildConfig
import com.nekosu.Natives
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.settings.IconVariants
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.data.system.ManagerApk
import com.nekosu.data.system.ManagerAppearance
import com.nekosu.data.system.ManagerIcon
import com.nekosu.data.system.ManagerManifest
import com.nekosu.data.system.ManagerTransfer
import com.nekosu.domain.model.HiddenManagerState

/** A downloaded update cannot be re-signed as a valid hidden Manager update. */
class HiddenUpdateUnsupportedException(message: String) : IllegalStateException(message)

/**
 * Hides the Manager under a randomized package name with a locally generated
 * signing key, and restores the official Manager afterwards.
 *
 * A migration is guarded by a one-time token and a handover record written into
 * the target app's private directory. The target confirms root/kernel access
 * before the source Manager is allowed to be uninstalled.
 */
class HiddenManagerRepository(
    private val context: Context,
    private val ksuCliRepository: KsuCliRepository,
    private val appSettingsRepository: AppSettingsRepository,
) {
    private companion object {
        private const val TAG = "HiddenManager"
        private const val MAIN_ACTIVITY = "com.nekosu.ui.MainActivity"
        private const val PER_USER_RANGE = 100000
        private const val SUPPORTED_USER_ID = 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operation = Mutex()
    private val handoverOperation = Mutex()
    private val mutableState = MutableStateFlow(HiddenManagerState())
    val state: StateFlow<HiddenManagerState> = mutableState.asStateFlow()
    private val json = Json { ignoreUnknownKeys = false }
    private var kernelSupport: Boolean? = null

    private val directory get() = File(context.filesDir, "hidden_manager")
    private val officialApk get() = File(directory, "official.apk")
    private val localKey get() = File(directory, "identity.p12")
    private val transferFile get() = File(directory, "transfer.json")
    private val readyFile get() = File(directory, "ready")
    private val authorizedFile get() = File(directory, "authorized")
    private val failureFile get() = File(directory, "failure")
    private val appearanceFile get() = File(directory, "appearance.json")
    private val customIcon get() = File(directory, "icon.png")
    private val updateDirectory get() = File(context.cacheDir, "hidden_manager_updates")

    private val isHidden: Boolean get() = context.packageName != BuildConfig.APPLICATION_ID
    private val currentUserId: Int get() = Process.myUid() / PER_USER_RANGE

    /** Only the primary Android user is supported for install/uninstall/paths. */
    private fun supportsCurrentUser(): Boolean = currentUserId == SUPPORTED_USER_ID

    /** True when this install is the randomized hidden Manager copy. */
    val isHiddenManager: Boolean get() = isHidden

    init {
        scope.launch { refreshInternal() }
    }

    suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { refreshInternal() }
    }

    suspend fun hide(displayName: String, iconUri: Uri?): Result<Unit> =
        migrate(hidden = true, displayName = displayName, iconUri = iconUri)

    suspend fun restore(): Result<Unit> = migrate(hidden = false, displayName = null, iconUri = null)

    /**
     * Re-sign a downloaded official update so it installs over the current
     * hidden package with the same hidden package name and local certificate,
     * keeping the kernel identity record and root access valid.
     */
    suspend fun repackageManagerUpdate(updateApk: File): Result<File> = operation.withLock {
        withContext(Dispatchers.IO) {
            runCatching { repackageManagerUpdateLocked(updateApk) }
        }
    }

    private suspend fun migrate(hidden: Boolean, displayName: String?, iconUri: Uri?): Result<Unit> =
        operation.withLock {
            withContext(Dispatchers.IO) {
                if (!supportsCurrentUser()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            context.getString(R.string.hidden_manager_multiuser_unsupported, currentUserId),
                        ),
                    )
                }
                if (!supported()) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.hidden_manager_unsupported)),
                    )
                }
                if (hidden == isHidden) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.hidden_manager_invalid_state)),
                    )
                }
                mutableState.update { it.copy(busy = true, error = null) }
                val result = runCatching { migrateLocked(hidden, displayName, iconUri) }
                result.onFailure { error ->
                    mutableState.update { it.copy(error = error.message) }
                }
                refreshInternal()
                mutableState.update { it.copy(busy = false) }
                result
            }
        }

    /**
     * Called by every Manager start. Only the freshly installed Manager acts on
     * the pending record; the source Manager ignores it.
     */
    suspend fun completeHandoverIfNeeded(): Result<Unit> = handoverOperation.withLock {
        withContext(Dispatchers.IO) {
            if (!transferFile.isFile) return@withContext Result.success(Unit)
            val transfer = try {
                json.decodeFromString<ManagerTransfer>(transferFile.readText())
            } catch (error: Throwable) {
                return@withContext Result.failure(error)
            }
            if (transfer.target != context.packageName) return@withContext Result.success(Unit)
            // Do not retry a handover that already failed, and drop stale records.
            val recordedFailure = runCatching {
                failureFile.takeIf { it.isFile }?.readText()
            }.getOrNull()
            if (recordedFailure?.startsWith("${transfer.token}:") == true) {
                return@withContext Result.success(Unit)
            }
            if (System.currentTimeMillis() - transfer.createdAt > 600_000L) {
                transferFile.delete()
                readyFile.delete()
                authorizedFile.delete()
                failureFile.delete()
                return@withContext Result.success(Unit)
            }
            val result = runCatching { completeHandoverLocked(transfer) }
            result.onFailure { error -> recordTransferFailure(transfer, error) }
            refreshInternal()
            result
        }
    }

    private suspend fun migrateLocked(hidden: Boolean, displayName: String?, iconUri: Uri?) {
        // All pm/path operations below are intentionally fixed to --user 0.
        // migrate() has already rejected runs from secondary Android users.
        require(supportsCurrentUser()) {
            context.getString(R.string.hidden_manager_multiuser_unsupported, currentUserId)
        }
        require(ManagerManifest.validPackage(context.packageName)) { "The Manager package name is invalid" }
        if (operationPending()) {
            throw IOException(context.getString(R.string.hidden_manager_pending))
        }
        val self = context.packageName
        val source = File(context.applicationInfo.sourceDir)
        val sourceIdentity = identity(archive(source))
        require(sourceIdentity.packageName == self) { "The running Manager identity is inconsistent" }

        val target = if (hidden) {
            generateSequence { ManagerApk.randomPackage() }.first { installedPath(it) == null }
        } else {
            BuildConfig.APPLICATION_ID
        }
        require(installedPath(target) == null) { "The target Manager is already installed" }
        if (!hidden) {
            require(officialApk.isFile) { context.getString(R.string.hidden_manager_restore_copy_missing) }
            val officialIdentity = identity(archive(officialApk))
            require(officialIdentity.packageName == BuildConfig.APPLICATION_ID) {
                context.getString(R.string.hidden_manager_restore_copy_invalid)
            }
            val currentIdentity = currentIdentity()
            require(officialIdentity.signerSha256 != currentIdentity.signerSha256) {
                context.getString(R.string.hidden_manager_restore_signature_mismatch)
            }
        }

        val staged = if (hidden) {
            File.createTempFile("manager-hidden-", ".apk", context.cacheDir).also { it.delete() }
        } else {
            officialApk
        }
        var installed = false
        try {
            if (hidden) {
                require(directory.isDirectory || directory.mkdirs()) {
                    "The private Manager directory is unavailable"
                }
                // The hidden copy gets a single replaced icon; activity-alias
                // launchers would keep their old icon, so go back to default.
                resetLauncherIconToDefault()
                val icon = ManagerIcon.png(context, iconUri)
                val application = archiveApplication(source)
                val originalIconEntry = ManagerIcon.entry(context, application)
                val hiddenIconEntry = ManagerApk.hiddenIconEntry(originalIconEntry)
                val appearance = ManagerAppearance(
                    ManagerAppearance.validateHiddenName(
                        requireNotNull(displayName) { "A hidden app name is required" },
                    ),
                    hiddenIconEntry,
                )
                writeAtomic(appearanceFile, json.encodeToString(appearance).toByteArray())
                writeAtomic(customIcon, icon)
                localKey.delete()
                ManagerApk.createKey(localKey)
                atomicCopy(source, officialApk)
                ManagerApk.repackage(
                    source,
                    staged,
                    self,
                    target,
                    localKey,
                    appearance,
                    icon,
                    application.loadLabel(context.packageManager).toString(),
                    originalIconEntry,
                )
            }

            val candidate = identity(archive(staged))
            // A hidden copy starts at the running version; a restore may fall
            // back to an older saved official APK (a different package, so the
            // platform does not enforce a downgrade). Keep that explicit instead
            // of failing the transfer: the official update flow can catch up.
            val versionCompatible = if (hidden) {
                candidate.versionCode == sourceIdentity.versionCode
            } else {
                true
            }
            require(candidate.packageName == target && versionCompatible &&
                !candidate.debuggable && !candidate.testOnly && !candidate.split) {
                context.getString(R.string.hidden_manager_replacement_invalid)
            }
            if (!hidden && candidate.versionCode < sourceIdentity.versionCode) {
                Log.w(
                    TAG,
                    "Restoring official Manager ${candidate.versionCode} over hidden " +
                        "${sourceIdentity.versionCode}; update the Manager after restoring",
                )
            }
            val certificate = ManagerApk.verifiedSigner(staged)
            val signer = ManagerApk.certificateHash(certificate)
            require(candidate.signerSha256 == setOf(signer)) { "The replacement Manager signature is invalid" }

            val token = ByteArray(32).also(SecureRandom()::nextBytes)
                .joinToString("") { "%02x".format(it) }
            var transfer = ManagerTransfer(
                token = token,
                source = self,
                target = target,
                sourceUid = Process.myUid(),
                targetUid = 0,
                versionCode = candidate.versionCode,
                versionName = candidate.versionName ?: throw IOException("Missing Manager version"),
                sourceCertificate = sourceIdentity.signerSha256.single(),
                targetCertificate = signer,
                hidden = hidden,
                createdAt = System.currentTimeMillis(),
            )
            writeAtomic(transferFile, json.encodeToString(transfer).toByteArray())

            // Record the hidden identity before the install so the packages.list
            // scan triggered by the install can already recognize the new copy.
            if (hidden) require(recordIdentity(transfer, certificate.encoded.size))

            val output = root(
                "pm install -r --user 0 -S ${staged.length()} < ${quote(staged.absolutePath)}",
            )
            require("Success" in output) { "The replacement Manager was not installed" }
            installed = true

            val installedApkPath = installedPath(target)
                ?: throw IOException("The installed Manager is missing")
            val targetUid = installedUid(target)
            transfer = transfer.copy(targetUid = targetUid)
            writeAtomic(transferFile, json.encodeToString(transfer).toByteArray())

            val installedApk = File(installedApkPath)
            require(hash(installedApk.readBytes()) == hash(staged.readBytes())) {
                "The installed Manager does not match the verified APK"
            }
            require(identity(archive(installedApk)) == candidate) {
                "The installed Manager identity does not match the verified APK"
            }
            if (hidden) {
                val appearance = json.decodeFromString<ManagerAppearance>(appearanceFile.readText())
                require(archiveApplication(installedApk).loadLabel(context.packageManager).toString() ==
                    appearance.displayName) { "The hidden Manager label was not applied" }
                appearance.iconEntry?.let { entry ->
                    ZipFile(installedApk).use { apk ->
                        val image = apk.getInputStream(requireNotNull(apk.getEntry(entry)))
                            .use { it.readBytes() }
                        require(image.contentEquals(customIcon.readBytes())) {
                            "The hidden Manager icon was not applied"
                        }
                    }
                }
                // The install event may have raced the first write; refresh again.
                require(recordIdentity(transfer, certificate.encoded.size))
            }

            val targetData = "/data/user/0/$target"
            val targetFiles = "$targetData/files/hidden_manager"
            root(
                "mkdir -p ${quote(targetFiles)} ${quote("$targetData/files")} && " +
                    "chown $targetUid:$targetUid ${quote("$targetData/files")} && " +
                    "chmod 700 ${quote("$targetData/files")} && " +
                    "restorecon -F ${quote("$targetData/files")}",
            )
            if (hidden) {
                for (name in listOf("official.apk", "identity.p12", "transfer.json", "appearance.json", "icon.png")) {
                    val input = File(directory, name)
                    if (input.isFile) root("cp ${quote(input.absolutePath)} ${quote("$targetFiles/$name")}")
                }
                // Keep the Manager UI settings across the package rename.
                val datastore = File(context.filesDir, "datastore")
                if (datastore.isDirectory) {
                    root("mkdir -p ${quote("$targetData/files/datastore")}")
                    datastore.listFiles()?.filter { it.isFile }?.forEach {
                        root("cp ${quote(it.absolutePath)} ${quote("$targetData/files/datastore/${it.name}")}")
                    }
                }
                val preferences = File(context.applicationInfo.dataDir, "shared_prefs")
                if (preferences.isDirectory) {
                    root("mkdir -p ${quote("$targetData/shared_prefs")}")
                    preferences.listFiles()?.filter { it.isFile && it.extension == "xml" }?.forEach {
                        root("cp ${quote(it.absolutePath)} ${quote("$targetData/shared_prefs/${it.name}")}")
                    }
                }
            } else {
                root("cp ${quote(transferFile.absolutePath)} ${quote("$targetFiles/transfer.json")}")
            }
            root(
                "chown -R $targetUid:$targetUid ${quote(targetFiles)} ${quote("$targetData/files")} && " +
                    "chmod 700 ${quote(targetFiles)} ${quote("$targetData/files")} && " +
                    "restorecon -RF ${quote(targetFiles)} ${quote("$targetData/files")}",
            )
            if (hidden) {
                val targetPreferences = "$targetData/shared_prefs"
                rootOrNull(
                    "if [ -d ${quote(targetPreferences)} ]; then " +
                        "chown -R $targetUid:$targetUid ${quote(targetPreferences)} && " +
                        "chmod 700 ${quote(targetPreferences)} && " +
                        "restorecon -RF ${quote(targetPreferences)}; fi",
                )
            }
            rootOrNull("appops set --user 0 ${quote(target)} REQUEST_INSTALL_PACKAGES allow")

            launchManager(target)
            var ready = false
            repeat(240) {
                if (!ready) {
                    transferFailure(targetFiles, token)?.let { throw IOException(it) }
                    ready = rootOrNull("cat ${quote("$targetFiles/ready")}") == token
                    if (!ready) delay(250)
                }
            }
            require(ready) {
                "The new Manager did not confirm its identity; the original app was retained"
            }
            root(
                "printf %s ${quote(token)} > ${quote("$targetFiles/authorized.new")} && " +
                    "chown $targetUid:$targetUid ${quote("$targetFiles/authorized.new")} && " +
                    "chmod 600 ${quote("$targetFiles/authorized.new")} && " +
                    "restorecon -F ${quote("$targetFiles/authorized.new")} && " +
                    "mv ${quote("$targetFiles/authorized.new")} ${quote("$targetFiles/authorized")}",
            )
            withTimeout(30_000L) {
                while (installedPath(self) != null) {
                    transferFailure(targetFiles, token)?.let { throw IOException(it) }
                    delay(250)
                }
            }
        } catch (error: Throwable) {
            if (installed && installedPath(self) != null) {
                runCatching {
                    rootOrNull("am force-stop --user 0 ${quote(target)}")
                    if (hidden) clearIdentity()
                    rootOrNull("pm uninstall --user 0 ${quote(target)}")
                }
                transferFile.delete()
                if (hidden) directory.deleteRecursively()
            }
            throw error
        } finally {
            if (hidden) staged.delete()
        }
    }

    private suspend fun completeHandoverLocked(transfer: ManagerTransfer) {
        transfer.validate(transfer.token, context.packageName, Process.myUid(), System.currentTimeMillis())
        val self = identity(archive(File(context.applicationInfo.sourceDir)))
        require(self.packageName == transfer.target && self.versionCode == transfer.versionCode &&
            self.versionName == transfer.versionName &&
            self.signerSha256 == setOf(transfer.targetCertificate)) {
            "The installed Manager does not match the transfer record"
        }
        require(runCatching { Natives.isManager }.getOrDefault(false)) {
            "The new Manager has no kernel management access"
        }
        if (!authorizedFile.isFile) {
            writeAtomic(readyFile, transfer.token.toByteArray())
            withTimeout(120_000L) {
                while (!authorizedFile.isFile) delay(250)
            }
        }
        require(authorizedFile.readText() == transfer.token) {
            "The new Manager was not authorized before the original removal"
        }
        val sourcePath = installedPath(transfer.source)
        if (sourcePath != null) {
            transfer.validate(transfer.token, context.packageName, Process.myUid(), System.currentTimeMillis())
            require(installedUid(transfer.source) == transfer.sourceUid) {
                "The original Manager UID changed"
            }
            val source = identity(archive(File(sourcePath)))
            require(source.packageName == transfer.source &&
                source.signerSha256 == setOf(transfer.sourceCertificate)) {
                "The original Manager identity changed"
            }
            root("pm uninstall --user 0 ${quote(transfer.source)}")
        }
        require(installedPath(transfer.source) == null) {
            "The original Manager could not be uninstalled"
        }
        if (!transfer.hidden) {
            clearIdentity()
            directory.deleteRecursively()
        }
        transferFile.delete()
        readyFile.delete()
        authorizedFile.delete()
        failureFile.delete()
    }

    private fun recordIdentity(transfer: ManagerTransfer, certificateSize: Int): Boolean =
        ksuCliRepository.execKsud(
            "manager-identity set ${quote(transfer.target)} $certificateSize ${transfer.targetCertificate}",
            newShell = true,
        )

    private fun clearIdentity(): Boolean =
        ksuCliRepository.execKsud("manager-identity clear", newShell = true)

    private fun launchManager(target: String) {
        rootOrNull(
            "am start --user 0 -n ${quote("$target/com.nekosu.ui.MainActivity")}",
        )
    }

    private fun transferFailure(targetFiles: String, token: String): String? =
        rootOrNull("cat ${quote("$targetFiles/failure")}")
            ?.takeIf { it.startsWith("$token:") }
            ?.removePrefix("$token:")

    private fun recordTransferFailure(transfer: ManagerTransfer, error: Throwable) {
        runCatching {
            writeAtomic(failureFile, "${transfer.token}:${error.message ?: "Manager transfer failed"}".toByteArray())
        }
    }

    private fun operationPending(): Boolean = transferFile.isFile

    private fun currentIdentity(): ApkIdentity =
        identity(archive(File(context.applicationInfo.sourceDir)))

    private fun readAppearance(): ManagerAppearance? = runCatching {
        if (appearanceFile.isFile) {
            json.decodeFromString<ManagerAppearance>(appearanceFile.readText())
        } else {
            null
        }
    }.getOrNull()

    /**
     * The hidden APK replaces the default launcher foreground only. If an
     * activity-alias icon variant is active, the launcher keeps showing the
     * alias icon; switch back to the default variant before hiding so the
     * replaced icon and label are visible and the alias state is not carried
     * over into the hidden package.
     */
    private suspend fun resetLauncherIconToDefault() {
        val packageManager = context.packageManager
        val mainComponent = ComponentName(context.packageName, MAIN_ACTIVITY)
        IconVariants.allComponents(context).forEach { component ->
            val state = if (component == mainComponent) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }.onFailure {
                Log.w(TAG, "cannot reset launcher component $component", it)
            }
        }
        runCatching {
            appSettingsRepository.putInt("icon_variant", 0)
            appSettingsRepository.putBoolean("use_alt_icon", false)
            // Flush queued preference writes before the datastore is copied to
            // the hidden package.
            appSettingsRepository.editBlocking { }
        }.onFailure {
            Log.w(TAG, "cannot persist the default icon variant", it)
        }
        val mainState = runCatching {
            packageManager.getComponentEnabledSetting(mainComponent)
        }.getOrNull()
        require(
            mainState == null ||
                mainState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                mainState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        ) { context.getString(R.string.hidden_manager_default_icon_required) }
    }

    private fun repackageManagerUpdateLocked(updateApk: File): File {
        require(isHidden) { context.getString(R.string.hidden_manager_update_not_hidden) }
        require(supportsCurrentUser()) {
            context.getString(R.string.hidden_manager_multiuser_unsupported, currentUserId)
        }
        require(updateApk.isFile) { context.getString(R.string.hidden_manager_update_invalid) }
        require(ManagerManifest.validPackage(context.packageName)) {
            context.getString(R.string.hidden_manager_update_invalid)
        }
        require(directory.isDirectory && localKey.isFile && officialApk.isFile) {
            context.getString(R.string.hidden_manager_restore_copy_missing)
        }
        val current = currentIdentity()
        val update = identity(archive(updateApk))
        val official = identity(archive(officialApk))
        require(update.packageName == BuildConfig.APPLICATION_ID) {
            context.getString(R.string.hidden_manager_update_invalid)
        }
        if (update.signerSha256 != official.signerSha256) {
            throw HiddenUpdateUnsupportedException(
                context.getString(R.string.hidden_manager_update_signature_mismatch),
            )
        }
        require(!update.debuggable && !update.testOnly && !update.split) {
            context.getString(R.string.hidden_manager_update_invalid)
        }
        require(update.versionCode > current.versionCode) {
            context.getString(R.string.hidden_manager_update_not_newer)
        }

        val appearance = requireNotNull(readAppearance()) {
            context.getString(R.string.hidden_manager_update_invalid)
        }
        val icon = appearance.iconEntry?.let {
            require(customIcon.isFile) { context.getString(R.string.hidden_manager_update_invalid) }
            customIcon.readBytes()
        }
        val application = archiveApplication(updateApk)
        val originalIconEntry = if (appearance.iconEntry != null) {
            ManagerIcon.entry(context, application)
        } else {
            null
        }
        val outputAppearance = appearance.copy(
            iconEntry = originalIconEntry?.let(ManagerApk::hiddenIconEntry),
        )
        val output = File(updateDirectory.apply { if (!isDirectory) mkdirs() }, "hidden-manager-update.apk")
        if (output.exists() && !output.delete()) {
            throw IOException(context.getString(R.string.hidden_manager_update_failed))
        }
        ManagerApk.repackage(
            input = updateApk,
            output = output,
            originalPackage = BuildConfig.APPLICATION_ID,
            targetPackage = context.packageName,
            key = localKey,
            appearance = outputAppearance,
            icon = icon,
            originalLabel = application.loadLabel(context.packageManager).toString(),
            originalIconEntry = originalIconEntry,
        )
        val candidate = identity(archive(output))
        require(candidate.packageName == context.packageName &&
            candidate.versionCode == update.versionCode &&
            !candidate.debuggable && !candidate.testOnly && !candidate.split) {
            context.getString(R.string.hidden_manager_update_invalid)
        }
        val certificate = ManagerApk.verifiedSigner(output)
        val signer = ManagerApk.certificateHash(certificate)
        require(candidate.signerSha256 == setOf(signer)) {
            context.getString(R.string.hidden_manager_update_signature_mismatch)
        }
        require(current.signerSha256 == setOf(signer)) {
            context.getString(R.string.hidden_manager_update_identity_changed)
        }
        writeAtomic(appearanceFile, json.encodeToString(outputAppearance).toByteArray())
        // Keep the restore source in sync with the version being installed: an
        // official restore after a cancelled install installs the newer official
        // release, which is safe and avoids a stale saved APK.
        atomicCopy(updateApk, officialApk)
        Log.i(TAG, "repackaged hidden Manager update ${update.versionCode} -> ${output.name}")
        return output
    }

    private fun refreshInternal() {
        val appearance = readAppearance()
        val supported = supported()
        val multiUserDevice = runCatching {
            (context.getSystemService(Context.USER_SERVICE) as? UserManager)?.userCount?.let { it > 1 } ?: false
        }.getOrDefault(false)
        // After a hidden update is prepared the saved official APK is newer than
        // the running hidden package until the installer finishes. That is also
        // what remains when the user cancels the install prompt, so surface it
        // as a retry hint.
        val pendingUpdate = if (isHidden) {
            runCatching {
                identity(archive(officialApk)).versionCode > currentIdentity().versionCode
            }.getOrDefault(false)
        } else {
            false
        }
        mutableState.update { current ->
            current.copy(
                supported = supported,
                hidden = isHidden,
                currentPackage = context.packageName,
                displayName = appearance?.displayName,
                pendingTransfer = operationPending(),
                hasOfficialCopy = officialApk.isFile,
                currentUserId = currentUserId,
                multiUserDevice = multiUserDevice,
                pendingUpdate = pendingUpdate,
            )
        }
    }

    private fun supported(): Boolean =
        Process.myUid() in 10000..99999 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            runCatching { Natives.isManager }.getOrDefault(false) &&
            kernelSupportsHiddenManager()

    private fun kernelSupportsHiddenManager(): Boolean = kernelSupport
        ?: ksuCliRepository.execKsud("manager-identity probe", newShell = true)
            .also { kernelSupport = it }

    private data class ApkIdentity(
        val packageName: String,
        val versionCode: Long,
        val versionName: String?,
        val signerSha256: Set<String>,
        val debuggable: Boolean,
        val testOnly: Boolean,
        val split: Boolean,
    )

    @Suppress("DEPRECATION", "NewApi")
    private fun archive(file: File): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("The Manager APK cannot be inspected")
    }

    @Suppress("DEPRECATION", "NewApi")
    private fun identity(info: PackageInfo): ApkIdentity {
        val application = info.applicationInfo
        return ApkIdentity(
            packageName = info.packageName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            },
            versionName = info.versionName,
            signerSha256 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners.orEmpty()
                    .map { hash(it.toByteArray()) }
                    .toSet()
            } else {
                info.signatures.orEmpty().map { hash(it.toByteArray()) }.toSet()
            },
            debuggable = (application?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            testOnly = (application?.flags ?: 0) and ApplicationInfo.FLAG_TEST_ONLY != 0,
            split = !info.splitNames.isNullOrEmpty(),
        )
    }

    private fun archiveApplication(file: File): ApplicationInfo =
        requireNotNull(archive(file).applicationInfo) { "The Manager application info is missing" }.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }

    private fun installedPath(packageName: String): String? {
        require(ManagerManifest.validPackage(packageName))
        val output = rootOrNull("pm path --user 0 ${quote(packageName)}") ?: return null
        val lines = output.lineSequence().filter { it.startsWith("package:") }.toList()
        val line = lines.singleOrNull()
            ?: lines.firstOrNull { it.endsWith("/base.apk") }
            ?: return null
        val path = line.removePrefix("package:")
        require(path.startsWith("/data/app/") && path.endsWith("/base.apk")) {
            "The installed Manager APK path is invalid"
        }
        return path
    }

    private fun installedUid(packageName: String): Int =
        installedUidOrNull(packageName) ?: throw IOException("The installed Manager UID is missing")

    private fun installedUidOrNull(packageName: String): Int? {
        require(ManagerManifest.validPackage(packageName))
        val output = rootOrNull("cmd package list packages -U --user 0 ${quote(packageName)}")
            ?: return null
        if (output.isBlank()) return null
        val entries = output.lineSequence().map { line ->
            Regex("package:(\\S+) uid:(\\d+)").matchEntire(line.trim())
                ?: throw IOException("The package service did not return a valid package list")
        }.filter { it.groupValues[1] == packageName }.toList()
        if (entries.isEmpty()) return null
        return entries.singleOrNull()?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 10000..99999 }
            ?: throw IOException("The installed Manager UID is invalid")
    }

    private fun root(command: String): String =
        ksuCliRepository.withNewRootShell(globalMnt = true) {
            val stdout = ArrayList<String>()
            val stderr = ArrayList<String>()
            val result = newJob().add(command).to(stdout, stderr).exec()
            if (!result.isSuccess) {
                throw IOException(
                    stderr.joinToString("\n").ifBlank { stdout.joinToString("\n") },
                )
            }
            stdout.joinToString("\n").trim()
        }

    private fun rootOrNull(command: String): String? = runCatching { root(command) }.getOrNull()

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        require(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun atomicCopy(from: File, to: File) = writeAtomic(to, from.readBytes())
}
