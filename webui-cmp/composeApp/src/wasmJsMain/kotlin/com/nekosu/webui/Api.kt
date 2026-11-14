package com.nekosu.webui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ------------------------------ API models ------------------------------ */

@Serializable
data class KernelVersionInfo(
    val major: Int = 0,
    val patchLevel: Int = 0,
    val subLevel: Int = 0,
)

@Serializable
data class KernelStatusInfo(
    val isRootAvailable: Boolean = false,
    val isLateLoadMode: Boolean = false,
    val hookType: String = "",
    val kernelVersion: KernelVersionInfo? = null,
    val ksuFullVersion: String? = null,
    val lkmMode: Boolean? = null,
)

@Serializable
data class HomeInfo(
    val status: KernelStatusInfo = KernelStatusInfo(),
    val version: String = "",
    val versionCode: Int = 0,
    val deviceModel: String = "—",
    val deviceBrand: String = "",
    val androidVersion: String = "—",
    val kernelRelease: String = "—",
    val selinuxStatus: String = "Unknown",
    val seccompStatus: Int = -1,
    val moduleCount: Int = 0,
    val superuserCount: Int = 0,
)

@Serializable
data class ModuleInfo(
    val id: String = "",
    val name: String = "",
    val author: String = "",
    val version: String = "",
    val versionCode: Int = 0,
    val description: String = "",
    val enabled: Boolean = false,
    val update: Boolean = false,
    val remove: Boolean = false,
    val metamodule: Boolean = false,
    val hasWebUi: Boolean = false,
    val dirId: String = "",
)

@Serializable
data class AppBrief(
    val packageName: String = "",
    val label: String = "",
    val uid: Int = 0,
)

@Serializable
data class ProfileInfo(
    val rootAllowed: Boolean? = null,
    val allowRoot: Boolean? = null,
    val umountModules: Boolean? = null,
)

@Serializable
data class AppGroupInfo(
    val uid: Int = 0,
    val primaryPackageName: String = "",
    val apps: List<AppBrief> = emptyList(),
    val profile: ProfileInfo? = null,
    val shouldUmount: Boolean = false,
)

@Serializable
data class WebUiSettingsInfo(
    val enabled: Boolean = false,
    val randomPort: Boolean = false,
    val boundPort: Int = 0,
    val fixedPort: Int = 8787,
    val version: String = "",
    val versionCode: Int = 0,
)

@Serializable
data class LogsInfo(
    val source: String = "ALL",
    @SerialName("text") val text: String = "",
)

/* --------------------------- JS fetch bridge ---------------------------- */

@JsFun(
    """(url, token, method, body, cb) => {
        const opts = { method: method || 'GET', headers: {} };
        if (token) opts.headers['Authorization'] = 'Bearer ' + token;
        if (body) { opts.headers['Content-Type'] = 'application/json'; opts.body = body; }
        fetch(url, opts)
            .then(r => r.text())
            .then(t => cb(t))
            .catch(e => cb('__NEKO_ERR__' + e));
    }"""
)
private external fun jsFetch(url: String, token: String, method: String, body: String, cb: (String) -> Unit)

@JsFun("(text, name) => { const a = document.createElement('a'); const b = new Blob([text], { type: 'text/plain' }); a.href = URL.createObjectURL(b); a.download = name; a.click(); URL.revokeObjectURL(a.href); }")
private external fun jsDownload(text: String, name: String)

fun downloadLog(text: String, name: String) = jsDownload(text, name)

@JsFun("(k) => window.localStorage.getItem(k) || ''")
private external fun jsGetPref(key: String): String

@JsFun("(k, v) => window.localStorage.setItem(k, v)")
private external fun jsSetPref(key: String, value: String)

@JsFun("(u) => window.open(u, '_blank')")
private external fun jsOpenUrl(url: String)

fun getPref(key: String, defaultValue: String): String = jsGetPref(key).ifEmpty { defaultValue }
fun setPref(key: String, value: String) = jsSetPref(key, value)
fun openUrl(url: String) = jsOpenUrl(url)

@JsFun("() => (window.NEKO_WEBUI && window.NEKO_WEBUI.token) || ''")
private external fun jsToken(): String

