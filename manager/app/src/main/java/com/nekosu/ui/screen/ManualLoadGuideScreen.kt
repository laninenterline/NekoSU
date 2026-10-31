package com.nekosu.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nekosu.R
import com.nekosu.magica.MagicaFailureLog
import com.nekosu.ui.component.SwipeableSnackbarHost
import com.nekosu.ui.component.settings.AppBackButton
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.util.LocalSnackbarHost
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import com.nekosu.ui.util.showReplacingSnackbar
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class GuideStep(val title: String, val body: String, val command: String? = null)

private fun copy(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManualLoadGuideScreen() {
    val context = LocalContext.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val copied = stringResource(R.string.guide_copied)

    val packageName = context.packageName
    val steps = listOf(
        GuideStep(
            stringResource(R.string.guide_step1_title),
            stringResource(R.string.guide_step1_body),
            "adb pair 192.168.x.x:PAIR_PORT",
        ),
        GuideStep(
            stringResource(R.string.guide_step2_title),
            stringResource(R.string.guide_step2_body),
            "adb connect 192.168.x.x:DEBUG_PORT",
        ),
        GuideStep(
            stringResource(R.string.guide_step3_title),
            stringResource(R.string.guide_step3_body),
            "adb root",
        ),
        GuideStep(
            stringResource(R.string.guide_step4_title),
            stringResource(R.string.guide_step4_body),
            "adb push ksud /data/local/tmp/ksud && adb shell \"chmod 755 /data/local/tmp/ksud && /data/local/tmp/ksud late-load --allow-shell --package-name $packageName\"",
        ),
    )

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
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
                Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyMedium)
            }
            items(steps.size) { index ->
                val step = steps[index]
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.titleSmall)
                        Text(step.body, style = MaterialTheme.typography.bodyMedium)
                        step.command?.let { command ->
                            SelectionContainer {
                                Text(
                                    text = command,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            TextButton(onClick = {
                                copy(context, "NekoSU guide", command)
                                scope.launch { snackBarHost.showReplacingSnackbar(copied) }
                            }) {
                                Text(stringResource(R.string.guide_copy))
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.guide_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
