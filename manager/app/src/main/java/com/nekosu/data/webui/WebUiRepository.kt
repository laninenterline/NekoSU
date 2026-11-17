package com.nekosu.data.webui

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.UiThreadHandler
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.util.concurrent.CompletableFuture
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.domain.model.WebUiCommandResult
import com.nekosu.domain.model.WebUiModuleInfo
import com.nekosu.domain.model.WebUiProcess

class WebUiRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    fun execute(command: String, globalMnt: Boolean = true): WebUiCommandResult {
        val result = ksuCliRepository.withNewRootShell(globalMnt) {
            newJob().add(command).to(ArrayList(), ArrayList()).exec()
        }
        return WebUiCommandResult(
            code = result.code,
            stdout = result.out.joinToString("\n"),
            stderr = result.err.joinToString("\n"),
        )
    }

    fun spawn(command: String, globalMnt: Boolean = true): WebUiProcess = ShellWebUiProcess(ksuCliRepository.createRootShell(globalMnt), command)

    fun listModules(): String = ksuCliRepository.listModules()

    /**
     * Cached global-mount root shell shared with the rest of the manager (log center, bugreport,
     * shortcuts ...). Long-running WebUI file operations should not use it; [createFileShell]
     * hands out a dedicated shell instead.
     */
    fun rootShell(globalMnt: Boolean = true): Shell = ksuCliRepository.getRootShell(globalMnt)

    /**
     * Creates a root shell owned by the caller for the WebUI file bridge (ksu.io).
     *
     * ksu.io must not use [rootShell]: SuFile commands are serialized per libsu shell, so a
     * long-running file operation sharing the cached global-mount shell would also block the
     * manager's exec/background work (and vice versa). The WebView keeps one dedicated shell for
     * its lifetime and closes it when the WebUI activity is disposed.
     */
    fun createFileShell(): Shell = ksuCliRepository.createRootShell(globalMnt = true)

    fun getModuleInfo(moduleId: String): WebUiModuleInfo? = ksuCliRepository.withNewRootShell(globalMnt = true) {
        val rootShell = this
        readWebUiModuleInfo(
            moduleId = moduleId,
            resolveFile = { path -> SuFile(path).apply { shell = rootShell } },
            openFile = { file -> SuFileInputStream.open(file) },
        )
    }

    fun openFile(path: String) = runCatching {
        val file = SuFile(path).apply { shell = ksuCliRepository.getRootShell(true) }
        SuFileInputStream.open(file)
    }.getOrNull()

    private class ShellWebUiProcess(
        private val shell: Shell,
        private val command: String,
    ) : WebUiProcess {
        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            try {
                val stdout = object : CallbackList<String>(UiThreadHandler::runAndWait) {
                    override fun onAddElement(s: String) = onStdout(s)
                }
                val stderr = object : CallbackList<String>(UiThreadHandler::runAndWait) {
                    override fun onAddElement(s: String) = onStderr(s)
                }
                val future = shell.newJob().add(command).to(stdout, stderr).enqueue()
                CompletableFuture.supplyAsync { future.get() }
                    .thenAccept { result ->
                        onComplete(
                            WebUiCommandResult(
                                code = result.code,
                                stdout = result.out.joinToString("\n"),
                                stderr = result.err.joinToString("\n"),
                            ),
                        )
                    }
                    .whenComplete { _, _ -> close() }
            } catch (error: Throwable) {
                onComplete(WebUiCommandResult(-1, "", error.message.orEmpty()))
                close()
            }
        }

        override fun close() {
            runCatching { shell.close() }
        }
    }
}
