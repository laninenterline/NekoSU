package com.nekosu.ui.webadmin

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nekosu.R
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.data.shell.WebAdminCli
import com.nekosu.ui.LocalUiMode
import com.nekosu.ui.UiMode
import com.nekosu.ui.component.settings.SettingsJumpPageWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Log center entry: collect ksud web-admin status/logs for troubleshooting.
 * Moved here from the WebAdmin settings card.
 */
@Composable
fun WebAdminDiagnoseItem(modifier: Modifier = Modifier) {
    val ksuCliRepository: KsuCliRepository = koinInject()
    val cli = remember(ksuCliRepository) { WebAdminCli(ksuCliRepository) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var diagnoseText by remember { mutableStateOf<String?>(null) }

    val runDiagnose: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                diagnoseText = withContext(Dispatchers.IO) { cli.diagnose() }
                busy = false
            }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Material -> SettingsJumpPageWidget(
            icon = Icons.TwoTone.BugReport,
            title = stringResource(R.string.webadmin_diagnose),
            description = stringResource(R.string.webadmin_diagnose_summary),
            onClick = { runDiagnose() },
        )

        UiMode.Miuix -> {
            val colorScheme = MiuixTheme.colorScheme
            Card(modifier = modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.webadmin_diagnose),
                    summary = stringResource(R.string.webadmin_diagnose_summary),
                    startAction = {
                        MiuixIcon(
                            imageVector = Icons.TwoTone.BugReport,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = colorScheme.onSurface,
                        )
                    },
                    onClick = { if (!busy) runDiagnose() },
                )
            }
        }
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
                        diagnoseText = null
                        Toast.makeText(
                            context,
                            context.getString(
                                if (ok) R.string.webadmin_copy_ok else R.string.webadmin_copy_fail,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.webadmin_copy_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { diagnoseText = null }) {
                    Text(stringResource(R.string.webadmin_cancel))
                }
            },
        )
    }
}
