package com.nekosu.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** KMI / 加载诊断数据（Android KmiDiagnosticsScreen 与 Web KMI 页共用）。 */
data class SharedKmiModel(
    val device: String,
    val android: String,
    val kernelRelease: String,
    val kernelVersion: String,
    val kmi: String,
    val kmiMatched: Boolean,
    val rootAvailable: Boolean,
    val lkmMode: String,
    val lateLoad: Boolean,
    val ksuVersion: String,
    val susfsText: String,
    val hook: String,
    val advice: List<String>,
    val riskyVendor: Boolean = false,
)

@Composable
fun SharedKmiContent(
    model: SharedKmiModel,
    onCopyReport: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SharedSettingGroup("诊断信息") {
            SharedSettingRowSurface {
                Column(Modifier.padding(16.dp)) {
                    KmiRow("设备", model.device)
                    KmiRow("Android 版本", model.android)
                    KmiRow("内核 release", model.kernelRelease)
                    KmiRow("内核版本", model.kernelVersion)
                    KmiRow("KMI", model.kmi)
                    KmiRow("KMI 匹配", if (model.kmiMatched) "匹配" else "未匹配")
                    KmiRow("Root 状态", if (model.rootAvailable) "可用" else "不可用")
                    KmiRow("加载模式", model.lkmMode)
                    KmiRow("Late-load", if (model.lateLoad) "是" else "否")
                    KmiRow("Hook 类型", model.hook)
                    KmiRow("KSU 版本", model.ksuVersion)
                    KmiRow("SuSFS", model.susfsText)
                }
            }
        }
        SharedSettingGroup("建议") {
            SharedSettingRowSurface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (model.advice.isEmpty()) {
                        Text("暂无建议", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        model.advice.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (model.riskyVendor) {
            Text(
                "当前设备属于已知高风险厂商，越狱操作可能导致重启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCopyReport != null) {
                Button(onClick = onCopyReport) { Text("复制诊断") }
            }
            if (onRefresh != null) {
                Button(onClick = onRefresh) { Text("刷新") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun KmiRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
