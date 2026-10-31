package com.nekosu.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nekosu.R
import com.nekosu.ui.component.settings.AppBackButton
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.navigation.Route
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import org.koin.compose.koinInject

private data class SearchEntry(val title: String, val description: String, val route: Route)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSearchScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var query by remember { mutableStateOf("") }

    val entries = listOf(
        SearchEntry(stringResource(R.string.block_modules_title), stringResource(R.string.block_modules_summary), Route.BlockModules),
        SearchEntry(stringResource(R.string.kmi_diag_title), stringResource(R.string.kmi_diag_summary), Route.KmiDiagnostics),
        SearchEntry(stringResource(R.string.magica_log_title), stringResource(R.string.magica_log_summary), Route.MagicaLog),
        SearchEntry(stringResource(R.string.guide_title), stringResource(R.string.guide_summary), Route.ManualLoadGuide),
        SearchEntry(stringResource(R.string.susfs_presets_title), stringResource(R.string.susfs_presets_summary), Route.SusfsPresets),
        SearchEntry(stringResource(R.string.multi_manager_title), stringResource(R.string.multi_manager_summary), Route.MultiManager),
        SearchEntry(stringResource(R.string.app_icon_title), stringResource(R.string.app_icon_summary), Route.AppIcon),
        SearchEntry(stringResource(R.string.theme_settings), stringResource(R.string.theme_settings), Route.ThemeSettings),
        SearchEntry(stringResource(R.string.dynamic_manager_title), stringResource(R.string.dynamic_manager_settings_summary), Route.DynamicManager),
        SearchEntry(stringResource(R.string.umount_path_manager), stringResource(R.string.umount_path_manager_summary), Route.UmountManager),
    )
    val filtered = if (query.isBlank()) {
        entries
    } else {
        entries.filter {
            it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_search_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                    singleLine = true,
                )
            }
            if (filtered.isEmpty()) {
                item { Text(stringResource(R.string.settings_search_empty), style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(filtered, key = { it.title }) { entry ->
                    androidx.compose.material3.OutlinedCard(
                        onClick = { navigator.push(entry.route) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
