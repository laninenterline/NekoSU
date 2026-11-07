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
import com.nekosu.data.profile.ProfileRepository
import com.nekosu.data.shell.KsuCliRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Keeps the local WebUI HTTP server (and optional screen mirror) alive. */
class WebUiService : Service(), KoinComponent {
    private val kernelRepository: KernelRepository by inject()
    private val moduleRepository: ModuleRepository by inject()
    private val superUserRepository: SuperUserRepository by inject()
    private val logCenterRepository: LogCenterRepository by inject()
    private val ksuCliRepository: KsuCliRepository by inject()
    private val profileRepository: ProfileRepository by inject()

    private var server: WebUiHttpServer? = null

    private val screenSource = object : WebUiScreenSource {
        override fun tap(nx: Float, ny: Float) {
            val w = ScreenMirror.frameWidth
            val h = ScreenMirror.frameHeight
            if (w > 0 && h > 0) inject("input tap ${(nx * w).toInt()} ${(ny * h).toInt()}")
        }

        override fun swipe(nx1: Float, ny1: Float, nx2: Float, ny2: Float) {
            val w = ScreenMirror.frameWidth
            val h = ScreenMirror.frameHeight
            if (w > 0 && h > 0) {
                inject(
                    "input swipe ${(nx1 * w).toInt()} ${(ny1 * h).toInt()} " +
                        "${(nx2 * w).toInt()} ${(ny2 * h).toInt()} 220",
                )
            }
        }

        private fun inject(command: String) {
            Thread {
                runCatching {
                    ksuCliRepository.withNewRootShell {
                        newJob().add(command).to(ArrayList(), ArrayList()).exec()
                    }
                }
            }.start()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MIRROR -> {
                ensureForeground(mirror = true)
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (data != null) {
                    runCatching { ScreenMirror.start(this, code, data) }
                        .onFailure { android.util.Log.e("NekoSU-WebUI", "mirror start failed", it) }
                }
                startServer()
                return START_STICKY
            }
            ACTION_STOP_MIRROR -> {
                ScreenMirror.stop()
                startServer()
                return START_STICKY
            }
            ACTION_STOP -> {
                ScreenMirror.stop()
                stopServer()
                return START_NOT_STICKY
            }
        }
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        ensureForeground(mirror = ScreenMirror.running)
        if (server != null) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val randomPort = prefs.getBoolean(KEY_RANDOM_PORT, false)
        val port = if (randomPort) 0 else prefs.getInt(KEY_PORT, DEFAULT_PORT)
        val token = prefs.getString(KEY_TOKEN, null) ?: WebUiPrefs.ensureToken(this)
        server = WebUiHttpServer(
            context = this,
            token = token,
            port = port,
            kernelRepository = kernelRepository,
            moduleRepository = moduleRepository,
            superUserRepository = superUserRepository,
            logCenterRepository = logCenterRepository,
            profileRepository = profileRepository,
            screenSource = screenSource,
            onStopRequested = { stopServer() },
        ).also { started ->
            runCatching { started.start() }
                .onSuccess {
                    WebUiPrefs.setBoundPort(this, started.boundPort)
                    WebUiPrefs.setEnabled(this, true)
                }
                .onFailure { error ->
                    server = null
                    WebUiPrefs.setEnabled(this, false)
                    stopServer()
                    android.util.Log.e("NekoSU-WebUI", "server start failed", error)
                }
        }
    }

    private fun ensureForeground(mirror: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "WebUI server", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("NekoSU WebUI")
            .setContentText(if (mirror) "浏览器界面 + 屏幕镜像运行中" else "Local browser UI is running")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (mirror) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopServer() {
        ScreenMirror.stop()
        server?.stop()
        server = null
        WebUiPrefs.setEnabled(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        ScreenMirror.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nekosu.webui.STOP"
        const val ACTION_START_MIRROR = "com.nekosu.webui.START_MIRROR"
        const val ACTION_STOP_MIRROR = "com.nekosu.webui.STOP_MIRROR"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val PREFS = "webui_server"
        const val KEY_PORT = "port"
        const val KEY_TOKEN = "token"
        const val KEY_ENABLED = "enabled"
        const val KEY_RANDOM_PORT = "random_port"
        const val KEY_BOUND_PORT = "bound_port"
        const val DEFAULT_PORT = 8787
        private const val CHANNEL = "neko_webui"
        private const val NOTIFICATION_ID = 0x4E45

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, WebUiService::class.java),
            )
        }

        fun startMirror(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, WebUiService::class.java).apply {
                action = ACTION_START_MIRROR
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stopMirror(context: Context) {
            context.startService(Intent(context, WebUiService::class.java).setAction(ACTION_STOP_MIRROR))
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

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(WebUiService.KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(WebUiService.KEY_ENABLED, enabled).apply()
    }

    fun randomPort(context: Context): Boolean =
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(WebUiService.KEY_RANDOM_PORT, false)

    fun setRandomPort(context: Context, enabled: Boolean) {
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(WebUiService.KEY_RANDOM_PORT, enabled).apply()
    }

    fun fixedPort(context: Context): Int = port(context)

    fun boundPort(context: Context): Int =
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .getInt(WebUiService.KEY_BOUND_PORT, 0)

    fun setBoundPort(context: Context, port: Int) {
        context.getSharedPreferences(WebUiService.PREFS, Context.MODE_PRIVATE)
            .edit().putInt(WebUiService.KEY_BOUND_PORT, port).apply()
    }
}
