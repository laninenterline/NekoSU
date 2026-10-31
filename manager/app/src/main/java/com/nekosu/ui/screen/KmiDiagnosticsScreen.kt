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
    val matched = matchKmi(version)
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
                OutlinedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.kmi_diag_summary), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        row(stringResource(R.string.kmi_diag_device), info.deviceModel)
                        row(stringResource(R.string.kmi_diag_android), info.androidVersion)
                        row(stringResource(R.string.kmi_diag_kernel), info.kernelRelease)
                        row(stringResource(R.string.kmi_diag_kmi), kmiText)
                        row(
                            stringResource(R.string.kmi_diag_match),
                            if (matched != null) {
                                stringResource(R.string.kmi_diag_match_ok, matched.kmi)
                            } else {
                                stringResource(R.string.kmi_diag_match_fail, version.toString())
                            },
                        )
                        row(
                            stringResource(R.string.kmi_diag_root),
                            if (status.isRootAvailable) "OK" else "-",
                        )
                        row(stringResource(R.string.kmi_diag_lkm), status.lkmMode?.toString() ?: "-")
                        row(stringResource(R.string.kmi_diag_late_load), status.isLateLoadMode.toString())
                        row(stringResource(R.string.kmi_diag_ksu), "${status.ksuVersion ?: "-"} ${status.ksuFullVersion.orEmpty()}")
                        row(
                            stringResource(R.string.kmi_diag_susfs),
                            if (info.susfsEnabled) {
                                "${info.susfsVersion} (${info.susfsFeatures})"
                            } else {
                                stringResource(R.string.kmi_diag_susfs_off)
                            },
                        )
                    }
                }
            }
            item {
                OutlinedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.kmi_diag_advice_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        val advice = buildList {
                            if (!status.isRootAvailable) add(stringResource(R.string.kmi_diag_advice_no_root))
                            if (matched == null) add(stringResource(R.string.kmi_diag_advice_no_match))
                            if (status.isRootAvailable && !info.susfsEnabled) add(stringResource(R.string.kmi_diag_advice_susfs))
                            if (status.isRootAvailable && matched != null) add(stringResource(R.string.kmi_diag_advice_ok))
                        }
                        advice.forEach { Text("\u2022 $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (status.isRootAvailable) {
                item {
                    var showReboot by remember { mutableStateOf(false) }
                    if (showReboot) {
                        AlertDialog(
                            onDismissRequest = { showReboot = false },
                            title = { Text(stringResource(R.string.kmi_diag_reboot_title)) },
                            text = { Text(stringResource(R.string.kmi_diag_reboot_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showReboot = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                ShellUtils.fastCmd(repository.getRootShell(), "reboot")
                                            }
                                        }
                                    }
                                }) { Text(androidx.compose.ui.res.stringResource(android.R.string.ok)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showReboot = false }) {
                                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                                }
                            },
                        )
                    }
                    TextButton(onClick = { showReboot = true }) {
                        Text(stringResource(R.string.kmi_diag_reboot_button))
                    }
                }
            }
            if (MagicaCompat.isRiskyDevice()) {
                item {
                    Text(
                        stringResource(R.string.kmi_diag_risky_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        copyToClipboard(context, "NekoSU diagnostics", report)
                        scope.launch { snackBarHost.showReplacingSnackbar(copied) }
                    }) { Text(stringResource(R.string.kmi_diag_copy)) }
                    TextButton(onClick = { viewModel.dispatch(HomeUiAction.AwaitInitialData) }) {
                        Text(stringResource(R.string.kmi_diag_refresh))
                    }
                }
            }
        }
    }
}
