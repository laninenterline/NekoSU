package com.nekosu.data.webui

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nekosu.BuildConfig
import com.nekosu.data.kernel.KernelRepository
import com.nekosu.data.logging.LogCenterRepository
import com.nekosu.data.logging.LogSource
import com.nekosu.data.module.ModuleRepository
import com.nekosu.data.packageinfo.SuperUserRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/**
 * Loops the manager's own views into a browser: a token-guarded HTTP server bound to
 * 127.0.0.1 only.  The static frontend lives in assets/webui and talks JSON to the /api endpoints;
 * every API call is answered from the same repositories the Compose UI uses.
 */
class WebUiHttpServer(
    private val context: Context,
    private val token: String,
    private val port: Int,
    private val kernelRepository: KernelRepository,
    private val moduleRepository: ModuleRepository,
    private val superUserRepository: SuperUserRepository,
    private val logCenterRepository: LogCenterRepository,
    private val onStopRequested: () -> Unit,
) {
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val workers = Executors.newFixedThreadPool(8)
    private val acceptThread = Thread({ acceptLoop() }, "neko-webui-accept").apply { isDaemon = true }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
        acceptThread.start()
    }

    val boundPort: Int get() = serverSocket?.localPort ?: port

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        workers.shutdownNow()
    }

    private fun acceptLoop() {
        while (running.get()) {
            val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
            workers.execute { handle(socket) }
        }
    }

    private data class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
    )

    private fun handle(socket: Socket) {
        socket.use {
            runCatching {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val target = parts[1]
                val headers = LinkedHashMap<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (length > 0) {
                    val buf = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val n = reader.read(buf, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    String(buf, 0, read)
                } else ""
                val queryIdx = target.indexOf('?')
                val path = (if (queryIdx >= 0) target.substring(0, queryIdx) else target).ifEmpty { "/" }
                val query = if (queryIdx >= 0) {
                    target.substring(queryIdx + 1).split('&').filter { it.contains('=') }.associate {
                        val k = it.substringBefore('=')
                        k to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                    }
                } else emptyMap()
                val request = Request(method, path, query, headers, body)
                route(request, socket.getOutputStream())
            }.onFailure { error ->
                runCatching { writeResponse(socket.getOutputStream(), 500, "application/json", errorJson(error.message).toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    private fun route(request: Request, out: OutputStream) {
        val path = request.path
        if (!path.startsWith("/api/")) {
            serveStatic(path, out)
            return
        }
        val supplied = request.headers["authorization"]?.removePrefix("Bearer ")?.trim()
            ?: request.query["token"]
        if (supplied != token) {
            writeJson(out, 401, """{"error":"unauthorized"}""")
            return
        }
        when {
            path == "/api/status" && request.method == "GET" -> handleStatus(out)
            path == "/api/modules" && request.method == "GET" -> handleModules(out)
            path == "/api/superuser" && request.method == "GET" -> handleSuperUser(out)
            path.startsWith("/api/logs") && request.method == "GET" ->
                handleLogs(out, request.query["source"] ?: "ALL")
            path == "/api/about" && request.method == "GET" -> handleAbout(out)
            path == "/api/refresh" && request.method == "POST" -> handleRefresh(out)
            path == "/api/stop" && request.method == "POST" -> {
                writeJson(out, 200, """{"ok":true}""")
                onStopRequested()
            }
            path.startsWith("/api/modules/") && request.method == "POST" -> handleModuleAction(path, out)
            else -> writeJson(out, 404, """{"error":"not found"}""")
        }
    }

    private fun handleStatus(out: OutputStream) {
        val status = runBlocking { kernelRepository.getStatus() }
        val payload = mapOf(
            "version" to BuildConfig.VERSION_NAME,
            "versionCode" to BuildConfig.VERSION_CODE,
            "packageName" to BuildConfig.APPLICATION_ID,
            "sdk" to android.os.Build.VERSION.SDK_INT,
            "kernel" to status,
        )
        writeJson(out, 200, gson.toJson(payload))
    }

    private fun handleModules(out: OutputStream) {
        runBlocking { moduleRepository.refreshInstalledModules(manual = false, checkUpdates = false) }
        writeJson(out, 200, gson.toJson(moduleRepository.installedModules.value.modules))
    }

    private fun handleSuperUser(out: OutputStream) {
        runBlocking { superUserRepository.refresh() }
        writeJson(out, 200, gson.toJson(superUserRepository.state.value.groups))
    }

    private fun handleLogs(out: OutputStream, sourceName: String) {
        val source = runCatching { LogSource.valueOf(sourceName.uppercase()) }.getOrDefault(LogSource.ALL)
        val text = runBlocking { logCenterRepository.load(source) }
        writeJson(out, 200, gson.toJson(mapOf("source" to source.name, "text" to text)))
    }

    private fun handleAbout(out: OutputStream) {
        writeJson(
            out, 200,
            gson.toJson(
                mapOf(
                    "version" to BuildConfig.VERSION_NAME,
                    "versionCode" to BuildConfig.VERSION_CODE,
                    "webui" to "NekoSU WebUI",
                    "token" to token,
                ),
            ),
        )
    }

    private fun handleRefresh(out: OutputStream) {
        runBlocking {
            moduleRepository.refreshInstalledModules(manual = true, checkUpdates = false)
            superUserRepository.refresh()
        }
        writeJson(out, 200, """{"ok":true}""")
    }

    private fun handleModuleAction(path: String, out: OutputStream) {
        // /api/modules/<id>/<enable|disable|remove|restore>
        val segments = path.trim('/').split('/')
        if (segments.size != 4 || segments[0] != "api" || segments[1] != "modules") {
            writeJson(out, 404, """{"error":"not found"}""")
            return
        }
        val id = segments[2]
        val action = segments[3]
        val result = runBlocking {
            when (action) {
                "enable" -> moduleRepository.setModuleEnabled(id, true)
                "disable" -> moduleRepository.setModuleEnabled(id, false)
                "remove" -> moduleRepository.setModuleRemoved(id, true)
                "restore" -> moduleRepository.setModuleRemoved(id, false)
                else -> Result.failure(IllegalArgumentException("unknown action"))
            }
        }
        if (result.isSuccess) writeJson(out, 200, """{"ok":true}""")
        else writeJson(out, 400, errorJson(result.exceptionOrNull()?.message))
    }

    private fun serveStatic(rawPath: String, out: OutputStream) {
        val path = rawPath.trimStart('/').ifEmpty { "index.html" }
        if (path.contains("..")) {
            writeJson(out, 400, """{"error":"bad path"}""")
            return
        }
        val bytes = runCatching { context.assets.open("webui/$path").use { it.readBytes() } }.getOrNull()
        if (bytes == null) {
            writeJson(out, 404, """{"error":"not found"}""")
            return
        }
        var data = bytes
        if (path == "index.html") {
            data = String(bytes, Charsets.UTF_8).replace("__TOKEN__", token).toByteArray(Charsets.UTF_8)
        }
        writeResponse(out, 200, mimeOf(path), data)
    }

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "")) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    private fun writeJson(out: OutputStream, code: Int, json: String) =
        writeResponse(out, code, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))

    private fun writeResponse(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val header = "HTTP/1.1 $code ${if (code == 200) "OK" else "NOK"}\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun errorJson(message: String?): String =
        gson.toJson(mapOf("error" to (message ?: "unknown")))
}
