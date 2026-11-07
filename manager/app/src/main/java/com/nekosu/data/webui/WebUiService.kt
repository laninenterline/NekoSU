package com.nekosu.data.webui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nekosu.R
import com.nekosu.data.kernel.KernelRepository
import com.nekosu.data.logging.LogCenterRepository
import com.nekosu.data.module.ModuleRepository
import com.nekosu.data.packageinfo.SuperUserRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Keeps the local WebUI HTTP server alive while the manager is in the background. */
class WebUiService : Service(), KoinComponent {
    private val kernelRepository: KernelRepository by inject()
    private val moduleRepository: ModuleRepository by inject()
    private val superUserRepository: SuperUserRepository by inject()
    private val logCenterRepository: LogCenterRepository by inject()

    private var server: WebUiHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                return START_NOT_STICKY
            }
        }
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        val token = prefs.getString(KEY_TOKEN, null) ?: WebUiPrefs.ensureToken(this)
        if (server == null) {
            startForegroundNotification()
            server = WebUiHttpServer(
                context = this,
                token = token,
                port = port,
                kernelRepository = kernelRepository,
                moduleRepository = moduleRepository,
                superUserRepository = superUserRepository,
                logCenterRepository = logCenterRepository,
                onStopRequested = { stopServer() },
            ).also {
                runCatching { it.start() }
                    .onFailure { error ->
                        server = null
                        stopServer()
                        android.util.Log.e("NekoSU-WebUI", "server start failed", error)
                    }
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "WebUI server", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("NekoSU WebUI")
            .setContentText("Local browser UI is running")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nekosu.webui.STOP"
        const val PREFS = "webui_server"
        const val KEY_PORT = "port"
        const val KEY_TOKEN = "token"
        const val DEFAULT_PORT = 8787
        private const val CHANNEL = "neko_webui"
        private const val NOTIFICATION_ID = 0x4E45

        fun start(context: Context) {
            val intent = Intent(context, WebUiService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WebUiService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** Token/port persistence shared by the service and the launcher activity. */
object WebUiPrefs {
    fun ensureToken(context: Context): String {
        val prefs = context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
        prefs.getString(WebUiService.KEY_TOKEN, null)?.let { return it }
        val token = buildString {
            repeat(32) { append("0123456789abcdef"[java.security.SecureRandom().nextInt(16)]) }
        }
        prefs.edit().putString(WebUiService.KEY_TOKEN, token).apply()
        return token
    }

    fun token(context: Context): String =
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .getString(WebUiService.KEY_TOKEN, null) ?: ensureToken(context)

    fun port(context: Context): Int =
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .getInt(WebUiService.KEY_PORT, WebUiService.DEFAULT_PORT)

    fun setPort(context: Context, port: Int) {
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .edit().putInt(WebUiService.KEY_PORT, port).apply()
    }
}
