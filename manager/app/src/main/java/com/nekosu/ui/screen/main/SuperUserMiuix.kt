package com.nekosu.ui.screen.main

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nekosu.R
import com.nekosu.domain.model.AllowlistOperationResult
import com.nekosu.domain.model.InstalledAppGroup
import com.nekosu.ui.component.PackageIcon
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.navigation.Route
import com.nekosu.ui.util.LocalPagerPage
import com.nekosu.ui.util.LocalPagerState
import com.nekosu.ui.viewmodel.SortType
import com.nekosu.ui.viewmodel.SuperUserUiAction
import com.nekosu.ui.viewmodel.SuperUserUiEvent
import com.nekosu.ui.viewmodel.SuperUserViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 超级用户页的 Miuix 风格实现。复用 [SuperUserViewModel] 的状态与 Action
 * （搜索、排序、显示系统应用、备份/恢复 allowlist、刷新），只把视觉组件换成 Miuix。
 *
 * Material 版保持不变。
 */
@Composable
fun SuperUserPageMiuix(bottomPadding: Dp) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<SuperUserViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerPage = LocalPagerPage.current
    val isPageVisible = pagerPage == null || LocalPagerState.current.currentPage == pagerPage
    val scope = rememberCoroutineScope()
    val colorScheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val searchState = rememberTextFieldState(uiState.search)

    var showMenu by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.dispatch(SuperUserUiAction.Search(""))
    }
    LaunchedEffect(viewModel, isPageVisible) {
        if (isPageVisible) viewModel.dispatch(SuperUserUiAction.LoadInitialData)
    }
    LaunchedEffect(searchState.text) {
        viewModel.dispatch(SuperUserUiAction.Search(searchState.text.toString()))
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SuperUserUiEvent.AllowlistOperationFinished -> {
                    val successMessage = if (event.restore) {
                        R.string.allowlist_restore_success
                    } else {
                        R.string.allowlist_backup_success
                    }
                    val failureMessage = if (event.restore) {
                        R.string.allowlist_restore_failed
                    } else {
                        R.string.allowlist_backup_failed
                    }
                    android.widget.Toast.makeText(
                        context,
                        context.allowlistOperationMessage(
                            result = event.result,
                            successMessage = successMessage,
                            failureMessage = failureMessage,
                        ),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.dispatch(SuperUserUiAction.BackupAllowlist(uri.toString()))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri.toString()
            showRestoreConfirm = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.superuser),
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.push(Route.Sulog) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.Article,
                            contentDescription = stringResource(R.string.sulog),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(R.string.settings),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        popupHost = {},
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp,
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

            when {
                uiState.appGroupList.isEmpty() && uiState.isRefreshing -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator()
                        }
                    }
                }

                uiState.appGroupList.isEmpty() -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = stringResource(
                                    uiState.loadFailure?.resId ?: R.string.no_apps_found,
                                ),
                                summary = if (uiState.search.isNotEmpty()) {
                                    stringResource(R.string.search_no_any_match)
                                } else {
                                    stringResource(R.string.network_retry)
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.network_retry),
                            onClick = {
                                viewModel.dispatch(SuperUserUiAction.Refresh)
                            },
                        )
                    }
                }

                else -> {
                    items(
                        items = uiState.appGroupList,
                        key = { appGroup -> "${appGroup.uid}-${appGroup.profileKey}" },
                        contentType = { appGroup -> "${appGroup.uid}-${appGroup.profileKey}" },
                    ) { appGroup ->
                        AppGroupCardMiuix(
                            appGroup = appGroup,
                            isManager = appGroup.uid in uiState.managerUids,
                            onClick = {
                                navigator.push(
                                    Route.AppProfile(appGroup.uid, appGroup.profileKey),
                                )
                            },
                        )
                    }
                    uiState.loadFailure?.let { failure ->
                        item {
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(failure.resId),
                                onClick = {
                                    viewModel.dispatch(SuperUserUiAction.Refresh)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMenu) {
        WindowDialog(
            show = showMenu,
            title = stringResource(R.string.settings),
            onDismissRequest = { showMenu = false },
            content = {
                Column {
                    Text(
                        text = stringResource(R.string.superuser),
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    SortType.entries.forEach { sortType ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = (if (uiState.currentSortType == sortType) "✓ " else "  ") +
                                stringResource(sortType.displayNameRes),
                            onClick = {
                                viewModel.dispatch(SuperUserUiAction.SetSort(sortType))
                                showMenu = false
                            },
                        )
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = (if (uiState.reverseOrder) "✓ " else "  ") +
                            stringResource(R.string.reverse_order),
                        onClick = {
                            viewModel.dispatch(
                                SuperUserUiAction.SetReverseOrder(!uiState.reverseOrder),
                            )
                            showMenu = false
                        },
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = (if (uiState.showSystemApps) "✓ " else "  ") +
                            stringResource(R.string.show_system_apps),
                        onClick = {
                            viewModel.dispatch(
                                SuperUserUiAction.SetShowSystemApps(!uiState.showSystemApps),
                            )
                            showMenu = false
                        },
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.backup_allowlist),
                        onClick = {
                            showMenu = false
                            backupLauncher.launch(createAllowlistBackupFileNameMiuix())
                        },
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.restore_allowlist),
                        onClick = {
                            showMenu = false
                            restoreLauncher.launch(arrayOf("application/octet-stream"))
                        },
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.network_retry),
                        onClick = {
                            showMenu = false
                            viewModel.dispatch(SuperUserUiAction.Refresh)
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    }

    if (showRestoreConfirm) {
        WindowDialog(
            show = showRestoreConfirm,
            title = stringResource(R.string.allowlist_restore_confirm_title),
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestoreUri = null
            },
            content = {
                Column {
                    Text(text = stringResource(R.string.allowlist_restore_confirm_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.cancel),
                            onClick = {
                                showRestoreConfirm = false
                                pendingRestoreUri = null
                            },
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.confirm),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                val uri = pendingRestoreUri
                                showRestoreConfirm = false
                                pendingRestoreUri = null
                                if (uri != null) {
                                    scope.launch {
                                        viewModel.dispatch(
                                            SuperUserUiAction.RestoreAllowlist(uri),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun AppGroupCardMiuix(
    appGroup: InstalledAppGroup,
    isManager: Boolean,
    onClick: () -> Unit,
) {
    val mainApp = appGroup.mainApp
    val colorScheme = MiuixTheme.colorScheme
    val groupCountText = stringResource(R.string.group_contains_apps, appGroup.apps.size)
    val recentlyInstalledText = stringResource(R.string.recently_installed)
    val tags = buildList {
        if (appGroup.apps.size > 1) add(groupCountText)
        if (appGroup.allowSu) add("ROOT")
        if (appGroup.shouldUmount) add("UMOUNT")
        if (appGroup.hasCustomProfile) add("CUSTOM")
        if (isManager) add("MANAGER")
        if (appGroup.isRecentlyInstalled) add(recentlyInstalledText)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = mainApp.label,
            summary = (if (appGroup.apps.size > 1) {
                tags
            } else {
                listOf(mainApp.displayIdentifier.toString()) + tags
            }).joinToString(" · "),
            startAction = {
                PackageIcon(
                    packageName = if (appGroup.isWebViewZygote) "android" else mainApp.packageName,
                    contentDescription = mainApp.label,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(40.dp),
                )
            },
            endActions = {
                Icon(
                    imageVector = Icons.TwoTone.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariantSummary,
                )
            },
            onClick = onClick,
        )
    }
}

private fun createAllowlistBackupFileNameMiuix(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "ksu_allowlist_backup_$timestamp.dat"
}

private fun Context.allowlistOperationMessage(
    result: AllowlistOperationResult,
    successMessage: Int,
    failureMessage: Int,
): String = when (result) {
    AllowlistOperationResult.Success ->
        getString(successMessage)

    AllowlistOperationResult.InvalidFile ->
        getString(failureMessage, getString(R.string.unknown_file))

    AllowlistOperationResult.UnsupportedVersion ->
        getString(failureMessage, getString(R.string.home_unsupported))

    is AllowlistOperationResult.ProfileUpdateFailed ->
        getString(
            failureMessage,
            getString(R.string.failed_to_update_app_profile, result.uid.toString()),
        )

    is AllowlistOperationResult.Failed ->
        getString(
            failureMessage,
            result.cause?.localizedMessage ?: getString(R.string.unknown),
        )
}
