package com.nekosu.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** App Profile 详情（Android AppProfile 与 Web 详情页共用）。 */
data class SharedAppProfileModel(
    val label: String,
    val packageName: String,
    val uid: Int,
    val allowSu: Boolean,
    val umount: Boolean,
    val appsCount: Int = 1,
    val isManager: Boolean = false,
)

@Composable
fun SharedAppProfileContent(
    model: SharedAppProfileModel,
    onRootChange: (Boolean) -> Unit,
    onUmountChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    model.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    model.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "UID ${model.uid}" + if (model.appsCount > 1) " · 包含 ${model.appsCount} 个应用" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (model.allowSu) SharedLabelPill("ROOT")
                    if (model.umount) SharedLabelPillTonal("UMOUNT", MaterialTheme.colorScheme.secondaryContainer)
                    SharedLabelPillTonal(
                        if (model.allowSu || model.umount) "CUSTOM" else "DEFAULT",
                        MaterialTheme.colorScheme.tertiaryContainer,
                    )
                    if (model.isManager) SharedLabelPillTonal("MANAGER", MaterialTheme.colorScheme.errorContainer)
                }
            }
        }
        SharedSettingGroup("权限配置") {
            SharedSettingSwitchRow(
                Icons.TwoTone.Security,
                "允许 ROOT",
                "授予该应用超级用户权限",
                model.allowSu,
                onRootChange,
            )
            SharedSettingSwitchRow(
                Icons.TwoTone.FolderOff,
                "不挂载模块",
                "该应用运行时默认不挂载任何模块",
                model.umount,
                onUmountChange,
            )
        }
        SharedSettingGroup("操作") {
            SharedSettingRowSurface {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReset) { Text("恢复默认") }
                }
            }
        }
    }
}
