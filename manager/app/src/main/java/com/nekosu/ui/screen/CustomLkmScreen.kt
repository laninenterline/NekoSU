package com.nekosu.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.nekosu.R
import com.nekosu.ui.component.SwipeableSnackbarHost
import com.nekosu.ui.component.settings.AppBackButton
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.util.LocalSnackbarHost
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import com.nekosu.ui.util.showReplacingSnackbar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private fun customDir(context: Context): File = File(context.filesDir, "lkm/custom")
private fun customKo(context: Context): File = File(customDir(context), "kernelsu.ko")
private fun customSusfs(context: Context): File = File(customDir(context), "susfs_guard_lkm.ko")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomLkmScreen() {
    val context = LocalContext.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var version by remember { mutableStateOf(0) }
    val imported = stringResource(R.string.custom_lkm_imported)
    val failed = stringResource(R.string.custom_lkm_failed)

    fun import(uri: Uri, target: () -> File) {
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val file = target()
                file.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } != null && file.length() > 64
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                version++
                snackBarHost.showReplacingSnackbar(context.getString(if (ok) R.string.custom_lkm_imported else R.string.custom_lkm_failed))
            }
        }
    }

    val pickKernel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { import(it) { customKo(context) } }
    }
    val pickSusfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { import(it) { customSusfs(context) } }
    }

    val ko = customKo(context)
    val susfs = customSusfs(context)

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.custom_lkm_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.custom_lkm_summary), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.custom_lkm_kernelsu), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (ko.isFile) stringResource(R.string.custom_lkm_present, ko.length() / 1024) else stringResource(R.string.custom_lkm_absent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickKernel.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.custom_lkm_pick))
                            }
                            if (ko.isFile) {
                                TextButton(onClick = { ko.delete(); version++ }) { Text(stringResource(R.string.custom_lkm_remove)) }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.custom_lkm_susfs), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (susfs.isFile) stringResource(R.string.custom_lkm_present, susfs.length() / 1024) else stringResource(R.string.custom_lkm_absent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickSusfs.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.custom_lkm_pick))
                            }
                            if (susfs.isFile) {
                                TextButton(onClick = { susfs.delete(); version++ }) { Text(stringResource(R.string.custom_lkm_remove)) }
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.custom_lkm_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
