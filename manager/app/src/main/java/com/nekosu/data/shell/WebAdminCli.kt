package com.nekosu.data.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Manager 侧访问「ksud 网页管理器」的唯一通道。
 *
 * 网页服务本身跑在 ksud（root 守护进程）里，不依赖 Manager 进程，所以划掉 App / 一键清理都不会
 * 让端口消失。App 这边只负责：开关、取地址（含访问密钥）、打开/复制地址、重置密钥、诊断。
 *
 * 对应命令行（root shell 里也可直接用）：
 * ```
 * ksud webadmin enable       # 开启并拉起常驻进程（stdout 含带密钥的地址）
 * ksud webadmin disable      # 关闭并停止常驻进程（密钥保留）
 * ksud webadmin status       # enabled/port/pid/token 状态（不含密钥本体）
 * ksud webadmin url          # 打印访问地址（含访问密钥）
 * ksud webadmin reset-token  # 换一把密钥（旧链接立即失效）并打印新地址
 * ksud webadmin diagnose     # 排障信息（不含密钥本体）
 * ```
 *
 * 密钥只出现在 [url]/[resetTokenUrl] 的返回值里，不写日志、不写 prefs、不作为命令行参数
 * （`ps` 对同机其它 App 可读）。
 */
class WebAdminCli(
    private val ksuCliRepository: KsuCliRepository,
) {
    private companion object {
        const val TAG = "NekoSU-WebAdmin"

        /** 把输出/日志里的访问密钥打码，只保留 URL 形状。 */
        val TOKEN_IN_URL = Regex("(/nekosu/)[0-9a-fA-F]{16,}")
    }

    private fun redact(text: String): String = TOKEN_IN_URL.replace(text, "\$1<redacted>")

    /** 跑一条 `ksud webadmin <args>`，返回 stdout（失败返回空串，stderr 只记日志且打码）。 */
    private fun run(vararg args: String): String = runCatching {
        val command = buildString {
            append(ksuCliRepository.getKsuDaemonPath())
            append(" webadmin")
            for (arg in args) {
                append(' ')
                append(arg)
            }
        }
        val stdout = ArrayList<String>()
        val stderr = ArrayList<String>()
        val result = ksuCliRepository.getRootShell()
            .newJob()
            .add(command)
            .to(stdout, stderr)
            .exec()
        if (result.code != 0) {
            Log.w(
                TAG,
                "ksud webadmin ${args.joinToString(" ")} exit=${result.code}: " +
                    redact(stderr.joinToString("\n")),
            )
        }
        stdout.joinToString("\n").trim()
    }.getOrElse {
        Log.w(TAG, "ksud webadmin ${args.joinToString(" ")} failed", it)
        ""
    }

    /** 原始状态文本（诊断/设置页展示用，不含密钥本体）。 */
    fun status(): String = run("status")

    fun isEnabled(): Boolean = status().lineSequence().any { it.trim() == "enabled=1" }

    fun isRunning(): Boolean = status().lineSequence().any { it.trim() == "running=1" }

    /** 开关：enable 会同时把常驻进程拉起来。 */
    fun setEnabled(enable: Boolean): Boolean {
        val output = run(if (enable) "enable" else "disable")
        Log.i(TAG, "webadmin ${if (enable) "enable" else "disable"} → ${redact(output)}")
        return output.isNotEmpty()
    }

    /** 访问地址（含访问密钥）；ksud 不可用/未开启时返回空串。 */
    fun url(): String = run("url")
        .lineSequence()
        .map(String::trim)
        .lastOrNull { it.startsWith("http://") }
        .orEmpty()

    /** 换一把访问密钥，返回新的完整地址（失败为空串）。 */
    fun resetTokenUrl(): String = run("reset-token")
        .lineSequence()
        .map(String::trim)
        .lastOrNull { it.startsWith("http://") }
        .orEmpty()

    /** 设备端诊断文本（不含密钥本体）。 */
    fun diagnose(): String = run("diagnose")

    /** 用系统浏览器打开地址；失败返回错误描述。 */
    fun openInBrowser(context: Context, url: String): String? {
        if (url.isBlank()) return "blank url"
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.exceptionOrNull()?.let { error -> error.message ?: error.javaClass.simpleName }
    }

    /** 复制文本到剪贴板（访问密钥只走剪贴板，不进日志/prefs）。 */
    fun copyToClipboard(context: Context, text: String, label: String = "NekoSU WebAdmin"): Boolean =
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)
}
