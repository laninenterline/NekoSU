package com.nekosu.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SharedHomeModel(
    val working: Boolean,
    val lkmMode: Boolean,
    val lateLoad: Boolean,
    val superuserCount: Int,
    val moduleCount: Int,
    val device: String,
    val kernelRelease: String,
    val androidVersion: String,
    val kernelDriverVersion: String,
    val managerVersion: String,
    val susfsVersion: String,
    val selinux: String,
    val seccomp: String,
    val kmi: String,
    val hook: String,
)

@Composable
fun SharedHomeContent(
    model: SharedHomeModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        StatusCard(model)
        Spacer(Modifier.height(2.dp))
        SharedSection("版本信息") {
            SharedInfoRow(Icons.TwoTone.Smartphone, "设备", model.device)
            SharedInfoRow(Icons.TwoTone.DeveloperBoard, "内核版本", model.kernelRelease)
            SharedInfoRow(Icons.TwoTone.Android, "Android 版本", model.androidVersion)
            SharedInfoRow(Icons.TwoTone.Memory, "内核驱动版本", model.kernelDriverVersion)
            SharedInfoRow(Icons.TwoTone.Tag, "管理器版本", model.managerVersion)
            SharedInfoRow(Icons.TwoTone.Memory, "SuSFS 版本", model.susfsVersion)
        }
        SharedSection("状态信息") {
            SharedInfoRow(Icons.TwoTone.Security, "SELinux 状态", model.selinux)
            SharedInfoRow(Icons.TwoTone.Security, "Seccomp 状态", model.seccomp)
            SharedInfoRow(Icons.TwoTone.Tag, "KMI", model.kmi)
            SharedInfoRow(Icons.TwoTone.Tag, "Hook 类型", model.hook)
            SharedInfoRow(Icons.TwoTone.Tag, "Late-load", if (model.lateLoad) "是" else "否")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(model: SharedHomeModel) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.TwoTone.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (model.working) "工作中" else "未安装",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (model.lkmMode) SharedPill("LKM")
                    if (model.lateLoad) {
                        Spacer(Modifier.width(6.dp))
                        SharedPill("越狱模式")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${model.superuserCount} 个超级用户, ${model.moduleCount} 个模块",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SharedPill(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun SharedSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SharedInfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 46.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}
