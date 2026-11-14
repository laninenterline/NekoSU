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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
