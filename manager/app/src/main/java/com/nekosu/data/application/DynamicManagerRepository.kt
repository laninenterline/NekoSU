package com.nekosu.data.application

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.nekosu.data.kernel.KernelRepository
import com.nekosu.data.packageinfo.InstalledPackageRepository
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.domain.model.DynamicManagerApp
import com.nekosu.domain.model.DynamicManagerConfig
import com.nekosu.domain.model.DynamicManagerState

class DynamicManagerRepository(
    private val kernelRepository: KernelRepository,
    private val installedPackageRepository: InstalledPackageRepository,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(DynamicManagerState())
    val state: StateFlow<DynamicManagerState> = mutableState.asStateFlow()

    suspend fun refresh(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(isRefreshing = !it.isLoading) }
            runCatching {
                installedPackageRepository.refresh().getOrThrow()
                val signatureIndexes = managerSignatureIndexes()
                val apps = installedPackageRepository.packages.value
                    .mapNotNull { packageInfo ->
                        val nativeLibraryDir = packageInfo.nativeLibraryDir
                        // Classic KernelSU forks ship libksud.so; some newer forks
                        // (SukiSU-Nova, KernelSU-Next, MKSU, ...) rename native libs
                        // or embed ksud differently, so also match their package
                        // names. Any selected APK is accepted by the kernel's
                        // dynamic-manager signature mechanism.
                        // The running manager does not manage itself.
                        if (packageInfo.packageName == "com.nekosu") {
                            return@mapNotNull null
                        }
                        val hasKsudLib = !nativeLibraryDir.isNullOrBlank() &&
                            File(nativeLibraryDir, "libksud.so").isFile
                        if (!hasKsudLib && !isKnownKsuManagerPackage(packageInfo.packageName)) {
                            return@mapNotNull null
                        }
                        val uid = packageInfo.uid
                        val signatureIndex = signatureIndexes[uid % PER_USER_RANGE]
                        DynamicManagerApp(
                            label = packageInfo.appLabel.ifBlank { packageInfo.packageName },
                            packageName = packageInfo.packageName,
                            uid = uid,
                            apkPath = packageInfo.apkPath,
                            isSelected = signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                            managerSignatureIndex = signatureIndex,
                            isChangeable = signatureIndex == null ||
                                signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                        )
                    }
                    .sortedWith(appComparator)
                mutableState.value = DynamicManagerState(
                    config = ksuCliRepository.getDynamicManagerConfig()?.let {
                        DynamicManagerConfig(size = it.size, hash = it.hash)
                    },
                    apps = apps,
                    isLoading = false,
                    isRefreshing = false,
                )
            }.onFailure {
                mutableState.update { current ->
                    current.copy(isLoading = false, isRefreshing = false)
                }
            }
        }
    }

    suspend fun selectManager(apkPath: String): Result<Unit> = submit {
        ksuCliRepository.setDynamicManagerApk(apkPath)
    }

    suspend fun setManual(size: Int, hash: String): Result<Unit> = submit {
        ksuCliRepository.setDynamicManager(size, hash)
    }

    suspend fun clear(): Result<Unit> = submit(ksuCliRepository::clearDynamicManager)

    private suspend fun submit(command: () -> Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(isSubmitting = true) }
            runCatching {
                check(command())
                val signatureIndexes = managerSignatureIndexes()
                mutableState.update { current ->
                    current.copy(
                        config = ksuCliRepository.getDynamicManagerConfig()?.let {
                            DynamicManagerConfig(size = it.size, hash = it.hash)
                        },
                        apps = current.apps.map { app ->
                            val signatureIndex = signatureIndexes[app.uid % PER_USER_RANGE]
                            app.copy(
                                isSelected = signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                                managerSignatureIndex = signatureIndex,
                                isChangeable = signatureIndex == null ||
                                    signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                            )
                        }.sortedWith(appComparator),
                        isSubmitting = false,
                    )
                }
            }.onFailure {
                mutableState.update { current -> current.copy(isSubmitting = false) }
            }
        }
    }


    private fun isKnownKsuManagerPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return KNOWN_MANAGER_PACKAGES.contains(packageName) ||
            KSU_MANAGER_KEYWORDS.any { lower.contains(it) }
    }

    private suspend fun managerSignatureIndexes(): Map<Int, Int> = kernelRepository.getManagerRuntimeInfo().managers.associate { it.uid to it.signatureIndex }

    private companion object {
        const val DYNAMIC_MANAGER_SIGNATURE_INDEX = 255
        val KNOWN_MANAGER_PACKAGES = setOf(
            "me.weishu.kernelsu",
            "com.nekosu",
            "dev.maplesu.manager",
            "io.milksu.manager",
            "com.sukisu.nova",
            "com.sukisu.ultra",
            "com.rifsxd.ksunext",
            "com.resukisu.resukisu",
            "org.mksu.mksu",
            "com.mksu",
        )
        val KSU_MANAGER_KEYWORDS = listOf(
            "kernelsu", "sukisu", "resukisu", "ksunext", "milksu", "maplesu", "nekosu", "mksu",
        )
        const val PER_USER_RANGE = 100_000
        val appComparator =
            compareByDescending<DynamicManagerApp> { it.managerSignatureIndex != null }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    }
}
