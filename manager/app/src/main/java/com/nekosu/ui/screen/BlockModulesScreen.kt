package com.nekosu.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nekosu.R
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.ui.component.SwipeableSnackbarHost
import com.nekosu.ui.component.settings.AppBackButton
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.util.LocalSnackbarHost
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import com.nekosu.ui.util.showReplacingSnackbar
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val CONFIG_PATH = "/data/adb/ksu/block_modules"
private const val DEFAULT_BLOCK_MODULES = "vr,vklp,oplus_secure_guard,oplus_secure_guard_new,mkp"

private fun validBlockModules(value: String): Boolean {
    if (value.length > 255) return false
    if (value.isEmpty()) return true
    return value.split(',').all { name ->
        name.isNotEmpty() && name.all { c ->
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-'
        }
    }
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlockModulesScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val repository: KsuCliRepository = koinInject()
    val snackBarHost = LocalSnackbarHost.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.block_modules_saved)
    val invalidMessage = stringResource(R.string.block_modules_invalid)

    LaunchedEffect(Unit) {
        val current = withContext(Dispatchers.IO) {
            runCatching {
                ShellUtils.fastCmd(repository.getRootShell(), "cat $CONFIG_PATH 2>/dev/null")
            }.getOrDefault("").trim()
        }
        text = current
        loaded = true
    }

    fun save(value: String) {
        if (!validBlockModules(value)) {
            scope.launch { snackBarHost.showReplacingSnackbar(invalidMessage) }
            return
        }
        scope.launch(Dispatchers.IO) {
            val command = "mkdir -p /data/adb/ksu && printf %s ${shellQuote(value)} > $CONFIG_PATH " +
                "&& chmod 600 $CONFIG_PATH"
            val ok = ShellUtils.fastCmdResult(repository.getRootShell(), command)
            withContext(Dispatchers.Main) {
                snackBarHost.showReplacingSnackbar(if (ok) savedMessage else invalidMessage)
            }
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.block_modules_title)) },
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
                Text(stringResource(R.string.block_modules_description))
            }
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loaded,
                    minLines = 3,
                    label = { Text(stringResource(R.string.block_modules_list_label)) },
                    placeholder = { Text(DEFAULT_BLOCK_MODULES) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { save(text) }, enabled = loaded) {
                        Text(stringResource(R.string.block_modules_save))
                    }
                    TextButton(onClick = { text = DEFAULT_BLOCK_MODULES }, enabled = loaded) {
                        Text(stringResource(R.string.block_modules_fill_default))
                    }
                    TextButton(onClick = { text = "" }, enabled = loaded) {
                        Text(stringResource(R.string.block_modules_clear))
                    }
                }
            }
            item {
                Column {
                    Text(
                        text = stringResource(R.string.block_modules_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.block_modules_path_note, CONFIG_PATH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
