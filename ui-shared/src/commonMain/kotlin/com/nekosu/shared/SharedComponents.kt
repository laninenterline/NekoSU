package com.nekosu.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** 超级用户应用组（与 App SuperUserPage 的 AppGroupItem 对齐）。 */
data class SharedAppGroupModel(
    val label: String,
    val packageName: String,
    val uid: Int,
    val allowSu: Boolean,
    val shouldUmount: Boolean = false,
    val customProfile: Boolean = false,
    val isManager: Boolean = false,
    val appsCount: Int = 1,
    val userName: String? = null,
)

@Composable
fun SharedAppGroupCard(
    model: SharedAppGroupModel,
    onClick: () -> Unit,
    onRootChange: ((Boolean) -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        model.label.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (model.appsCount > 1) "包含 ${model.appsCount} 个应用" else model.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (model.allowSu) SharedLabelPill("ROOT")
                    else if (model.shouldUmount) SharedLabelPillTonal("UMOUNT", MaterialTheme.colorScheme.secondaryContainer)
                    if (model.customProfile) SharedLabelPillTonal("CUSTOM", MaterialTheme.colorScheme.tertiaryContainer)
                    else if (!model.allowSu) SharedLabelPillTonal("DEFAULT", MaterialTheme.colorScheme.primaryContainer)
                    if (model.isManager) SharedLabelPillTonal("MANAGER", MaterialTheme.colorScheme.errorContainer)
                    model.userName?.let { SharedLabelPillTonal(it, MaterialTheme.colorScheme.secondaryContainer) }
                }
            }
            if (onRootChange != null) {
                Switch(
                    checked = model.allowSu,
                    onCheckedChange = onRootChange,
                    thumbContent = null,
                )
            } else {
                Icon(
                    Icons.TwoTone.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SharedLabelPill(text: String) {
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
fun SharedLabelPillTonal(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SharedSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 模块卡片（与 App ModulePage 的 ModuleItem 视觉对齐）。 */
data class SharedModuleModel(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val update: Boolean = false,
    val remove: Boolean = false,
    val metamodule: Boolean = false,
    val hasWebUi: Boolean = false,
)

@Composable
fun SharedModuleCard(
    model: SharedModuleModel,
    onCheckedChange: (Boolean) -> Unit,
    onAction: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (model.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Version: ${model.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Author: ${model.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = model.enabled,
                    onCheckedChange = onCheckedChange,
                    thumbContent = null,
                )
            }
            if (model.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAction) { Text("操作") }
                TextButton(onClick = onRemove) {
                    Text(if (model.remove) "恢复" else "卸载", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 模块卡片头部：名称 / Version / Author + 启用开关（Android 与 Web 共用）。 */
@Composable
fun SharedModuleHeader(
    model: SharedModuleModel,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (model.remove) TextDecoration.LineThrough else null,
            )
            Text(
                "Version: ${model.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (model.remove) TextDecoration.LineThrough else null,
            )
            Text(
                "Author: ${model.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (model.remove) TextDecoration.LineThrough else null,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            thumbContent = null,
        )
    }
}
