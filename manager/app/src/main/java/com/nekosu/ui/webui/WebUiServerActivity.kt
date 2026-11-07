package com.nekosu.ui.webui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nekosu.data.webui.WebUiPrefs
import com.nekosu.data.webui.WebUiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launcher and settings for the local browser UI. */
class WebUiServerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { WebUiServerScreen() }
            }
        }
    }
}

@Composable
private fun WebUiServerScreen() {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.IO) }
    var enabled by remember { mutableStateOf(WebUiPrefs.enabled(context)) }
    var randomPort by remember { mutableStateOf(WebUiPrefs.randomPort(context)) }
    var boundPort by remember { mutableStateOf(WebUiPrefs.boundPort(context)) }
    var running by remember { mutableStateOf(false) }
    val token = WebUiPrefs.token(context)

    fun refresh() {
        boundPort = WebUiPrefs.boundPort(context)
        val url = if (boundPort > 0) "http://127.0.0.1:$boundPort/" else ""
        scope.launch {
            val ok = url.isNotEmpty() && probe(url, token)
            withContext(Dispatchers.Main) { running = ok }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("NekoSU WebUI（浏览器版）", style = MaterialTheme.typography.headlineSmall)
        Text(
            "在设备本地提供管理器的浏览器版本；仅 127.0.0.1 可访问，API 需要 token。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("启用 WebUI"); Text("在设置中也可开关", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = enabled, onCheckedChange = { value ->
                        enabled = value
                        WebUiPrefs.setEnabled(context, value)
                        if (value) WebUiService.start(context) else WebUiService.stop(context)
                        if (value) scope.launch { delay(700); refresh() }
                    })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("随机端口"); Text("关闭时使用固定端口 ${WebUiPrefs.fixedPort(context)}", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = randomPort, onCheckedChange = { value ->
                        randomPort = value
                        WebUiPrefs.setRandomPort(context, value)
                        if (enabled) {
                            WebUiService.stop(context)
                            scope.launch { delay(400); WebUiService.start(context); delay(800); refresh() }
                        }
                    })
                }
                Text(
                    "状态: " + if (running) "运行中（端口 ${boundPort}）" else if (enabled) "启动中…" else "未运行",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("地址", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(if (boundPort > 0) "http://127.0.0.1:$boundPort/" else "（启动后显示）") }
                Text("Token", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(token) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyIt(context, if (boundPort > 0) "http://127.0.0.1:$boundPort/" else "") }) { Text("复制地址") }
            OutlinedButton(onClick = { copyIt(context, token) }) { Text("复制 Token") }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = boundPort > 0 && running,
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$boundPort/?token=$token")))
                }.onFailure { Toast.makeText(context, "没有可用浏览器", Toast.LENGTH_SHORT).show() }
            },
        ) { Text("在浏览器中打开") }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                WebUiService.stop(context)
                enabled = false
                WebUiPrefs.setEnabled(context, false)
                scope.launch { delay(400); refresh() }
            },
        ) { Text("停止服务") }
    }
}

private fun copyIt(context: Context, text: String) {
    if (text.isEmpty()) return
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager.setPrimaryClip(ClipData.newPlainText("NekoSU WebUI", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun probe(url: String, token: String): Boolean = runCatching {
    val connection = java.net.URL("${url}api/status").openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = 800
    connection.readTimeout = 800
    connection.setRequestProperty("Authorization", "Bearer $token")
    connection.responseCode == 200
}.getOrDefault(false)
