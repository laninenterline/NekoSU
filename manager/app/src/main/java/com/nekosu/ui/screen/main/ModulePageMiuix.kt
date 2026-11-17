package com.nekosu.ui.screen.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import com.nekosu.R
import com.nekosu.domain.model.InstalledModule
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.navigation.Route
import com.nekosu.ui.viewmodel.HomeViewModel
import com.nekosu.ui.viewmodel.ModuleUiAction
import com.nekosu.ui.viewmodel.ModuleUiEvent
import com.nekosu.ui.viewmodel.ModuleViewModel
import com.nekosu.ui.webui.WebUIActivity
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 模块列表页的 Miuix 风格实现。复用 [ModuleViewModel] 的状态与 Action
 * （搜索/排序/刷新/启停/卸载/执行 Action/打开 WebUI/LoadSize），视觉组件换成 Miuix。
 *
 * 说明：Material 版的「模块更新（仓库更新 + 校验）」「本地 ZIP 安装确认」
 * 与「长按创建快捷方式」尚未在 Miuix 版实现；FAB 打开仓库页用于安装模块。
 * Material 版保持原样。
 */
@Composable
fun ModulePageMiuix(bottomPadding: Dp) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<ModuleViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val homeState by koinViewModel<HomeViewModel>().uiState.collectAsStateWithLifecycle()
    val colorScheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val searchState = rememberTextFieldState(uiState.search)

    var showSortMenu by remember { mutableStateOf(false) }
    var pendingUninstall by remember { mutableStateOf<InstalledModule?>(null) }

    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        navigator.push(Route.Flash.modules(listOf(uri.toString())))
        viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
    }

    LaunchedEffect(Unit) {
        viewModel.dispatch(ModuleUiAction.Search(""))
        if (uiState.moduleList.isEmpty() || uiState.isNeedRefresh) {
            viewModel.dispatch(ModuleUiAction.Refresh())
        }
    }
    LaunchedEffect(searchState.text) {
        viewModel.dispatch(ModuleUiAction.Search(searchState.text.toString()))
    }
    LaunchedEffect(viewModel, uiState.moduleList) {
        viewModel.events.collectLatest { event ->
            val moduleName = { moduleId: String ->
                uiState.moduleList.find { it.dirId == moduleId }?.name ?: moduleId
            }
            when (event) {
                is ModuleUiEvent.EnabledChanged -> {
                    val name = moduleName(event.moduleId)
                    val message = if (event.successful) {
                        context.getString(R.string.reboot_to_apply)
                    } else if (event.enabled) {
                        context.getString(R.string.module_failed_to_enable).format(name)
                    } else {
                        context.getString(R.string.module_failed_to_disable).format(name)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }

                is ModuleUiEvent.RemovedChanged -> {
                    val name = moduleName(event.moduleId)
                    val message = if (event.removed) {
                        if (event.successful) {
                            context.getString(R.string.module_uninstall_success).format(name)
                        } else {
                            context.getString(R.string.module_uninstall_failed).format(name)
                        }
                    } else {
                        context.getString(R.string.module_uninstall_failed).format(name)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (event.successful) {
                        viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                        viewModel.dispatch(ModuleUiAction.Refresh())
                    }
                }

                is ModuleUiEvent.Error -> if (event.message.isNotBlank()) {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                ModuleUiEvent.RefreshCompleted -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.module),
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.push(Route.ModuleRepo) }) {
                        Icon(
                            imageVector = Icons.TwoTone.Cloud,
                            contentDescription = stringResource(R.string.module_repo),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(R.string.module),
                            tint = colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { viewModel.dispatch(ModuleUiAction.Refresh(manual = true)) }) {
                        Icon(
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = stringResource(R.string.log_center_refresh),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!homeState.systemStatus.isSafeMode && !uiState.hasMagisk) {
                FloatingActionButton(
                    onClick = { installLauncher.launch(arrayOf("application/zip")) },
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.PlayArrow,
                        contentDescription = stringResource(R.string.module),
                    )
                }
            }
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
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextField(
                    state = searchState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (uiState.hasMagisk) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = stringResource(R.string.module_magisk_conflict),
                            startAction = {
                                Icon(
                                    imageVector = Icons.TwoTone.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.error,
                                )
                            },
                        )
                    }
                }
            } else if (uiState.moduleList.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = if (uiState.search.isNotEmpty()) {
                                stringResource(R.string.search_no_any_match)
                            } else {
                                stringResource(R.string.module_empty)
                            },
                        )
                    }
                }
            } else {
                items(
                    items = uiState.moduleList,
                    key = { module -> "module-${module.id}" },
                ) { module ->
                    ModuleCardMiuix(
                        module = module,
                        sizeText = uiState.moduleSizes[module.dirId],
                        showMoreModuleInfo = uiState.showMoreModuleInfo,
                        onToggleEnabled = { enabled ->
                            viewModel.dispatch(ModuleUiAction.SetEnabled(module.dirId, enabled))
                        },
                        onAction = {
                            navigator.push(Route.ExecuteModuleAction(module.dirId, false))
                            viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                        },
                        onWebUi = {
                            context.startActivity(
                                Intent(context, WebUIActivity::class.java)
                                    .setData(
                                        Uri.Builder()
                                            .scheme("kernelsu")
                                            .authority("webui")
                                            .appendQueryParameter("id", module.dirId)
                                            .build(),
                                    ),
                            )
                        },
                        onUninstall = { pendingUninstall = module },
                        onRestore = {
                            viewModel.dispatch(ModuleUiAction.SetRemoved(module.dirId, false))
                        },
                    )
                }
            }
        }
    }

    if (showSortMenu) {
        WindowDialog(
            show = showSortMenu,
            title = stringResource(R.string.module),
            onDismissRequest = { showSortMenu = false },
            content = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = (if (uiState.sortActionFirst) "✓ " else "  ") +
                            stringResource(R.string.module_sort_action_first),
                        onClick = {
                            viewModel.dispatch(
                                ModuleUiAction.Sort(
                                    uiState.sortEnabledFirst,
                                    !uiState.sortActionFirst,
                                ),
                            )
                            showSortMenu = false
                        },
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = (if (uiState.sortEnabledFirst) "✓ " else "  ") +
                            stringResource(R.string.module_sort_enabled_first),
                        onClick = {
                            viewModel.dispatch(
                                ModuleUiAction.Sort(
                                    !uiState.sortEnabledFirst,
                                    uiState.sortActionFirst,
                                ),
                            )
                            showSortMenu = false
                        },
                    )
                }
            },
        )
    }

    pendingUninstall?.let { module ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.module),
            onDismissRequest = { pendingUninstall = null },
            content = {
                Column {
                    Text(
                        text = stringResource(
                            if (module.metamodule) {
                                R.string.metamodule_uninstall_confirm
                            } else {
                                R.string.module_uninstall_confirm
                            },
                        ).format(module.name),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.cancel),
                            onClick = { pendingUninstall = null },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.uninstall),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                val target = pendingUninstall
                                pendingUninstall = null
                                if (target != null) {
                                    viewModel.dispatch(
                                        ModuleUiAction.SetRemoved(target.dirId, true),
                                    )
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
private fun ModuleCardMiuix(
    module: InstalledModule,
    sizeText: String?,
    showMoreModuleInfo: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onAction: () -> Unit,
    onWebUi: () -> Unit,
    onUninstall: () -> Unit,
    onRestore: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    var isEnabled by remember(module.dirId, module.enabled) { mutableStateOf(module.enabled) }
    val textDecoration = if (module.remove) TextDecoration.LineThrough else null
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            BasicComponent(
                title = module.name,
                summary = "Version: ${module.version} · ${module.author}",
                endActions = {
                    Switch(
                        checked = isEnabled,
                        enabled = !module.update,
                        onCheckedChange = { enabled ->
                            isEnabled = enabled
                            onToggleEnabled(enabled)
                        },
                    )
                },
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = module.description,
                    fontSize = 13.sp,
                    maxLines = if (showMoreModuleInfo) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = textDecoration,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                if (module.update && module.moduleUpdate != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.module_update),
                        fontSize = 12.sp,
                        color = colorScheme.primary,
                    )
                }
                if (showMoreModuleInfo && module.updateJson.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = module.updateJson,
                        fontSize = 11.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(
                        module.dirId,
                        sizeText,
                        "META".takeIf { module.metamodule },
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (module.hasActionScript) {
                        TextButton(
                            text = "Action",
                            onClick = onAction,
                        )
                    }
                    if (module.hasWebUi) {
                        TextButton(
                            text = "WebUI",
                            onClick = onWebUi,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        text = stringResource(
                            if (module.remove) R.string.restore else R.string.uninstall,
                        ),
                        colors = ButtonDefaults.textButtonColors(),
                        onClick = if (module.remove) onRestore else onUninstall,
                    )
                }
            }
        }
    }
}
