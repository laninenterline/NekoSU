package com.nekosu.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nekosu.ui.component.SwipeableSnackbarHost
import com.nekosu.ui.util.LocalSnackbarHost
import com.nekosu.ui.util.showReplacingSnackbar
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nekosu.R
import com.nekosu.ui.component.settings.AppBackButton
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.util.adaptiveScaffoldWindowInsets
import com.nekosu.ui.viewmodel.SettingsUiAction
import com.nekosu.ui.viewmodel.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    data class Option(val alt: Boolean, val label: Int, val preview: Int)

    val options = listOf(
        Option(false, R.string.app_icon_default, R.drawable.ic_launcher_foreground),
        Option(true, R.string.app_icon_alt, R.drawable.ic_launcher_foreground_alt),
    )

    val context = LocalContext.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val requested = stringResource(R.string.app_icon_custom_requested)
    val failed = stringResource(R.string.app_icon_custom_failed)
    val customPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val bitmap = decodeSquareIcon(context.contentResolver.openInputStream(uri))
                requestCustomIconShortcut(context, bitmap)
            }.isSuccess
            withContext(Dispatchers.Main) {
                snackBarHost.showReplacingSnackbar(if (ok) requested else failed)
            }
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_icon_title)) },
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
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
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
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.app_icon_custom), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.app_icon_custom_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { customPicker.launch("image/*") },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.app_icon_custom_pick))
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.app_icon_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(2) { index ->
                val option = options[index]
                OutlinedCard(
                    onClick = { viewModel.dispatch(SettingsUiAction.SetAlternateIcon(option.alt)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(option.preview),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(option.label), style = MaterialTheme.typography.titleSmall)
                            if (state.useAltIcon == option.alt) {
                                Text(stringResource(R.string.app_icon_current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeSquareIcon(input: InputStream?): Bitmap {
    requireNotNull(input) { "no input" }
    val bitmap = input.use { BitmapFactory.decodeStream(it) }
    requireNotNull(bitmap) { "decode failed" }
    val side = minOf(bitmap.width, bitmap.height)
    val src = Rect((bitmap.width - side) / 2, (bitmap.height - side) / 2,
        (bitmap.width + side) / 2, (bitmap.height + side) / 2)
    val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(bitmap, src, Rect(0, 0, 512, 512), Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

private fun requestCustomIconShortcut(context: android.content.Context, bitmap: Bitmap) {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return
    val shortcut = ShortcutInfo.Builder(context, "nekosu_custom_icon")
        .setShortLabel(context.getString(R.string.app_icon_custom_shortcut_label))
        .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
        .setIntent(
            Intent(context, Class.forName("com.nekosu.ui.MainActivity")).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        .build()
    manager.requestPinShortcut(shortcut, null)
}
