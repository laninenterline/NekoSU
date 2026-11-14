package com.nekosu.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SharedLogModel(
    val sources: List<Pair<String, String>>,
    val selectedSource: String,
    val query: String,
    val text: String,
    val loading: Boolean,
)

@Composable
fun SharedLogContent(
    model: SharedLogModel,
    onSelectSource: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            model.sources.forEach { (value, label) ->
                FilterChip(
                    selected = model.selectedSource == value,
                    onClick = { onSelectSource(value) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(999.dp),
                )
            }
        }
        OutlinedTextField(
            value = model.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索日志") },
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (model.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Text(
                model.text.ifEmpty { "（无日志）" },
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onRefresh != null) {
                androidx.compose.material3.Button(onClick = onRefresh) { Text("刷新") }
            }
            if (onExport != null) {
                androidx.compose.material3.OutlinedButton(onClick = onExport) { Text("导出") }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

data class SharedThemeModel(
    val darkMode: String,
    val seedIndex: Int,
)

@Composable
fun SharedThemeContent(
    model: SharedThemeModel,
    onDarkModeChange: (String) -> Unit,
    onSeedChange: (Int) -> Unit,
) {
    val seeds = listOf(
        Color(0xFF6750A4),
        Color(0xFF0061A4),
        Color(0xFF2E6B3F),
        Color(0xFF8B5000),
    )
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SharedSettingGroup("深色模式") {
            SharedSettingRowSurface {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                        FilterChip(
                            selected = model.darkMode == value,
                            onClick = { onDarkModeChange(value) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }
            }
        }
        SharedSettingGroup("主题色") {
            SharedSettingRowSurface {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    seeds.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (model.seedIndex == index) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onSeedChange(index) },
                        )
                    }
                }
            }
        }
        SharedSettingGroup("预览") {
            SharedSettingRowSurface {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("主色容器", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                        Text("主色", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
