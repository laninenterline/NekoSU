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
import android.content.Intent
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Share
import androidx.core.content.FileProvider
import com.nekosu.BuildConfig
import com.nekosu.domain.usecase.GenerateBugreportUseCase
import org.koin.compose.koinInject
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

    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    val bugreportSavedMsg = stringResource(R.string.log_center_bugreport_saved)
    val bugreportFailedMsg = stringResource(R.string.log_center_bugreport_failed)
    val shareBugreportTitle = stringResource(R.string.log_center_bugreport_share)

    val bugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                generateBugreport().inputStream().use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }.isSuccess
            withContext(Dispatchers.Main) {
                snackbarHost.showReplacingSnackbar(if (ok) bugreportSavedMsg else bugreportFailedMsg)
            }
        }
    }

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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            com.nekosu.shared.SharedLogContent(
                model = com.nekosu.shared.SharedLogModel(
                    sources = filters.map { it.first.name to it.second },
                    selectedSource = state.source.name,
                    query = query,
                    text = if (query.isBlank()) {
                        state.content
                    } else {
                        state.content.lines().filter { it.contains(query, ignoreCase = true) }.joinToString("\n")
                    },
                    loading = state.isLoading,
                ),
                onSelectSource = { name ->
                    runCatching { LogSource.valueOf(name) }.getOrNull()?.let { viewModel.select(it) }
                },
                onQueryChange = { query = it },
                onRefresh = { viewModel.refresh() },
                onExport = { exportLauncher.launch("NekoSU_logs.txt") },
                actions = {
                    IconButton(onClick = {
                        viewModel.clear { ok ->
                            scope.launch {
                                snackbarHost.showReplacingSnackbar(if (ok) clearedMsg else clearFailedMsg)
                            }
                        }
                    }) {
                        Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.log_center_clear))
                    }
                    IconButton(onClick = {
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                        val current = java.time.LocalDateTime.now().format(formatter)
                        bugreportLauncher.launch("KernelSU_bugreport_$current.tar.gz")
                    }) {
                        Icon(Icons.TwoTone.BugReport, contentDescription = stringResource(R.string.log_center_bugreport_save))
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val ok = runCatching {
                                val bugreport = generateBugreport()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    bugreport,
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    setDataAndType(uri, "application/gzip")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                withContext(Dispatchers.Main) {
                                    context.startActivity(Intent.createChooser(shareIntent, shareBugreportTitle))
                                }
                                true
                            }.isSuccess
                            if (!ok) {
                                withContext(Dispatchers.Main) {
                                    snackbarHost.showReplacingSnackbar(bugreportFailedMsg)
                                }
                            }
                        }
                    }) {
                        Icon(Icons.TwoTone.Share, contentDescription = shareBugreportTitle)
                    }
                },
            )
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
