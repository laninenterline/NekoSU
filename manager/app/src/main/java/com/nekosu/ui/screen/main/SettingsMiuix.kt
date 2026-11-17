package com.nekosu.ui.screen.main

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Fence
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.RemoveCircle
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nekosu.R
import com.nekosu.ui.LocalUiMode
import com.nekosu.ui.security.StealthSettingsItem
import com.nekosu.data.settings.LauncherIconHider
import com.nekosu.ui.security.UsbDebugHideSettingsItem
import com.nekosu.ui.webadmin.WebAdminSettingsItem
import com.nekosu.ui.UiMode
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.navigation.Route
import com.nekosu.ui.viewmodel.HomeViewModel
import com.nekosu.ui.viewmodel.SettingsUiAction
import com.nekosu.ui.viewmodel.SettingsViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的 Miuix 风格实现。复用 [SettingsViewModel] / [HomeViewModel] 的状态与
 * [SettingsUiAction]，仅把 Material 的 SegmentedColumn/SettingsSwitchWidget 等
 * 视觉组件替换为 Miuix 的 Card + Preference。
 *
 * 已与 Material 版对齐：设置搜索（标题/描述/关键词过滤 + 跳转）、备份/恢复
 * （Toast 提示）、隐藏管理器入口；其余设置项与 Material 版一一对应。
 */
