package com.nekosu.ui.security

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.UsbOff
import androidx.compose.material.icons.twotone.Usb
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
import com.nekosu.ui.component.settings.SettingsSwitchWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val PREF = "usb_hide_enabled"

/**
 * Hide the framework's "USB debugging enabled" flag from app detection while
 * keeping adbd alive (ksud usb-hide re-adds the adb USB function and restarts
 * adbd periodically).
 */
@Composable
fun UsbDebugHideSettingsItem() {
    val context = LocalContext.current
    val ksuCli = koinInject<KsuCliRepository>()
    val settings = koinInject<AppSettingsRepository>()
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(settings.getBoolean(PREF, false)) }
    val onText = stringResource(R.string.usb_hide_enabled)
    val offText = stringResource(R.string.usb_hide_disabled)

    SettingsSwitchWidget(
        icon = if (enabled) Icons.TwoTone.UsbOff else Icons.TwoTone.Usb,
        title = stringResource(R.string.usb_hide_title),
        description = stringResource(R.string.usb_hide_summary),
        checked = enabled,
        onCheckedChange = { value ->
            enabled = value
            settings.putBoolean(PREF, value)
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        ksuCli.execKsud("usb-hide ${if (value) "enable" else "disable"}", true)
                    }
                }
                Toast.makeText(context, if (value) onText else offText, Toast.LENGTH_SHORT).show()
            }
        },
    )
}