private suspend fun fetchText(
    path: String,
    method: String = "GET",
    body: String? = null,
): String? = suspendCancellableCoroutine { cont ->
    jsFetch(path, jsToken(), method, body ?: "") { text ->
        cont.resume(if (text.startsWith("__NEKO_ERR__")) null else text)
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

private suspend inline fun <reified T> get(path: String): T? {
    val text = fetchText(path) ?: return null
    return runCatching { json.decodeFromString<T>(text) }.getOrNull()
}

private suspend fun post(path: String, body: String): String? = fetchText(path, "POST", body)

/* ------------------------------ live data ------------------------------- */

object AppData {
    var home by mutableStateOf<HomeInfo?>(null)
    var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
    var groups by mutableStateOf<List<AppGroupInfo>>(emptyList())
    var settings by mutableStateOf<WebUiSettingsInfo?>(null)
    var loaded by mutableStateOf(false)

    suspend fun load() {
        home = get<HomeInfo>("/api/home")
        modules = get<List<ModuleInfo>>("/api/modules") ?: emptyList()
        groups = get<List<AppGroupInfo>>("/api/superuser") ?: emptyList()
        settings = get<WebUiSettingsInfo>("/api/settings")
        loaded = true
    }

    suspend fun loadLogs(source: String): String? =
        get<LogsInfo>("/api/logs?source=" + source)?.text

    suspend fun setSuperuserRoot(uid: Int, allowed: Boolean): Boolean {
        val ok = post("/api/superuser/$uid/root", "{\"allowed\":$allowed}") != null
        if (ok) groups = get<List<AppGroupInfo>>("/api/superuser") ?: groups
        return ok
    }

    suspend fun setSuperuserUmount(uid: Int, umount: Boolean): Boolean {
        val ok = post("/api/superuser/$uid/umount", "{\"umount\":$umount}") != null
        if (ok) groups = get<List<AppGroupInfo>>("/api/superuser") ?: groups
        return ok
    }

    suspend fun setModuleEnabled(id: String, enabled: Boolean): Boolean {
        val action = if (enabled) "enable" else "disable"
        val ok = post("/api/modules/" + encodePath(id) + "/" + action, "{}") != null
        if (ok) modules = get<List<ModuleInfo>>("/api/modules") ?: modules
        return ok
    }

    suspend fun setModuleRemoved(id: String, removed: Boolean): Boolean {
        val action = if (removed) "remove" else "restore"
        val ok = post("/api/modules/" + encodePath(id) + "/" + action, "{}") != null
        if (ok) modules = get<List<ModuleInfo>>("/api/modules") ?: modules
        return ok
    }

    private fun encodePath(value: String): String =
        value.replace("%", "%25").replace("/", "%2F").replace(" ", "%20")

    suspend fun updateWebUi(enabled: Boolean? = null, randomPort: Boolean? = null) {
        val payload = buildString {
            append("{")
            if (enabled != null) append("\"enabled\":").append(enabled)
            if (randomPort != null) {
                if (enabled != null) append(",")
                append("\"randomPort\":").append(randomPort)
            }
            append("}")
        }
        val text = post("/api/settings/webui", payload) ?: return
        runCatching { json.decodeFromString<WebUiSettingsInfo>(text) }.getOrNull()?.let { settings = it }
    }
}

/* ------------------------------- fallback ------------------------------- */

fun fallbackHome(): HomeInfo = HomeInfo(
    status = KernelStatusInfo(
        isRootAvailable = true,
        isLateLoadMode = true,
        hookType = "tracepoint",
        kernelVersion = KernelVersionInfo(5, 10, 245),
        ksuFullVersion = "v1.0.1-NekoSU",
        lkmMode = true,
    ),
    version = "1.0.1",
    versionCode = 40002,
    deviceModel = "Pixel 8 (mock)",
    deviceBrand = "google",
    androidVersion = "14",
    kernelRelease = "5.10.245-android13-8",
    selinuxStatus = "Enforcing",
    seccompStatus = 2,
    moduleCount = 3,
    superuserCount = 2,
)

fun fallbackModules(): List<ModuleInfo> = listOf(
    ModuleInfo("meta-hide", "MetaHide", "NekoSU", "2.3.0", 230, "SUSFS 增强隐藏规则（示例数据）", true, metamodule = false, dirId = "meta-hide"),
    ModuleInfo("zygisk-next", "Zygisk Next", "Dr-TSNG", "1.2.5", 125, "Zygisk 独立实现，为模块提供进程注入能力。", true, metamodule = true, dirId = "zygisk-next"),
    ModuleInfo("tricky-store", "Tricky Store", "5ec1cff", "1.2.0", 120, "证书与密钥库注入（示例）。", false, dirId = "tricky-store"),
)

fun fallbackGroups(): List<AppGroupInfo> = listOf(
    AppGroupInfo(10100, "com.nekosu", listOf(AppBrief("com.nekosu", "NekoSU Manager", 10100)), ProfileInfo(rootAllowed = true), false),
    AppGroupInfo(10123, "com.termux", listOf(AppBrief("com.termux", "Termux", 10123)), ProfileInfo(rootAllowed = true), false),
    AppGroupInfo(10234, "com.nekosu.demo", listOf(AppBrief("com.nekosu.demo", "NekoSU Demo", 10234)), ProfileInfo(rootAllowed = false), true),
)

fun fallbackSettings(): WebUiSettingsInfo = WebUiSettingsInfo(true, false, 8787, 8787, "1.0.1", 40002)
