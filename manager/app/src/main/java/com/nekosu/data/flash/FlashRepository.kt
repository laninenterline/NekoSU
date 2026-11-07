package com.nekosu.data.flash

import android.app.Application
import androidx.core.net.toUri
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import com.nekosu.data.file.ModuleFileRepository
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.domain.model.FlashOperation
import com.nekosu.domain.model.FlashOperationUpdate

/**
 * Flashes KernelSU modules.
 *
 * Kernel/boot-image installation and permanent uninstall are intentionally not supported:
 * NekoSU only supports temporary root loaded from userspace.
 */
class FlashRepository(
    private val application: Application,
    private val moduleFileRepository: ModuleFileRepository,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun execute(operation: FlashOperation): Flow<FlashOperationUpdate> = callbackFlow {
        val onStdout: (String) -> Unit = { trySend(FlashOperationUpdate.Output(it)) }
        val onStderr: (String) -> Unit = { trySend(FlashOperationUpdate.ErrorOutput(it)) }
        val onFinish: (Boolean, Int) -> Unit = { showReboot, code ->
            trySend(FlashOperationUpdate.Completed(showReboot, code))
        }

        try {
            withContext(Dispatchers.IO) {
                when (operation) {
                    is FlashOperation.Module -> ksuCliRepository.flashModule(
                        application,
                        operation.uri.toUri(),
                        onFinish,
                        onStdout,
                        onStderr,
                    )
                }
            }
        } catch (error: Throwable) {
            trySend(FlashOperationUpdate.ErrorOutput(error.message.orEmpty()))
            trySend(FlashOperationUpdate.Completed(showReboot = false, code = -1))
        } finally {
            close()
        }
        awaitClose()
    }

    suspend fun moduleNeedsMount(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val parsedUri = uri.toUri()
            val moduleId = moduleFileRepository.extractModuleId(parsedUri.toString())
                ?: return@runCatching false
            val oldSystem = SuFile.open("/data/adb/modules/$moduleId/system")
            val updatedSystem = SuFile.open("/data/adb/modules_update/$moduleId/system")
            oldSystem.isDirectory || updatedSystem.isDirectory
        }.getOrDefault(false)
    }
}
