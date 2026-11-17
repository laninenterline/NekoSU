package com.nekosu.ui.screen.main

import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.FilterList
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.VolunteerActivism
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nekosu.BuildConfig
import com.nekosu.Natives.KernelPatchImplementation
import com.nekosu.R
import com.nekosu.domain.model.ManagerUpdateChannel
import com.nekosu.domain.model.ManagerUpdateInfo
import com.nekosu.domain.usecase.EnqueueManagerUpdateUseCase
import com.nekosu.ui.security.Stealth
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.navigation.Route
import com.nekosu.ui.util.LocalPermissionRequestInterface
import com.nekosu.ui.util.downloader.downloadManagerUpdate
import com.nekosu.ui.viewmodel.HomeUiAction
import com.nekosu.ui.viewmodel.HomeUiEvent
import com.nekosu.ui.viewmodel.HomeUiState
import com.nekosu.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 首页的 Miuix 风格实现。复用 [HomeViewModel] 与 Material 版完全相同的状态，
 * 只把视觉组件替换为 Miuix（Card / BasicComponent / TopAppBar）。
 *
 * 与 Material 版对齐的能力：SuSFS 配置入口、完整重启方式（含 userspace）、
 * 管理器更新确认（含 changelog）对话框。
 */
@Composable
fun HomePageMiuix(bottomPadding: Dp) {
    val context = LocalContext.current
    val viewModel = koinViewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val enqueueManagerUpdate = koinInject<EnqueueManagerUpdateUseCase>()

    var showRebootMenu by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<ManagerUpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeUiAction.AwaitInitialData)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.Error -> if (event.message.isNotBlank()) {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (uiState.isCoreDataLoaded) {
                        if (uiState.systemInfo.susfsVersionSupported && !Stealth.looksStealthy()) {
                            IconButton(onClick = { navigator.push(Route.SuSFSConfig) }) {
                                Icon(
                                    Icons.TwoTone.Tune,
                                    contentDescription = stringResource(R.string.susfs_config_setting_title),
                                )
                            }
                        }
                        if (uiState.systemStatus.isRootAvailable && !Stealth.looksStealthy()) {
                            IconButton(
                                onClick = { showRebootMenu = true },
                            ) {
                                Icon(
                                    Icons.TwoTone.PowerSettingsNew,
                                    contentDescription = stringResource(R.string.reboot),
                                )
                            }
                        }
                    }
                },
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
            if (uiState.isCoreDataLoaded) {
                item { HomeWarningsMiuix(uiState) }
                item { HomeStatusCardMiuix(uiState) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ManagerUpdateCardMiuix(uiState.stableManagerUpdate) { pendingUpdate = it }
                    ManagerUpdateCardMiuix(uiState.betaManagerUpdate) { pendingUpdate = it }
                }
            }
            if (uiState.isBetaManagerUpdateCheckFailed) {
                item {
                    WarningCardMiuix(stringResource(R.string.beta_update_check_failed))
                }
            }
            if (uiState.isExtendedDataLoaded) {
                item { HomeInfoCardMiuix(uiState) }
            }
            if (!uiState.isSimpleMode) {
                item { HomeLinksCardMiuix() }
            }
        }
    }

    if (showRebootMenu) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager?
        var methods = mapOf(
            R.string.reboot to "",
            R.string.reboot_soft to "soft_reboot",
            R.string.reboot_recovery to "recovery",
            R.string.reboot_bootloader to "bootloader",
            R.string.reboot_download to "download",
            R.string.reboot_edl to "edl",
        )

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
            methods = methods + (R.string.reboot_userspace to "userspace")
        }

        WindowDialog(
            show = showRebootMenu,
            title = stringResource(R.string.reboot),
            onDismissRequest = { showRebootMenu = false },
            content = {
                Column {
                    methods.forEach { (titleRes, reason) ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(titleRes),
                            onClick = {
                                showRebootMenu = false
                                viewModel.dispatch(HomeUiAction.Reboot(reason))
                            },
                        )
                    }
                }
            },
        )
    }

    pendingUpdate?.let { update ->
        val updateChannelTitle = stringResource(
            if (update.channel == com.nekosu.domain.model.ManagerUpdateChannel.STABLE) {
                R.string.manager_update_stable
            } else {
                R.string.manager_update_beta
            },
        )
        val updateDetails = stringResource(
            R.string.manager_update_details,
            update.versionName,
            update.versionCode,
            update.abi,
        )
        val updateContent = if (update.changelog.isBlank()) {
            updateDetails
        } else {
            "$updateDetails\n\n${update.changelog}"
        }
        WindowDialog(
            show = true,
            title = updateChannelTitle,
            onDismissRequest = { pendingUpdate = null },
            content = {
                Column {
                    Text(
                        text = updateContent,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.cancel),
                            onClick = { pendingUpdate = null },
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.module_update),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                pendingUpdate = null
                                downloadManagerUpdate(
                                    context,
                                    permissionRequestInterface,
                                    update,
                                    enqueueManagerUpdate,
                                )
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun HomeWarningsMiuix(
    uiState: HomeUiState,
) {
    val systemStatus = uiState.systemStatus
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (uiState.systemStatus.isManager && !uiState.systemStatus.isFullFeatured) {
            val kernelApi = systemStatus.kernelUAPIVersion ?: 1
            if (kernelApi > systemStatus.managerUAPIVersion) {
                WarningCardMiuix(stringResource(R.string.require_manager_version))
            } else {
                WarningCardMiuix(
                    stringResource(
                        if (systemStatus.lkmMode == true) {
                            R.string.require_kernel_version
                        } else {
                            R.string.require_kernel_version_gki
                        },
                    ),
                )
            }
        }
        if (BuildConfig.DEBUG) {
            WarningCardMiuix(stringResource(R.string.debug_version_notice))
        }
        if (BuildConfig.IS_PR_BUILD || systemStatus.isPrBuild) {
            WarningCardMiuix(stringResource(R.string.home_pr_build_warning))
        }
        if (systemStatus.kernelPatchImplementation == KernelPatchImplementation.OFFICIAL) {
            WarningCardMiuix(stringResource(R.string.conflict_with_apatch))
        }
        if (systemStatus.ksuVersion != null && !systemStatus.isRootAvailable) {
            WarningCardMiuix(stringResource(R.string.grant_root_failed))
        }
    }
}

@Composable
private fun WarningCardMiuix(message: String) {
    val colorScheme = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = message,
            startAction = {
                Icon(
                    Icons.TwoTone.Error,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                    tint = colorScheme.error,
                )
            },
        )
    }
}

