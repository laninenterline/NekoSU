package com.nekosu.ui.security

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.SystemUpdateAlt
import androidx.compose.material.icons.twotone.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.ui.component.settings.SettingsBaseWidget
import com.nekosu.ui.component.settings.SettingsSwitchWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val PREF_ALLOW = "allow_hot_update"

/**
 * Enable the hot-update capability (next load keeps the module kobject, so it can
 * be unloaded) and trigger an in-place KO reload without re-running the exploit.
 */
@Composable
fun HotUpdateSettingsItem() {
    val context = LocalContext.current
    val ksuCli = koinInject<KsuCliRepository>()
    val settings = koinInject<AppSettingsRepository>()
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(settings.getBoolean(PREF_ALLOW, false)) }

    val started = stringResource(R.string.hot_update_started)
    val failed = stringResource(R.string.hot_update_failed)
    val savedThenReload = stringResource(R.string.hot_update_capability_hint)

    Column {
        SettingsSwitchWidget(
            icon = Icons.TwoTone.VerifiedUser,
            title = stringResource(R.string.hot_update_capability_title),
            description = stringResource(R.string.hot_update_capability_summary),
            checked = allowed,
            onCheckedChange = { value ->
                allowed = value
                settings.putBoolean(PREF_ALLOW, value)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            ksuCli.execKsud("hot-update --set-capability $value", true)
                        }
                    }
                    if (value) Toast.makeText(context, savedThenReload, Toast.LENGTH_LONG).show()
                }
            },
        )
        SettingsBaseWidget(
            icon = Icons.TwoTone.SystemUpdateAlt,
            title = stringResource(R.string.hot_update_title),
            description = stringResource(R.string.hot_update_summary),
            onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            ksuCli.execKsud(
                                "hot-update --detach --package ${context.packageName}",
                                true,
                            )
                        }.getOrDefault(false)
                    }
                    Toast.makeText(context, if (ok) started else failed, Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}
