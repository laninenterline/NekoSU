package com.nekosu.ui.webui.pm

import android.os.SystemClock
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.nekosu.data.packageinfo.InstalledPackageRepository
import com.nekosu.domain.model.InstalledPackageInfo

object KsuPm {
    private const val KSU_PM_VERSION = 1

    /**
     * JS bridge calls must return synchronously and must not stall the WebView on the first
     * package refresh, so a cold cache waits at most this long and the refresh keeps running in
     * the background; later calls pick up the populated cache.
     */
    private const val FIRST_LOAD_WAIT_MS = 500L
    private const val FIRST_LOAD_POLL_MS = 20L

    @Volatile
    private var packageRepository: InstalledPackageRepository? = null

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Any()

    @Volatile
    private var refreshJob: Job? = null

    /**
     * NekoSU keeps the package inventory in InstalledPackageRepository; inject
     * it once when the WebUI Javascript interface is created. A background refresh is kicked
     * off immediately so the first JS call usually hits a warm cache.
     */
    internal fun initialize(repository: InstalledPackageRepository) {
        packageRepository = repository
        if (repository.packages.value.isEmpty()) startBackgroundRefresh(repository)
    }

    private fun packages(): List<InstalledPackageInfo> {
        val repository = packageRepository ?: return emptyList()
        val cached = repository.packages.value
        if (cached.isNotEmpty()) return cached
        startBackgroundRefresh(repository)
        // Short bounded wait: the JS bridge thread is released quickly, and the refresh keeps
        // running so the next ksu.pm call returns the full inventory. A caller that still gets an
        // empty list should retry on a later turn (documented in bindings/js/README.md).
        val deadline = SystemClock.uptimeMillis() + FIRST_LOAD_WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val refreshed = repository.packages.value
            if (refreshed.isNotEmpty()) return refreshed
            Thread.sleep(FIRST_LOAD_POLL_MS)
        }
        return repository.packages.value
    }

    private fun startBackgroundRefresh(repository: InstalledPackageRepository) {
        synchronized(refreshLock) {
            if (refreshJob?.isActive == true) return
            refreshJob = refreshScope.launch { repository.refresh() }
        }
    }

    @JavascriptInterface
    fun version(): Int = KSU_PM_VERSION

    @JavascriptInterface
    fun listPackages(type: String): String {
        val packageNames = packages()
            .filter { packageInfo ->
                when (type.lowercase()) {
                    "system" -> packageInfo.isSystem
                    "user" -> !packageInfo.isSystem
                    else -> true
                }
            }
            .map { it.packageName }
            .distinct()
            .sorted()

        val jsonArray = JSONArray()
        for (pkgName in packageNames) {
            jsonArray.put(pkgName)
        }
        return jsonArray.toString()
    }

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val appsByPackage = packages().groupBy { it.packageName }
        val jsonArray = JSONArray()
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            val matches = appsByPackage[pkgName]
            if (matches.isNullOrEmpty()) {
                val error = JSONObject()
                error.put("packageName", pkgName)
                error.put("error", "Package not found or inaccessible")
                jsonArray.put(error)
                continue
            }
            for (appInfo in matches.sortedBy { it.uid }) {
                jsonArray.put(buildInfo(appInfo))
            }
        }
        return jsonArray.toString()
    }

    private fun buildInfo(appInfo: InstalledPackageInfo): JSONObject = JSONObject().apply {
        put("packageName", appInfo.packageName)
        put("versionName", appInfo.versionName)
        put("versionCode", appInfo.versionCode)
        put("appLabel", appInfo.appLabel)
        put("isSystem", appInfo.isSystem)
        if (appInfo.uid >= 0) {
            put("uid", appInfo.uid)
            put("userId", appInfo.uid / 100000)
        } else {
            put("uid", JSONObject.NULL)
            put("userId", JSONObject.NULL)
        }
    }
}
