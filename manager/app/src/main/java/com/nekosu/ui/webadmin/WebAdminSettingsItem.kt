package com.nekosu.ui.webadmin

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.OpenInBrowser
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.WebAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nekosu.R
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.data.shell.WebAdminCli
import com.nekosu.ui.LocalUiMode
import com.nekosu.ui.UiMode
import com.nekosu.ui.component.settings.SettingsJumpPageWidget
import com.nekosu.ui.component.settings.SettingsSwitchWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Settings 页的「网页管理器」条目（Material + Miuix 自适应）。
 *
 * 网页服务跑在 ksud 里；这里只做开关、打开/复制带密钥的地址、重置密钥、诊断。
 * 由 Settings 页插入，不需要修改 SettingsPage.kt / SettingsMiuix.kt 的其它内容；
 * 自包含状态与副作用，插入方式：`item { WebAdminSettingsItem() }`。
 */
@Composable
fun WebAdminSettingsItem(modifier: Modifier = Modifier) {
    val ksuCliRepository: KsuCliRepository = koinInject()
    val cli = remember(ksuCliRepository) { WebAdminCli(ksuCliRepository) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var diagnoseText by remember { mutableStateOf<String?>(null) }

    val toast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val refresh: () -> Unit = {
        scope.launch {
            val status = withContext(Dispatchers.IO) { cli.status() }
            enabled = status.lineSequence().any { it.trim() == "enabled=1" }
            running = status.lineSequence().any { it.trim() == "running=1" }
            loaded = true
        }
    }

    LaunchedEffect(cli) { refresh() }

    val onToggle: (Boolean) -> Unit = { target ->
        if (!busy && loaded) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { cli.setEnabled(target) }
                if (ok) {
                    enabled = target
                    running = if (target) {
                        withContext(Dispatchers.IO) { cli.isRunning() }
                    } else {
                        false
                    }
                    toast(
                        context.getString(
                            if (target) R.string.webadmin_enabled_ok else R.string.webadmin_disabled_ok,
                        ),
                    )
                } else {
                    toast(context.getString(R.string.webadmin_ksud_failed))
                }
                busy = false
            }
        }
    }

    val onOpen: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                val url = withContext(Dispatchers.IO) { cli.url() }
                busy = false
                if (url.isBlank()) {
                    toast(context.getString(R.string.webadmin_not_running))
                } else {
                    cli.openInBrowser(context, url)?.let { error ->
                        toast(context.getString(R.string.webadmin_browser_failed, error))
                    }
                }
            }
        }
    }

    val onCopyUrl: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                val url = withContext(Dispatchers.IO) { cli.url() }
                val ok = url.isNotBlank() &&
                    cli.copyToClipboard(context, url, context.getString(R.string.webadmin_title))
                busy = false
                toast(
                    context.getString(
                        if (ok) R.string.webadmin_copy_ok else R.string.webadmin_copy_fail,
                    ),
                )
            }
        }
    }

    val onResetToken: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                val url = withContext(Dispatchers.IO) { cli.resetTokenUrl() }
                busy = false
                if (url.isBlank()) {
                    toast(context.getString(R.string.webadmin_reset_failed))
                } else {
                    enabled = true
                    running = withContext(Dispatchers.IO) { cli.isRunning() }
                    toast(context.getString(R.string.webadmin_reset_ok))
                }
            }
        }
    }

    val onDiagnose: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                diagnoseText = withContext(Dispatchers.IO) { cli.diagnose() }
                busy = false
            }
        }
    }

    val summary = when {
        !loaded -> stringResource(R.string.webadmin_reading)
        enabled && running -> stringResource(R.string.webadmin_summary_on)
        enabled -> stringResource(R.string.webadmin_summary_starting)
        else -> stringResource(R.string.webadmin_summary_off)
    }

    when (LocalUiMode.current) {
        UiMode.Material -> MaterialWebAdminSettingsItem(
            modifier = modifier,
            summary = summary,
            enabled = enabled,
            loaded = loaded,
            busy = busy,
            onToggle = onToggle,
            onOpen = onOpen,
            onCopyUrl = onCopyUrl,
            onResetToken = { showResetConfirm = true },
        )

        UiMode.Miuix -> MiuixWebAdminSettingsItem(
            modifier = modifier,
            summary = summary,
            enabled = enabled,
            loaded = loaded,
            busy = busy,
            onToggle = onToggle,
            onOpen = onOpen,
            onCopyUrl = onCopyUrl,
            onResetToken = { showResetConfirm = true },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.webadmin_reset_confirm_title)) },
            text = { Text(stringResource(R.string.webadmin_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetToken()
                    },
                ) {
                    Text(stringResource(R.string.webadmin_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.webadmin_cancel))
                }
            },
        )
    }

    diagnoseText?.let { text ->
        AlertDialog(
            onDismissRequest = { diagnoseText = null },
            title = { Text(stringResource(R.string.webadmin_diagnose_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ok = cli.copyToClipboard(
                            context,
                            text,
                            context.getString(R.string.webadmin_diagnose_title),
                        )
                        toast(
                            context.getString(
                                if (ok) R.string.webadmin_copy_all_ok else R.string.webadmin_copy_fail,
                            ),
                        )
                        diagnoseText = null
                    },
                ) {
                    Text(stringResource(R.string.webadmin_copy_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { diagnoseText = null }) {
                    Text(stringResource(R.string.webadmin_close))
                }
            },
        )
    }
}

@Composable
private fun MaterialWebAdminSettingsItem(
    modifier: Modifier,
    summary: String,
    enabled: Boolean,
    loaded: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onCopyUrl: () -> Unit,
    onResetToken: () -> Unit,
) {
    Column(modifier = modifier) {
        SettingsSwitchWidget(
            icon = Icons.TwoTone.WebAsset,
            title = stringResource(R.string.webadmin_title),
            description = summary,
            enabled = loaded && !busy,
            checked = enabled,
            onCheckedChange = onToggle,
        )
        SettingsJumpPageWidget(
            icon = Icons.TwoTone.OpenInBrowser,
            title = stringResource(R.string.webadmin_open),
            description = stringResource(R.string.webadmin_open_summary),
            enabled = enabled && !busy,
            onClick = { onOpen() },
        )
        SettingsJumpPageWidget(
            icon = Icons.TwoTone.Link,
            title = stringResource(R.string.webadmin_copy),
            description = stringResource(R.string.webadmin_copy_summary),
            enabled = enabled && !busy,
            onClick = { onCopyUrl() },
        )
        SettingsJumpPageWidget(
            icon = Icons.TwoTone.RestartAlt,
            title = stringResource(R.string.webadmin_reset),
            description = stringResource(R.string.webadmin_reset_summary),
            enabled = !busy,
            onClick = { onResetToken() },
        )
    }
}

@Composable
private fun MiuixWebAdminSettingsItem(
    modifier: Modifier,
    summary: String,
    enabled: Boolean,
    loaded: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onCopyUrl: () -> Unit,
    onResetToken: () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        SwitchPreference(
            title = stringResource(R.string.webadmin_title),
            summary = summary,
            startAction = { WebAdminPreferenceIcon(Icons.TwoTone.WebAsset) },
            checked = enabled,
            enabled = loaded && !busy,
            onCheckedChange = onToggle,
        )
        ArrowPreference(
            title = stringResource(R.string.webadmin_open),
            summary = stringResource(R.string.webadmin_open_summary),
            startAction = { WebAdminPreferenceIcon(Icons.TwoTone.OpenInBrowser) },
            onClick = { if (enabled && !busy) onOpen() },
        )
        ArrowPreference(
            title = stringResource(R.string.webadmin_copy),
            summary = stringResource(R.string.webadmin_copy_summary),
            startAction = { WebAdminPreferenceIcon(Icons.TwoTone.Link) },
            onClick = { if (enabled && !busy) onCopyUrl() },
        )
        ArrowPreference(
            title = stringResource(R.string.webadmin_reset),
            summary = stringResource(R.string.webadmin_reset_summary),
            startAction = { WebAdminPreferenceIcon(Icons.TwoTone.RestartAlt) },
            onClick = { if (!busy) onResetToken() },
        )
    }
}

@Composable
private fun WebAdminPreferenceIcon(icon: ImageVector) {
    MiuixIcon(
        icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}
