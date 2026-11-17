package com.nekosu.ui.screen.about

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.material.icons.twotone.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekosu.BuildConfig
import com.nekosu.R
import com.nekosu.ui.navigation.LocalNavigator
import com.nekosu.ui.navigation.Route
import com.nekosu.ui.util.DebugFeatureGate
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页的 Miuix 风格实现：复用与 Material 版相同的数据（BuildConfig / 导航 / 链接），
 * 仅把 TopBar、卡片与行替换为 Miuix 组件。Material 版保持不变。
 */
@Composable
fun AboutScreenMiuix() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.about),
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = stringResource(R.string.about),
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
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AboutStatusCardMiuix()
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.get_source_code),
                        summary = stringResource(R.string.get_source_code_detail),
                        startAction = {
                            Icon(
                                Icons.TwoTone.Code,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            uriHandler.openUri("https://github.com/laninenterline/NekoSU")
                        },
                    )
                    BasicComponent(
                        title = stringResource(R.string.open_source_license),
                        summary = stringResource(R.string.open_source_license_settings_description),
                        startAction = {
                            Icon(
                                Icons.TwoTone.Copyright,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            navigator.push(Route.OpenSourceLicense)
                        },
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.app_name),
                        summary = stringResource(R.string.about_app_icon, "AzeriNeko")
                            .replace(Regex("<[^>]*>"), ""),
                        startAction = {
                            Icon(
                                Icons.TwoTone.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onSurface,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutStatusCardMiuix() {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val appIcon = remember { context.packageManager.getApplicationIcon(context.packageName) }
    var iconTapCount by remember { mutableIntStateOf(0) }
    var iconLastTapAt by remember { mutableLongStateOf(0L) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        // Release builds must not expose the developer unlock gesture.
                        if (!BuildConfig.DEBUG) return@clickable
                        val now = SystemClock.elapsedRealtime()
                        if (now - iconLastTapAt > 2000) iconTapCount = 0
                        iconLastTapAt = now
                        iconTapCount += 1
                        if (iconTapCount >= 7) {
                            iconTapCount = 0
                            DebugFeatureGate.unlock(context)
                            Toast.makeText(
                                context,
                                context.getString(R.string.debug_features_unlocked),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                painter = rememberDrawablePainter(appIcon),
                contentDescription = stringResource(R.string.app_name),
            )
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
