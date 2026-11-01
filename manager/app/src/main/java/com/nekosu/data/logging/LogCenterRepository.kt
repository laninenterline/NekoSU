package com.nekosu.data.logging

import android.app.Application
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.magica.MagicaFailureLog
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LogSource { ALL, SULOG, MAGICA, CRASH, METAMODULE, PROBLEM }

/** Reads/clears every persisted log source the manager knows about. */
class LogCenterRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val logDir = "/data/adb/ksu/log"

    suspend fun load(source: LogSource): String = withContext(Dispatchers.IO) {
        when (source) {
            LogSource.ALL -> buildString {
                append(load(LogSource.SULOG))
                append("\n\n")
                append(load(LogSource.MAGICA))
                append("\n\n")
                append(load(LogSource.CRASH))
                append("\n\n")
                append(load(LogSource.METAMODULE))
                append("\n\n")
                append(load(LogSource.PROBLEM))
            }

            LogSource.SULOG -> readSulog()
            LogSource.MAGICA -> readAppFile("magica_failure.log", MagicaFailureLog.read(application))
            LogSource.CRASH -> readAppFile("crash.log", CrashLogger.read(application))
            LogSource.METAMODULE -> readMetaModule()
            LogSource.PROBLEM -> buildProblemLog()
        }
    }

    suspend fun clear(source: LogSource): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when (source) {
                LogSource.SULOG -> {
                    ShellUtils.fastCmd(shell(), "rm -f $logDir/sulog-*.log")
                    true
                }
                LogSource.MAGICA -> MagicaFailureLog.file(application).delete()
                LogSource.CRASH -> {
                    CrashLogger.clear(application)
                    true
                }
                LogSource.METAMODULE -> {
                    ShellUtils.fastCmd(shell(), "rm -f $logDir/metamodule_mount $logDir/metamodule_metauninstall")
                    true
                }
                LogSource.ALL, LogSource.PROBLEM -> false
            }
        }.getOrDefault(false)
    }

    private fun shell() = ksuCliRepository.getRootShell(true)

    private fun readSulog(): String {
        val latest = runCatching {
            ShellUtils.fastCmd(shell(), "ls -t $logDir/sulog-*.log 2>/dev/null | head -n 1").trim()
        }.getOrDefault("")
        if (latest.isBlank()) {
            return "=== SU log ===\n(no sulog files yet; enable the SU log feature first)"
        }
        val content = runCatching {
            ShellUtils.fastCmd(shell(), "cat '$latest' 2>/dev/null")
        }.getOrDefault("")
        return "=== $latest ===\n$content"
    }

    private fun readMetaModule(): String {
        val files = listOf("metamodule_mount", "metamodule_metauninstall")
        return files.joinToString("\n\n") { name ->
            val content = runCatching {
                ShellUtils.fastCmd(shell(), "cat '$logDir/$name' 2>/dev/null")
            }.getOrDefault("")
            "=== $name ===\n" + content.ifBlank { "(empty)" }
        }
    }

    private fun readAppFile(name: String, content: String): String =
        "=== $name ===\n" + content.ifBlank { "(empty)" }

    private fun buildProblemLog(): String {
        val sb = StringBuilder()
        sb.appendLine("=== NekoSU problem log ===")
        sb.appendLine("generated: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        sb.appendLine("manager: ${getManagerVersion(application)}")
        sb.appendLine("brand/model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("android: ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("fingerprint: ${android.os.Build.FINGERPRINT}")
        sb.appendLine()
        sb.appendLine("=== jailbreak (Magica) failure log ===")
        sb.appendLine(MagicaFailureLog.read(application).ifBlank { "(no recorded jailbreak failure)" })
        sb.appendLine()
        sb.appendLine("=== crash log ===")
        sb.appendLine(CrashLogger.read(application).ifBlank { "(no recorded crash)" })
        return sb.toString()
    }
}
