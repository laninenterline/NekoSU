package com.nekosu.ui.component.miuix

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 风格的二级页面顶栏：标题 + 返回按钮 + 可选操作区。
 *
 * 供尚未完整迁移到 Miuix 的页面在外层替换 Material TopAppBar 使用，
 * 页面内容与 ViewModel/回调保持不变（混合实现）。
 */
@Composable
fun MiuixPageTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val colorScheme = MiuixTheme.colorScheme
    TopAppBar(
        title = title,
        color = colorScheme.surface,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                    )
                }
            }
        },
        actions = actions,
        bottomContent = bottomContent,
    )
}
