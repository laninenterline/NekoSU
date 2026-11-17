package com.nekosu.ui.security

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.nekosu.ui.component.settings.SettingsSwitchWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single insertable settings entry for stealth mode.
 *
 * The Lead inserts this composable into the settings screens; it deliberately
 * does not depend on SettingsPage/SettingsMiuix. Turning stealth on makes the
 * kernel stop reporting the Manager role, so the rest of the UI degrades to
 * the "not installed" state on the next refresh/recreate.
 */
@Composable
fun StealthSettingsItem(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kernelAvailable by remember { mutableStateOf(Stealth.kernelHasStealthFeature()) }
    var checked by remember { mutableStateOf(Stealth.isEnabled()) }
    var busy by remember { mutableStateOf(false) }

    val unsupported = stringResource(R.string.stealth_settings_unsupported)
    val enabledSummary = stringResource(R.string.stealth_settings_summary_on)
    val disabledSummary = stringResource(R.string.stealth_settings_summary_off)
    val failedMessage = stringResource(R.string.stealth_settings_failed)
    val unsupportedMessage = stringResource(R.string.stealth_settings_unsupported_toast)
    val enabledMessage = stringResource(R.string.stealth_settings_enabled_toast)
    val disabledMessage = stringResource(R.string.stealth_settings_disabled_toast)

    Column(modifier = modifier) {
        SettingsSwitchWidget(
            icon = Icons.TwoTone.Security,
            title = stringResource(R.string.stealth_settings_title),
            description = when {
                !kernelAvailable -> unsupported
                checked -> enabledSummary
                else -> disabledSummary
            },
            enabled = kernelAvailable && !busy,
            isError = !kernelAvailable,
            checked = checked,
            onCheckedChange = { target ->
                scope.launch {
                    busy = true
                    val result = withContext(Dispatchers.IO) {
                        Stealth.setEnabledReporting(target)
                    }
                    busy = false
                    when (result) {
                        Stealth.SetResult.SUCCESS -> {
                            checked = target
                            Toast.makeText(
                                context,
                                if (target) enabledMessage else disabledMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                            restartUiFresh(context)
                        }

                        Stealth.SetResult.UNSUPPORTED -> {
                            kernelAvailable = false
                            Toast.makeText(context, unsupportedMessage, Toast.LENGTH_LONG).show()
                        }

                        Stealth.SetResult.FAILED -> {
                            Toast.makeText(context, failedMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )
        Text(
            text = stringResource(R.string.stealth_settings_dial_hint, StealthCodeStore.DEFAULT_CODE),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
    }
}
