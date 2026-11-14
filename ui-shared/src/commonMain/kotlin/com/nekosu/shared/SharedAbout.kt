package com.nekosu.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


/** 关于页内容（Android About 与 Web About 共用）。 */
data class SharedAboutModel(
    val appName: String,
    val version: String,
    val versionCode: Int,
    val driverVersion: String,
    val onOpenUrl: (String) -> Unit,
)

@Composable
fun SharedAboutContent(model: SharedAboutModel) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    model.appName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "管理器 ${model.version} (${model.versionCode})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    "内核驱动 ${model.driverVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        SharedSettingGroup("相关链接") {
            SharedSettingChevronRow(Icons.TwoTone.Info, "GitHub 仓库", "github.com/laninenterline/NekoSU") {
                model.onOpenUrl("https://github.com/laninenterline/NekoSU")
            }
            SharedSettingChevronRow(Icons.TwoTone.Info, "KernelSU", "上游项目与文档") {
                model.onOpenUrl("https://kernelsu.org")
            }
            SharedSettingChevronRow(Icons.TwoTone.Info, "SUSFS", "上游 SUSFS4KSU 源码") {
                model.onOpenUrl("https://gitlab.com/simonpunk/susfs4ksu")
            }
        }
        SharedSettingGroup("开源许可") {
            SharedSettingRowSurface {
                Text(
                    "NekoSU 基于 KernelSU（GPL-3.0）与 SUSFS（GPL-2.0）构建。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SharedSettingGroup("致谢") {
            SharedSettingRowSurface {
                Text(
                    "感谢 KernelSU、SUSFS、Magisk 及所有上游贡献者。NekoSU 仅用于临时 root（越狱模式）场景。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
