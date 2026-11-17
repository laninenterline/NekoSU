package com.nekosu.ui.webui

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.packageinfo.AppIconDataSource
import com.nekosu.data.packageinfo.InstalledPackageRepository
import com.nekosu.data.webui.WebUiRepository
import com.nekosu.ui.LocalUiMode
import com.nekosu.ui.UiMode
import com.nekosu.ui.theme.KernelSUTheme
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.viewmodel.SuperUserViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        super.onCreate(savedInstanceState)

        setContent {
            val themeConfig: ThemeConfig = koinInject()
            val uiMode = themeConfig.uiMode
            CompositionLocalProvider(LocalUiMode provides uiMode) {
                KernelSUTheme(uiMode = uiMode) {
                    MainContent(activity = this, onFinish = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainContent(activity: ComponentActivity, onFinish: () -> Unit) {
    val moduleId = remember { activity.intent.data?.getQueryParameter("id") }
    val webUIState = remember { WebUIState() }
    val superUserViewModel = koinViewModel<SuperUserViewModel>()
    val settingsRepository = koinInject<AppSettingsRepository>()
    val packageRepository = koinInject<InstalledPackageRepository>()
    val appIconDataSource = koinInject<AppIconDataSource>()
    val webUiRepository = koinInject<WebUiRepository>()
    val monetColorsProvider = koinInject<MonetColorsProvider>()
    val colorsCss = monetColorsProvider.getColorsCss()
    val currentColorsCss = rememberUpdatedState(colorsCss)

    // WebUI downloads save through ksu_download from the JS bridge; POST_NOTIFICATIONS
    // (API 33+) and WRITE_EXTERNAL_STORAGE (API 26-28 public Downloads) are requested on
    // demand before the save, never from the WebView bridge thread itself. A denial is
    // explained with a Toast instead of failing silently.
    val downloadPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        webUIState.onPermissionsResult(result)
        showDownloadPermissionDeniedToasts(activity, result)
    }
    DisposableEffect(webUIState) {
        webUIState.permissionRequester = { permissions ->
            downloadPermissionLauncher.launch(permissions)
        }
        onDispose { webUIState.permissionRequester = null }
    }

    LaunchedEffect(moduleId) {
        if (moduleId == null) {
            onFinish()
            return@LaunchedEffect
        }
        prepareWebView(
            activity,
            moduleId,
            webUIState,
            superUserViewModel,
            settingsRepository,
            packageRepository,
            appIconDataSource,
            webUiRepository,
            { currentColorsCss.value },
        )
    }

    DisposableEffect(Unit) {
        onDispose { webUIState.dispose() }
    }

    when (val event = webUIState.uiEvent) {
        is WebUIEvent.Error -> {
            LaunchedEffect(event) {
                Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                onFinish()
            }
        }

        is WebUIEvent.Close -> {
            LaunchedEffect(event) { onFinish() }
        }

        else -> {}
    }
    val isLoading = webUIState.uiEvent is WebUIEvent.Loading

    Crossfade(targetState = isLoading, animationSpec = tween(300)) { loading ->
        if (loading) {
            LoadingContent()
        } else {
            WebUIScreen(webUIState = webUIState)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator()
            }
        }

        UiMode.Material -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }
    }
}

/**
 * Explains denied WebUI download permissions once per request: notification denial only disables
 * progress notifications, while storage denial makes API 26-28 fall back to the app-private
 * download folder instead of the public Download folder.
 */
private fun showDownloadPermissionDeniedToasts(
    activity: ComponentActivity,
    result: Map<String, Boolean>,
) {
    result.filterValues { granted -> !granted }.keys.forEach { permission ->
        val messageRes = when (permission) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE ->
                R.string.webui_download_storage_permission_denied

            Manifest.permission.POST_NOTIFICATIONS ->
                R.string.webui_download_notification_permission_denied

            else -> null
        }
        messageRes?.let { resId ->
            Toast.makeText(activity, activity.getString(resId), Toast.LENGTH_LONG).show()
        }
    }
}