@Composable
fun SettingsPageMiuix(bottomPadding: Dp) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val uiMode = LocalUiMode.current

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val source = miuixPreferencesBackupFile(context)
                if (!source.isFile) return@runCatching false
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                } != null
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(
                        if (ok) R.string.settings_backup_saved else R.string.settings_backup_failed,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val target = miuixPreferencesBackupFile(context)
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null && target.length() > 0
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(
                        if (ok) R.string.settings_restore_done else R.string.settings_backup_failed,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val uiModeItems = listOf(
        stringResource(R.string.ui_mode_material),
        stringResource(R.string.ui_mode_miuix),
    )
    val checkUpdateSummary = stringResource(R.string.settings_check_manager_update_summary)
    val checkModuleUpdateSummary = stringResource(R.string.settings_check_module_update_summary)
    val checkBetaUpdateSummary = stringResource(R.string.settings_check_beta_update_summary)
    val uiModeIndex = when (uiMode) {
        UiMode.Miuix -> 1
        UiMode.Material -> 0
    }
    val searchState = rememberTextFieldState()
    val searchQuery = searchState.text.toString()
    val resolvedSearchEntries = (settingSearchEntries() + extraSettingSearchEntries()).map { entry ->
        Triple(
            entry,
            stringResource(entry.titleRes),
            entry.descriptionRes?.let { stringResource(it) } ?: "",
        )
    }
    val filteredSearchEntries = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        resolvedSearchEntries.filter { (entry, title, description) ->
            title.contains(searchQuery, ignoreCase = true) ||
                description.contains(searchQuery, ignoreCase = true) ||
                entry.keywords.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings),
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = {},
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextField(
                    state = searchState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (searchQuery.isNotBlank()) {
                if (filteredSearchEntries.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = stringResource(R.string.settings_search_empty),
                                startAction = { PreferenceIcon(Icons.TwoTone.Search) },
                            )
                        }
                    }
                } else {
                    items(filteredSearchEntries, key = { it.second }) { (entry, title, description) ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = title,
                                summary = description.ifBlank {
                                    stringResource(R.string.settings_search_in_page)
                                },
                                startAction = { PreferenceIcon(Icons.TwoTone.Search) },
                                onClick = {
                                    val route = entry.route
                                    if (route != null) {
                                        navigator.push(route)
                                    } else {
                                        searchState.edit { replace(0, length, "") }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.settings_search_cleared),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (searchQuery.isBlank()) {
                item {
                    Column {
                        SettingsSectionHeader(stringResource(R.string.settings_general))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_check_manager_update),
                                summary = checkUpdateSummary,
                                startAction = { PreferenceIcon(Icons.TwoTone.Update) },
                                checked = uiState.checkManagerUpdate,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetManagerUpdateCheck(enabled),
                                    )
                                },
                            )

                            SwitchPreference(
                                title = stringResource(R.string.settings_check_beta_update),
                                summary = checkBetaUpdateSummary,
                                startAction = { PreferenceIcon(Icons.TwoTone.Science) },
                                checked = uiState.checkBetaUpdate,
                                enabled = uiState.checkManagerUpdate,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetBetaUpdateCheck(enabled),
                                    )
                                },
                            )

                            SwitchPreference(
                                title = stringResource(R.string.settings_check_module_update),
                                summary = checkModuleUpdateSummary,
                                startAction = { PreferenceIcon(Icons.TwoTone.Extension) },
                                checked = uiState.checkModuleUpdate,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetModuleUpdateCheck(enabled),
                                    )
                                },
                            )

                            OverlayDropdownPreference(
                                title = stringResource(R.string.settings_ui_mode),
                                summary = stringResource(R.string.settings_ui_mode_summary),
                                items = uiModeItems,
                                startAction = { PreferenceIcon(Icons.TwoTone.Settings) },
                                selectedIndex = uiModeIndex,
                                onSelectedIndexChange = { index ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetUiMode(
                                            if (index == 1) UiMode.Miuix else UiMode.Material,
                                        ),
                                    )
                                },
                            )

                            ArrowPreference(
                                title = stringResource(R.string.theme_settings),
                                summary = stringResource(R.string.theme_settings),
                                startAction = { PreferenceIcon(Icons.TwoTone.Settings) },
                                onClick = { navigator.push(Route.ThemeSettings) },
                            )

                            ArrowPreference(
                                title = stringResource(R.string.settings_backup_title),
                                summary = stringResource(R.string.settings_backup_summary),
                                startAction = { PreferenceIcon(Icons.TwoTone.Save) },
                                onClick = {
                                    exportSettingsLauncher.launch("NekoSU_settings_backup.bin")
                                },
                            )

                            ArrowPreference(
                                title = stringResource(R.string.settings_restore_title),
                                summary = stringResource(R.string.settings_restore_summary),
                                startAction = { PreferenceIcon(Icons.TwoTone.RestartAlt) },
                                onClick = {
                                    importSettingsLauncher.launch(
                                        arrayOf("application/octet-stream", "*/*"),
                                    )
                                },
                            )
                        }
                    }
                }

                if (homeState.systemStatus.isFullFeatured) {
                    item {
                        Column {
                            SettingsSectionHeader(stringResource(R.string.settings_root_and_loading))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                val suSummary = when (uiState.suStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_sucompat_summary)
                                }
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.settings_sucompat),
                                    summary = suSummary,
                                    items = listOf(
                                        stringResource(R.string.settings_mode_default),
                                        stringResource(R.string.settings_mode_disable_until_reboot),
                                        stringResource(R.string.settings_mode_disable_always),
                                    ),
                                    startAction = { PreferenceIcon(Icons.TwoTone.RemoveModerator) },
                                    selectedIndex = uiState.suCompatMode,
                                    enabled = uiState.suStatus == "supported",
                                    onSelectedIndexChange = { index ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetSuCompatMode(index))
                                    },
                                )

                                val umountSummary = when (uiState.kernelUmountStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_kernel_umount_summary)
                                }
                                SwitchPreference(
                                    title = stringResource(R.string.settings_kernel_umount),
                                    summary = umountSummary,
                                    startAction = { PreferenceIcon(Icons.TwoTone.RemoveCircle) },
                                    checked = uiState.isKernelUmountEnabled,
                                    enabled = uiState.kernelUmountStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetKernelUmount(enabled))
                                    },
                                )

                                SwitchPreference(
                                    title = stringResource(R.string.settings_umount_modules_default),
                                    summary = stringResource(R.string.settings_umount_modules_default_summary),
                                    startAction = { PreferenceIcon(Icons.TwoTone.FolderDelete) },
                                    checked = uiState.defaultUmountModules,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetDefaultUmountModules(enabled),
                                        )
                                    },
                                )

                                val adbRootSummary = when (uiState.adbRootStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_adb_root_summary)
                                }
                                SwitchPreference(
                                    title = stringResource(R.string.settings_adb_root),
                                    summary = adbRootSummary,
                                    startAction = { PreferenceIcon(Icons.TwoTone.Adb) },
                                    checked = uiState.isAdbRootEnabled,
                                    enabled = uiState.adbRootStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetAdbRoot(enabled))
                                    },
                                )

                                val shellSuSummary = when (uiState.shellSuAdbOnlyStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_shell_su_adb_only_summary)
                                }
                                SwitchPreference(
                                    title = stringResource(R.string.settings_shell_su_adb_only),
                                    summary = shellSuSummary,
                                    startAction = { PreferenceIcon(Icons.TwoTone.VerifiedUser) },
                                    checked = uiState.isShellSuAdbOnlyEnabled,
                                    enabled = uiState.shellSuAdbOnlyStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetShellSuAdbOnly(enabled),
                                        )
                                    },
                                )

                                SwitchPreference(
                                    title = stringResource(R.string.settings_soft_reboot),
                                    summary = stringResource(R.string.settings_soft_reboot_summary),
                                    startAction = { PreferenceIcon(Icons.TwoTone.RestartAlt) },
                                    checked = homeState.systemStatus.isLateLoadMode || uiState.useSoftReboot,
                                    enabled = homeState.systemStatus.isFullFeatured &&
                                        !homeState.systemStatus.isLateLoadMode,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetUseSoftReboot(enabled))
                                    },
                                )

                                ArrowPreference(
                                    title = stringResource(R.string.settings_profile_template),
                                    summary = stringResource(R.string.settings_profile_template_summary),
                                    startAction = { PreferenceIcon(Icons.TwoTone.Fence) },
                                    onClick = { navigator.push(Route.AppProfileTemplate) },
                                )
                            }
                        }
                    }

                    item {
                        Column {
                            SettingsSectionHeader(stringResource(R.string.settings_hidden_and_security))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_selinux_hide_summary)
                                }
                                SwitchPreference(
                                    title = stringResource(R.string.settings_selinux_hide),
                                    summary = selinuxHideSummary,
                                    startAction = { PreferenceIcon(Icons.TwoTone.Policy) },
                                    checked = uiState.isSelinuxHideEnabled,
                                    enabled = uiState.selinuxHideStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetSelinuxHide(enabled))
                                    },
                                )

                                val avcSpoofSummary = when (uiState.avcSpoofStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_avc_spoof_summary)
                                }
                                SwitchPreference(
                                    title = stringResource(R.string.settings_avc_spoof),
                                    summary = avcSpoofSummary,
                                    startAction = { PreferenceIcon(Icons.TwoTone.Security) },
                                    checked = uiState.isAvcSpoofEnabled,
                                    enabled = uiState.avcSpoofStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetAvcSpoof(enabled))
                                    },
                                )

                                val sulogSummary = when (uiState.sulogStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_sulog_summary)
                                }
                                SwitchPreference(
                                    title = stringResource(R.string.settings_sulog),
                                    summary = sulogSummary,
                                    startAction = { PreferenceIcon(Icons.AutoMirrored.TwoTone.Article) },
                                    checked = uiState.isSuLogEnabled,
                                    enabled = uiState.sulogStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetSuLog(enabled))
                                    },
                                )


                                ArrowPreference(
                                    title = stringResource(R.string.block_modules_title),
                                    summary = stringResource(R.string.block_modules_summary),
                                    startAction = { PreferenceIcon(Icons.TwoTone.RemoveModerator) },
                                    onClick = { navigator.push(Route.BlockModules) },
                                )

                                ArrowPreference(
                                    title = stringResource(R.string.hidden_manager_title),
                                    summary = stringResource(R.string.hidden_manager_summary),
                                    startAction = { PreferenceIcon(Icons.TwoTone.Security) },
                                    onClick = { navigator.push(Route.HiddenManager) },
                                )

                                StealthSettingsItem()

                                UsbDebugHideSettingsItem()

                                val iconContext = LocalContext.current
                                val iconSettings = koinInject<com.nekosu.data.AppSettingsRepository>()
                                var hideIcon by remember { mutableStateOf(LauncherIconHider.isHidden(iconSettings)) }
                                SwitchPreference(
                                    title = stringResource(R.string.hide_desktop_icon_title),
                                    summary = stringResource(R.string.hide_desktop_icon_summary),
                                    startAction = { PreferenceIcon(Icons.TwoTone.VisibilityOff) },
                                    checked = hideIcon,
                                    onCheckedChange = { value ->
                                        hideIcon = value
                                        LauncherIconHider.setHidden(iconContext, iconSettings, value)
                                    },
                                )

                                ArrowPreference(
                                    title = stringResource(R.string.manager_config_title),
                                    summary = stringResource(R.string.manager_config_summary),
                                    startAction = { PreferenceIcon(Icons.TwoTone.Security) },
                                    onClick = { navigator.push(Route.DynamicManager) },
                                )
                            }
                        }
                    }
                }

                item {
                    Column {
                        SettingsSectionHeader(stringResource(R.string.tools))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = stringResource(R.string.log_center_title),
                                summary = stringResource(R.string.log_center_summary),
                                startAction = { PreferenceIcon(Icons.AutoMirrored.TwoTone.Article) },
                                onClick = { navigator.push(Route.LogCenter) },
                            )

                            if (homeState.systemStatus.isFullFeatured) {
                                WebAdminSettingsItem()

                                if (uiState.isKernelUmountEnabled) {
                                    ArrowPreference(
                                        title = stringResource(R.string.umount_path_manager),
                                        summary = stringResource(R.string.umount_path_manager_summary),
                                        startAction = { PreferenceIcon(Icons.TwoTone.FolderOff) },
                                        onClick = { navigator.push(Route.UmountManager) },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        SettingsSectionHeader(stringResource(R.string.about))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = stringResource(R.string.about),
                                summary = stringResource(R.string.app_name),
                                startAction = { PreferenceIcon(Icons.TwoTone.Info) },
                                onClick = { navigator.push(Route.About) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
    )
}

@Composable
private fun PreferenceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

private fun miuixPreferencesBackupFile(context: android.content.Context): File =
    File(File(context.filesDir, "datastore"), "app_preferences.preferences_pb")