@Composable
private fun HomeStatusCardMiuix(
    uiState: HomeUiState,
) {
    val colorScheme = MiuixTheme.colorScheme
    val systemStatus = uiState.systemStatus
    when {
        Stealth.looksStealthy() -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.stealth_home_inactive),
                    summary = stringResource(R.string.stealth_home_inactive_hint),
                    startAction = {
                        Icon(
                            Icons.TwoTone.Block,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(20.dp),
                        )
                    },
                )
            }
        }

        systemStatus.ksuVersion != null -> {
            val workingModeText = if (systemStatus.isSafeMode) {
                stringResource(R.string.safe_mode)
            } else {
                stringResource(R.string.home_working)
            }
            val workingModeSurfaceText = if (systemStatus.lkmMode == true) "LKM" else "Built-in"
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = workingModeText,
                    summary = stringResource(
                        R.string.home_short_info,
                        uiState.systemInfo.superuserCount,
                        uiState.systemInfo.moduleCount,
                    ),
                    startAction = {
                        Icon(
                            Icons.TwoTone.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(20.dp),
                            tint = colorScheme.primary,
                        )
                    },
                    endActions = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusTagMiuix(workingModeSurfaceText)
                            if (systemStatus.isLateLoadMode) {
                                StatusTagMiuix(stringResource(R.string.jailbreak_mode))
                            }
                        }
                    },
                )
            }
        }

        systemStatus.kernelVersion.isGKI() -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.home_temp_root_inactive),
                    summary = stringResource(R.string.home_temp_root_inactive_hint),
                    startAction = {
                        Icon(
                            Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(20.dp),
                            tint = colorScheme.error,
                        )
                    },
                )
            }
        }

        else -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.home_unsupported),
                    summary = stringResource(R.string.home_unsupported_reason),
                    startAction = {
                        Icon(
                            Icons.TwoTone.Block,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(20.dp),
                            tint = colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusTagMiuix(text: String) {
    val colorScheme = MiuixTheme.colorScheme
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = colorScheme.onPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ManagerUpdateCardMiuix(
    update: ManagerUpdateInfo?,
    onRequestUpdate: (ManagerUpdateInfo) -> Unit,
) {
    if (update == null) return
    val channelTitle = stringResource(
        if (update.channel == ManagerUpdateChannel.STABLE) {
            R.string.manager_update_stable
        } else {
            R.string.manager_update_beta
        },
    )
    val message = stringResource(
        if (update.channel == ManagerUpdateChannel.STABLE) {
            R.string.new_version_available
        } else {
            R.string.beta_version_available
        },
        update.versionCode,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = channelTitle,
            summary = message,
            startAction = {
                Icon(
                    Icons.TwoTone.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                )
            },
            endActions = {
                TextButton(
                    text = stringResource(R.string.module_update),
                    onClick = { onRequestUpdate(update) },
                )
            },
        )
    }
}

@Composable
private fun HomeInfoCardMiuix(uiState: HomeUiState) {
    val systemStatus = uiState.systemStatus
    val systemInfo = uiState.systemInfo
    val isSimpleMode = uiState.isSimpleMode

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRowMiuix(
                    icon = Icons.TwoTone.Smartphone,
                    title = stringResource(R.string.home_device_model),
                    content = systemInfo.deviceModel,
                )
                InfoRowMiuix(
                    icon = Icons.TwoTone.DeveloperBoard,
                    title = stringResource(R.string.home_kernel),
                    content = systemInfo.kernelRelease,
                )
                if (!isSimpleMode) {
                    InfoRowMiuix(
                        icon = Icons.TwoTone.Android,
                        title = stringResource(R.string.home_android_version),
                        content = systemInfo.androidVersion,
                    )
                }
                if (systemStatus.isManager) {
                    InfoRowMiuix(
                        icon = Icons.TwoTone.Memory,
                        title = stringResource(R.string.home_kernel_version),
                        content = systemStatus.ksuFullVersion.orEmpty(),
                    )
                }
                InfoRowMiuix(
                    icon = Icons.TwoTone.Tag,
                    title = stringResource(R.string.home_manager_version),
                    content = "${systemInfo.managerVersion.first} (${systemInfo.managerVersion.second}/${systemInfo.managerVersion.third})",
                )
                val susfsIntegrationInfo = systemInfo.susfsIntegration
                if (!isSimpleMode && (
                        susfsIntegrationInfo?.loaded == true ||
                            susfsIntegrationInfo?.autoload == true ||
                            (systemInfo.susfsEnabled && systemInfo.susfsVersion.isNotEmpty())
                        )
                ) {
                    val backendLabel = when (susfsIntegrationInfo?.backend) {
                        "lkm" -> stringResource(R.string.susfs_backend_lkm)
                        "builtin" -> stringResource(R.string.susfs_backend_builtin)
                        else -> ""
                    }
                    val susfsLoaded = susfsIntegrationInfo?.loaded == true ||
                        (systemInfo.susfsEnabled && systemInfo.susfsVersion.isNotEmpty())
                    val content = when {
                        susfsLoaded && systemInfo.susfsVersion.isNotEmpty() ->
                            listOf(systemInfo.susfsVersion, backendLabel)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")

                        susfsIntegrationInfo?.autoload == true ->
                            stringResource(R.string.settings_susfs_integration_autoload_pending)

                        else -> systemInfo.susfsVersion
                    }
                    InfoRowMiuix(
                        icon = Icons.TwoTone.Tune,
                        title = stringResource(R.string.home_susfs_version),
                        content = content,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRowMiuix(
                    icon = Icons.TwoTone.Security,
                    title = stringResource(R.string.home_selinux_status),
                    content = systemInfo.selinuxStatus,
                )
                val seccompDisplay = when (systemInfo.seccompStatus) {
                    -1 -> stringResource(R.string.seccomp_status_not_supported)
                    0 -> stringResource(R.string.seccomp_status_disabled)
                    1 -> stringResource(R.string.seccomp_status_strict)
                    2 -> stringResource(R.string.seccomp_status_filter)
                    else -> stringResource(R.string.seccomp_status_unknown)
                }
                InfoRowMiuix(
                    icon = Icons.TwoTone.FilterList,
                    title = stringResource(R.string.home_seccomp_status),
                    content = seccompDisplay,
                )
                if (!isSimpleMode && systemStatus.isFullFeatured) {
                    InfoRowMiuix(
                        icon = Icons.TwoTone.Tune,
                        title = stringResource(R.string.home_hook_type),
                        content = systemStatus.hookType,
                    )
                }
                if (!isSimpleMode && systemInfo.zygiskImplement.isNotEmpty() && systemInfo.zygiskImplement != "None") {
                    InfoRowMiuix(
                        icon = Icons.TwoTone.Extension,
                        title = stringResource(R.string.home_zygisk_implement),
                        content = systemInfo.zygiskImplement,
                    )
                }
                if (!isSimpleMode && systemInfo.metaModuleImplement.isNotEmpty() && systemInfo.metaModuleImplement != "None") {
                    InfoRowMiuix(
                        icon = Icons.TwoTone.Extension,
                        title = stringResource(R.string.home_meta_module_implement),
                        content = systemInfo.metaModuleImplement,
                    )
                }
                val managersText = systemInfo.managersList?.managers
                    ?.groupBy { it.signatureIndex }
                    ?.toSortedMap()
                    ?.entries
                    ?.joinToString(" | ") { (_, managers) ->
                        managers.joinToString(", ") { "UID: ${it.uid}" }
                    }
                    .orEmpty()
                if (!isSimpleMode && managersText.isNotEmpty()) {
                    InfoRowMiuix(
                        icon = Icons.TwoTone.Group,
                        title = stringResource(R.string.multi_manager_list),
                        content = managersText,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRowMiuix(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String,
) {
    val colorScheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = title,
            modifier = Modifier.padding(end = 10.dp).size(22.dp),
            tint = colorScheme.onSurface,
        )
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = content,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun HomeLinksCardMiuix() {
    val uriHandler = LocalUriHandler.current
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.home_learn_kernelsu),
            summary = stringResource(R.string.home_click_to_learn_kernelsu),
            startAction = {
                Icon(
                    Icons.AutoMirrored.TwoTone.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                )
            },
            onClick = { uriHandler.openUri(learnMoreUrl) },
        )
        BasicComponent(
            title = stringResource(R.string.home_support_title),
            summary = stringResource(R.string.home_support_content),
            startAction = {
                Icon(
                    Icons.TwoTone.VolunteerActivism,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                )
            },
            onClick = { uriHandler.openUri("https://patreon.com/weishu") },
        )
    }
}
