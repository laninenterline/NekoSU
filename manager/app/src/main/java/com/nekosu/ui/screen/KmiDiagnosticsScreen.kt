package com.nekosu.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nekosu.KernelVersion
import com.nekosu.ui.util.embeddedKmis
import com.nekosu.ui.util.matchKmi
import com.nekosu.R
import com.nekosu.domain.model.HomeDashboardState
import com.nekosu.magica.MagicaCompat
import com.nekosu.ui.component.SwipeableSnackbarHost
import com.nekosu.ui.component.settings.AppBackButton
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.util.LocalSnackbarHost
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import com.nekosu.ui.util.showReplacingSnackbar
import com.nekosu.ui.viewmodel.HomeUiAction
import com.nekosu.ui.viewmodel.HomeViewModel
import com.nekosu.data.shell.KsuCliRepository
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KmiDiagnosticsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<HomeViewModel>()
    val repository: KsuCliRepository = koinInject()
    val state: HomeDashboardState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val copied = stringResource(R.string.kmi_diag_copied)

    val info = state.systemInfo
    val status = state.systemStatus
    val version = status.kernelVersion
    val matched = matchKmi(version, info.kernelRelease, android.os.Build.VERSION.SDK_INT)
    val kmiText = matched?.kmi ?: "-"

    val report = buildString {
        appendLine("device: ${info.deviceModel}")
        appendLine("android: ${info.androidVersion}")
        appendLine("kernel release: ${info.kernelRelease}")
        appendLine("kernel version: $version")
        appendLine("kmi: $kmiText")
        appendLine("embedded kmi: ${embeddedKmis.joinToString { it.kmi }}")
        appendLine("root available: ${status.isRootAvailable}")
        appendLine("lkm mode: ${status.lkmMode}")
        appendLine("late-load mode: ${status.isLateLoadMode}")
        appendLine("ksu version: ${status.ksuVersion} ${status.ksuFullVersion ?: ""}")
        appendLine("susfs: enabled=${info.susfsEnabled} version=${info.susfsVersion} features=${info.susfsFeatures}")
        appendLine("risky vendor: ${MagicaCompat.isRiskyDevice()}")
    }

    @Composable
    fun row(title: String, value: String) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.kmi_diag_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        val advice = buildList {
            if (!status.isRootAvailable) add("Root 不可用：请先通过越狱入口（Magica）或手动 ksud late-load 获取临时 root")
            if (matched == null) add("内置 KMI 未匹配当前内核")
            if (status.isRootAvailable && !info.susfsEnabled) add("SuSFS 未启用")
            if (status.isRootAvailable && matched != null) add("KMI 匹配正常")
        }
        val sharedModel = com.nekosu.shared.SharedKmiModel(
            device = info.deviceModel,
            android = info.androidVersion,
            kernelRelease = info.kernelRelease,
            kernelVersion = version.toString(),
            kmi = kmiText,
            kmiMatched = matched != null,
            rootAvailable = status.isRootAvailable,
            lkmMode = when (status.lkmMode) {
                true -> "LKM"
                false -> "Built-in"
                null -> "-"
            },
            lateLoad = status.isLateLoadMode,
            ksuVersion = "${status.ksuVersion ?: "-"} ${status.ksuFullVersion.orEmpty()}",
            susfsText = if (info.susfsEnabled) "${info.susfsVersion} (${info.susfsFeatures})" else "未启用",
            hook = status.hookType.ifBlank { "-" },
            advice = advice,
            riskyVendor = MagicaCompat.isRiskyDevice(),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                com.nekosu.shared.SharedKmiContent(
                    model = sharedModel,
                    onCopyReport = {
                        copyToClipboard(context, "NekoSU diagnostics", report)
                        scope.launch { snackBarHost.showReplacingSnackbar(copied) }
                    },
                    onRefresh = { viewModel.dispatch(HomeUiAction.AwaitInitialData) },
                )
            }
        }
    }
}
