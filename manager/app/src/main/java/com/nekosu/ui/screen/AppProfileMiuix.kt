package com.nekosu.ui.screen

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.nekosu.R
import com.nekosu.domain.model.AppControlAction
import com.nekosu.domain.model.AppProfile
import com.nekosu.ui.component.PackageIcon
import com.nekosu.ui.component.settings.SegmentedColumn
import com.nekosu.ui.component.profile.AppProfileConfig
import com.nekosu.ui.component.profile.RootProfileConfig
import com.nekosu.ui.component.profile.TemplateConfig
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.navigation.Route
import com.nekosu.ui.util.ActivityResumeEffect
import com.nekosu.ui.viewmodel.AppProfileUiAction
import com.nekosu.ui.viewmodel.AppProfileUiEvent
import com.nekosu.ui.viewmodel.AppProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class AppProfileModeMiuix(@param:StringRes val res: Int) {
    Default(R.string.profile_default),
    Template(R.string.profile_template),
    Custom(R.string.profile_custom),
    ;
}

/**
 * App 详情页的 Miuix 风格实现：页面骨架、应用控制、Root 授权与受影响应用为 Miuix 组件；
 * 复杂的 Profile 编辑器复用已有的 RootProfileConfig / AppProfileConfig / TemplateConfig
 * （它们属于 Material 组件，在 Miuix 主题内仍使用派生配色）。
 * Material 版保持不变。
 */
@Composable
fun AppProfileScreenMiuix(
    uid: Int,
    packageName: String,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val viewModel = koinViewModel<AppProfileViewModel>(
        parameters = { parametersOf(uid, packageName) },
    )
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val appGroup = uiState.appGroup
    val profile = uiState.profile
    val appLabel = appGroup?.mainApp?.label ?: packageName
    val isSpecial = appGroup?.isWebViewZygote == true

    val failToUpdateAppProfile = stringResource(R.string.failed_to_update_app_profile).format(appLabel)
    val failToUpdateSepolicy = stringResource(R.string.failed_to_update_sepolicy).format(appLabel)
    val suNotAllowed = stringResource(R.string.su_not_allowed).format(appLabel)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AppProfileUiEvent.Error -> Toast.makeText(
                    context,
                    failToUpdateAppProfile,
                    Toast.LENGTH_SHORT,
                ).show()

                AppProfileUiEvent.SepolicyUpdateFailed -> Toast.makeText(
                    context,
                    failToUpdateSepolicy,
                    Toast.LENGTH_SHORT,
                ).show()

                AppProfileUiEvent.Saved -> Unit
            }
        }
    }

    ActivityResumeEffect(packageName, uid) {
        viewModel.dispatch(AppProfileUiAction.Load)
    }

    if (appGroup == null || profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator()
        }
        return
    }

    val onProfileChange: (AppProfile) -> Unit = { incoming ->
        scope.launch {
            if (incoming.allowSu && uid < 2000 && uid != 1000) {
                Toast.makeText(context, suNotAllowed, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.dispatch(AppProfileUiAction.Save(incoming))
            }
        }
    }

    var mode by rememberSaveable(profile.allowSu, profile.rootUseDefault, profile.rootTemplate) {
        mutableStateOf(
            when {
                profile.rootUseDefault -> AppProfileModeMiuix.Default
                profile.rootTemplate != null -> AppProfileModeMiuix.Template
                else -> AppProfileModeMiuix.Custom
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = appLabel,
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = appLabel,
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
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = appGroup.mainApp.label,
                        summary = appGroup.mainApp.displayIdentifier,
                        startAction = {
                            PackageIcon(
                                packageName = if (isSpecial) "android" else appGroup.mainApp.packageName,
                                contentDescription = appGroup.mainApp.label,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(48.dp),
                            )
                        },
                    )
                }
            }

            if (!isSpecial) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                text = stringResource(R.string.launch_app),
                                onClick = {
                                    viewModel.dispatch(
                                        AppProfileUiAction.ControlApp(AppControlAction.LAUNCH),
                                    )
                                },
                            )
                            TextButton(
                                text = stringResource(R.string.force_stop_app),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    viewModel.dispatch(
                                        AppProfileUiAction.ControlApp(AppControlAction.FORCE_STOP),
                                    )
                                },
                            )
                            TextButton(
                                text = stringResource(R.string.restart_app),
                                onClick = {
                                    viewModel.dispatch(
                                        AppProfileUiAction.ControlApp(AppControlAction.RESTART),
                                    )
                                },
                            )
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(R.string.superuser),
                            summary = stringResource(R.string.superuser),
                            checked = profile.allowSu,
                            onCheckedChange = { checked ->
                                onProfileChange(profile.copy(allowSu = checked))
                            },
                        )
                    }
                }

                if (profile.allowSu) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AppProfileModeMiuix.entries.forEach { entry ->
                                    TextButton(
                                        text = stringResource(entry.res),
                                        colors = if (mode == entry) {
                                            ButtonDefaults.textButtonColorsPrimary()
                                        } else {
                                            ButtonDefaults.textButtonColors()
                                        },
                                        onClick = {
                                            mode = entry
                                            when (entry) {
                                                AppProfileModeMiuix.Default -> onProfileChange(
                                                    profile.copy(
                                                        rootUseDefault = true,
                                                        rootTemplate = null,
                                                    ),
                                                )

                                                AppProfileModeMiuix.Template -> onProfileChange(
                                                    profile.copy(rootUseDefault = false),
                                                )

                                                AppProfileModeMiuix.Custom -> onProfileChange(
                                                    profile.copy(
                                                        rootUseDefault = false,
                                                        rootTemplate = null,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    when (mode) {
                        AppProfileModeMiuix.Default -> Unit
                        AppProfileModeMiuix.Template -> item {
                            SegmentedColumn {
                                item {
                                    TemplateConfig(
                                        profile = profile,
                                        onViewTemplate = { id ->
                                            navigator.push(Route.TemplateEditor(id, true))
                                        },
                                        onProfileChange = onProfileChange,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = stringResource(R.string.manage_app_profile),
                                    summary = stringResource(R.string.settings_profile_template_summary),
                                    onClick = { navigator.push(Route.AppProfileTemplate) },
                                )
                            }
                        }

                        AppProfileModeMiuix.Custom -> item {
                            RootProfileConfig(
                                profile = profile,
                                sepolicyValid = uiState.sepolicyValid,
                                onValidateSepolicy = {
                                    viewModel.dispatch(AppProfileUiAction.ValidateSepolicy(it))
                                },
                                onProfileChange = onProfileChange,
                            )
                        }
                    }
                }

                item {
                    SegmentedColumn {
                        item {
                            AppProfileConfig(
                                enabled = true,
                                profile = profile,
                                defaultUmountModules = uiState.defaultUmountModules,
                                onProfileChange = onProfileChange,
                            )
                        }
                    }
                }

            }

            if (appGroup.apps.size > 1) {
                item {
                    Text(
                        text = stringResource(R.string.affected_applications),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                items(appGroup.apps, key = { app -> app.packageName }) { app ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = app.label,
                            summary = app.packageName,
                            startAction = {
                                PackageIcon(
                                    packageName = app.packageName,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(40.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
