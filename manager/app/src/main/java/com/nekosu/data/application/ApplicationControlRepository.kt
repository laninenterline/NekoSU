package com.nekosu.data.application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nekosu.Natives
import com.nekosu.data.shell.KsuCliRepository

class ApplicationControlRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun ensureManagerInstalled(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Natives.isFullFeatured() && ksuCliRepository.rootAvailable()) {
                ksuCliRepository.install()
            }
        }
    }

    suspend fun reboot(reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { ksuCliRepository.reboot(reason) }
    }
}
