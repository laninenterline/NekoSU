package com.nekosu.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Article
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nekosu.R
import com.nekosu.data.logging.LogSource
import com.nekosu.ui.component.SearchAppBar
import com.nekosu.ui.component.SwipeableSnackbarHost
import com.nekosu.ui.component.rememberSearchAppBarScrollBehavior
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.util.LocalSnackbarHost
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import com.nekosu.ui.util.showReplacingSnackbar
import com.nekosu.ui.viewmodel.LogCenterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCenterScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = koinViewModel<LogCenterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()),
    )

    val clearedMsg = stringResource(R.string.log_center_cleared)
    val clearFailedMsg = stringResource(R.string.log_center_clear_failed)
    val exportSuccessMsg = stringResource(R.string.log_center_export_success)
    val exportFailedMsg = stringResource(R.string.log_center_export_failed)
    val emptyMsg = stringResource(R.string.log_center_empty)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(state.content.toByteArray())
                }
            }.isSuccess
            withContext(Dispatchers.Main) {
                snackbarHost.showReplacingSnackbar(if (ok) exportSuccessMsg else exportFailedMsg)
            }
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.log_center_title),
                searchText = query,
                onSearchTextChange = { query = it },
                onBackClick = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.log_center_search_hint),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbarHost) },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        val lines = state.content.lines()
        val filtered = if (query.isBlank()) lines else lines.filter { it.contains(query, ignoreCase = true) }
        val filters = logSourceFilters()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.forEach { (source, label) ->
                        FilterChip(
                            selected = state.source == source,
                            onClick = { viewModel.select(source) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.isLoading) {
                            stringResource(R.string.log_center_loading)
                        } else {
                            stringResource(R.string.log_center_refresh)
                        },
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.log_center_refresh))
                    }
                    IconButton(onClick = {
                        viewModel.clear { ok ->
                            scope.launch {
                                snackbarHost.showReplacingSnackbar(if (ok) clearedMsg else clearFailedMsg)
                            }
                        }
                    }) {
                        Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.log_center_clear))
                    }
                    IconButton(onClick = { exportLauncher.launch("NekoSU_logs.txt") }) {
                        Icon(Icons.TwoTone.Save, contentDescription = stringResource(R.string.log_center_export))
                    }
                }
            }
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            text = filtered.joinToString("\n").ifBlank { emptyMsg },
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun logSourceFilters(): List<Pair<LogSource, String>> = listOf(
    LogSource.ALL to stringResource(R.string.log_center_all),
    LogSource.SULOG to stringResource(R.string.log_center_sulog),
    LogSource.MAGICA to stringResource(R.string.log_center_magica),
    LogSource.CRASH to stringResource(R.string.log_center_crash),
    LogSource.METAMODULE to stringResource(R.string.log_center_metamodule),
    LogSource.PROBLEM to stringResource(R.string.log_center_problem),
)
