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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launcher for the local browser UI; shows the URL/token and starts/stops the service. */
class WebUiServerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WebUiServerScreen()
                }
            }
        }
    }
}

@Composable
private fun WebUiServerScreen() {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.IO) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val port = WebUiPrefs.port(context)
    val token = WebUiPrefs.token(context)
    val url = "http://127.0.0.1:$port/"

    fun refresh() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { probe(url, token) }
            withContext(Dispatchers.Main) {
                running = ok
                message = if (ok) "WebUI 正在运行" else "WebUI 未运行"
            }
        }
    }
    remember { refresh(); true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("NekoSU WebUI（浏览器版）", style = MaterialTheme.typography.headlineSmall)
        Text(
            "在设备本地 127.0.0.1 上提供管理器的 Web 版本；仅本机可访问，所有 /api 请求需要 token。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("地址", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(url) }
                Text("Token", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(token) }
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                WebUiService.start(context)
                Toast.makeText(context, "正在启动 WebUI…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    kotlinx.coroutines.delay(600)
                    refresh()
                }
            }) { Text("启动") }
            OutlinedButton(onClick = {
                WebUiService.stop(context)
                Toast.makeText(context, "正在停止 WebUI…", Toast.LENGTH_SHORT).show()
                scope.launch { kotlinx.coroutines.delay(400); refresh() }
            }) { Text("停止") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(context, url) }) { Text("复制地址") }
            OutlinedButton(onClick = { copy(context, token) }) { Text("复制 Token") }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$url?token=$token")))
                }.onFailure {
                    Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
                }
            },
        ) { Text("在浏览器中打开") }
        if (running) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { scope.launch { withContext(Dispatchers.IO) { stopViaApi(url, token) }; refresh() } },
            ) { Text("通过 API 停止") }
        }
    }
}

private fun copy(context: Context, text: String) {
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

private fun stopViaApi(url: String, token: String) {
    runCatching {
        val connection = java.net.URL("${url}api/stop").openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.responseCode
    }
}
