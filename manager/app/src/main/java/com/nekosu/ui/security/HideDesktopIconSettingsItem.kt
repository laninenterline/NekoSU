package com.nekosu.ui.security

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.settings.LauncherIconHider
import com.nekosu.ui.component.settings.SettingsSwitchWidget
import org.koin.compose.koinInject

@Composable
fun HideDesktopIconSettingsItem() {
    val context = LocalContext.current
    val settings = koinInject<AppSettingsRepository>()
    var hidden by remember { mutableStateOf(LauncherIconHider.isHidden(settings)) }
    SettingsSwitchWidget(
        icon = Icons.TwoTone.VisibilityOff,
        title = stringResource(R.string.hide_desktop_icon_title),
        description = stringResource(R.string.hide_desktop_icon_summary),
        checked = hidden,
        onCheckedChange = { value ->
            hidden = value
            LauncherIconHider.setHidden(context, settings, value)
        },
    )
}
