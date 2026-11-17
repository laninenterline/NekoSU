package com.nekosu.ui.webui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed class WebUIEvent {
    data object Loading : WebUIEvent()
    data object WebViewReady : WebUIEvent()
    data class Error(val message: String) : WebUIEvent()
    data object Close : WebUIEvent()
    data class ShowAlert(val message: String, val result: JsResult) : WebUIEvent()
    data class ShowConfirm(val message: String, val result: JsResult) : WebUIEvent()
    data class ShowPrompt(val message: String, val defaultValue: String, val result: JsPromptResult) : WebUIEvent()
    data class ShowFileChooser(val intent: Intent) : WebUIEvent()
}

class WebUIState {
    var webView: WebView? = null
    var webViewInterface: WebViewInterface? = null
    var downloadInterface: WebUIDownloadInterface? = null
    lateinit var modDir: String
    var moduleName: String = ""

    var uiEvent by mutableStateOf<WebUIEvent>(WebUIEvent.Loading)
    var isUrlLoaded = false
    var currentInsets: Insets = Insets(0, 0, 0, 0)
    var isInsetsEnabled by mutableStateOf(false)
    var webCanGoBack by mutableStateOf(false)
    var filePathCallback: ValueCallback<Array<Uri>>? = null

    /**
     * Set by [WebUIActivity]'s permission launcher: called on the main thread with the runtime
     * permissions that still have to be requested before a WebUI download starts.
     */
    @Volatile
    var permissionRequester: ((Array<String>) -> Unit)? = null

    private val permissionLock = Any()
    private val pendingDownloadStarts = mutableListOf<() -> Unit>()
    private var permissionRequestInFlight = false

    /**
     * Download saves must not block the WebView JS bridge thread, so when runtime permissions are
     * missing the actual save is queued and only started after the permission round-trip. A missing
     * requester (activity already detached) falls back to starting immediately.
     */
    fun runDownloadWithPermissions(permissions: Array<String>, start: () -> Unit) {
        if (permissions.isEmpty()) {
            start()
            return
        }
        val requester = synchronized(permissionLock) { permissionRequester }
        if (requester == null) {
            start()
            return
        }
        val shouldLaunch = synchronized(permissionLock) {
            pendingDownloadStarts += start
            if (permissionRequestInFlight) {
                false
            } else {
                permissionRequestInFlight = true
                true
            }
        }
        if (!shouldLaunch) return
        val launch = {
            // A launcher can be torn down between our check and the main-thread hop; on failure
            // fall back to running the queued saves immediately instead of dropping them.
            if (!runCatching { requester(permissions) }.isSuccess) onPermissionsResult(emptyMap())
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            launch()
        } else {
            Handler(Looper.getMainLooper()).post { launch() }
        }
    }

    /** Called from the Activity permission launcher with the request result. */
    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") result: Map<String, Boolean>) {
        val pending = synchronized(permissionLock) {
            permissionRequestInFlight = false
            pendingDownloadStarts.toList().also { pendingDownloadStarts.clear() }
        }
        pending.forEach { action -> runCatching { action() } }
    }

    private fun clearPermissionQueue() {
        synchronized(permissionLock) {
            pendingDownloadStarts.clear()
            permissionRequestInFlight = false
        }
    }

    fun onAlertResult() {
        val event = uiEvent
        if (event is WebUIEvent.ShowAlert) {
            event.result.confirm()
            uiEvent = WebUIEvent.WebViewReady
        }
    }

    fun onConfirmResult(confirmed: Boolean) {
        val event = uiEvent
        if (event is WebUIEvent.ShowConfirm) {
            if (confirmed) event.result.confirm() else event.result.cancel()
            uiEvent = WebUIEvent.WebViewReady
        }
    }

    fun onPromptResult(result: String?) {
        val event = uiEvent
        if (event is WebUIEvent.ShowPrompt) {
            if (result != null) event.result.confirm(result) else event.result.cancel()
            uiEvent = WebUIEvent.WebViewReady
        }
    }

    fun onFileChooserResult(uris: Array<Uri>?) {
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
        uiEvent = WebUIEvent.WebViewReady
    }

    fun requestExit() {
        uiEvent = WebUIEvent.Close
    }

    fun dispose() {
        clearPermissionQueue()
        webViewInterface?.destroy()
        webViewInterface = null
        downloadInterface?.destroy()
        downloadInterface = null
        BlobDownloadHandler.cleanup()
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
    }
}
