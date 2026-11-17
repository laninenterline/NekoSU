package com.nekosu.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nekosu.R
import com.nekosu.data.logging.LogSource
import com.nekosu.domain.usecase.GenerateBugreportUseCase
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.viewmodel.LogCenterViewModel
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 日志中心的 Miuix 风格实现。复用 [LogCenterViewModel] 的状态与操作
 * （来源过滤、刷新、清空、导出、Bugreport 保存/分享、搜索），只替换视觉组件。
 * Material 版保持不变。
 */
@Composable
fun LogCenterScreenMiuix() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = koinViewModel<LogCenterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val searchState = rememberTextFieldState()
    val query = searchState.text.toString()

    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    val bugreportSavedMsg = stringResource(R.string.log_center_bugreport_saved)
    val bugreportFailedMsg = stringResource(R.string.log_center_bugreport_failed)
    val shareBugreportTitle = stringResource(R.string.log_center_bugreport_share)

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

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
                toast(if (ok) bugreportSavedMsg else bugreportFailedMsg)
            }
        }
    }

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
                toast(
                    context.getString(
                        if (ok) R.string.log_center_export_success else R.string.log_center_export_failed,
                    ),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.log_center_title),
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = stringResource(R.string.log_center_title),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        popupHost = {},
        containerColor = Color.Transparent,
    ) { innerPadding ->
        val lines = state.content.lines()
        val filtered = if (query.isBlank()) {
            lines
        } else {
            lines.filter { it.contains(query, ignoreCase = true) }
        }
        val filters = logSourceFiltersMiuix()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextField(
                    state = searchState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filters.forEach { (source, label) ->
                            TextButton(
                                text = label,
                                colors = if (state.source == source) {
                                    ButtonDefaults.textButtonColorsPrimary()
                                } else {
                                    ButtonDefaults.textButtonColors()
                                },
                                onClick = { viewModel.select(source) },
                            )
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.log_center_refresh),
                        onClick = { viewModel.refresh() },
                    )
                    BasicComponent(
                        title = stringResource(R.string.log_center_clear),
                        onClick = {
                            viewModel.clear { ok ->
                                scope.launch {
                                    toast(
                                        context.getString(
                                            if (ok) R.string.log_center_cleared else R.string.log_center_clear_failed,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                    BasicComponent(
                        title = stringResource(R.string.log_center_bugreport_save),
                        onClick = {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                            val current = LocalDateTime.now().format(formatter)
                            bugreportLauncher.launch("KernelSU_bugreport_$current.tar.gz")
                        },
                    )
                    BasicComponent(
                        title = stringResource(R.string.log_center_bugreport_share),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val ok = runCatching {
                                    val bugreport = generateBugreport()
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        bugreport,
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        setDataAndType(uri, "application/gzip")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, shareBugreportTitle),
                                        )
                                    }
                                    true
                                }.isSuccess
                                if (!ok) {
                                    withContext(Dispatchers.Main) {
                                        toast(bugreportFailedMsg)
                                    }
                                }
                            }
                        },
                    )
                    BasicComponent(
                        title = stringResource(R.string.log_center_export),
                        onClick = { exportLauncher.launch("NekoSU_logs.txt") },
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            text = filtered.joinToString("\n").ifBlank {
                                stringResource(R.string.log_center_empty)
                            },
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
private fun logSourceFiltersMiuix(): List<Pair<LogSource, String>> = listOf(
    LogSource.ALL to stringResource(R.string.log_center_all),
    LogSource.SULOG to stringResource(R.string.log_center_sulog),
    LogSource.CRASH to stringResource(R.string.log_center_crash),
    LogSource.METAMODULE to stringResource(R.string.log_center_metamodule),
    LogSource.PROBLEM to stringResource(R.string.log_center_problem),
)
