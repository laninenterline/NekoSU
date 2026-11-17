package com.nekosu.ui.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.settings.LauncherIconHider
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.ui.MainActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Dialer entry point: `*#*#<code>#*#*` disables stealth mode.
 *
 * The kernel keeps stealth enabled while the UI looks like a non-root Manager,
 * so this receiver is the recovery path when settings are unreachable. The
 * accepted code set includes the manifest host default, a root-persistent
 * backup and the app setting, so a stale code can never lock the user out.
 */
class StealthReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: AppSettingsRepository by inject()
    private val ksuCli: KsuCliRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SECRET_CODE) return
        if (!senderTrusted(context)) return
        val dialed = intent.data?.host ?: return

        val pending = goAsync()
        Thread {
            var accepted = false
            var restored = false
            var messageRes = R.string.stealth_restore_failed
            try {
                accepted = dialed in StealthCodeStore.acceptedCodes(settings, ksuCli)
                if (accepted) {
                    runCatching { LauncherIconHider.showSelected(context, settings) }
                    val wasEnabled = Stealth.kernelState() == 1
                    restored = Stealth.setEnabled(false)
                    messageRes = when {
                        restored && wasEnabled -> R.string.stealth_restore_disabled
                        restored -> R.string.stealth_restore_not_enabled
                        !Stealth.kernelHasStealthFeature() -> R.string.stealth_restore_unsupported
                        else -> R.string.stealth_restore_failed
                    }
                }
            } catch (_: Throwable) {
                restored = false
                messageRes = R.string.stealth_restore_failed
            } finally {
                Handler(Looper.getMainLooper()).post {
                    try {
                        if (accepted) {
                            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_LONG).show()
                            if (restored) {
                                val launch = Intent(context, MainActivity::class.java).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                    )
                                }
                                if (runCatching { context.startActivity(launch) }.isFailure) {
                                    Toast.makeText(
                                        context,
                                        R.string.stealth_restore_launch_failed,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }.start()
    }

    /** Only accept SECRET_CODE broadcasts from the dialer/system/ourselves. */
    private fun senderTrusted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return senderTrustedApi34(context)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun senderTrustedApi34(context: Context): Boolean {
        val uid = runCatching { sentFromUid }.getOrDefault(-1)
        if (uid < 0) return true // fail open: never lock the user out
        if (uid == Process.SYSTEM_UID || uid == Process.PHONE_UID || uid == Process.myUid()) {
            return true
        }
        val packages = buildSet {
            runCatching { sentFromPackage }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { add(it) }
            runCatching { context.packageManager.getPackagesForUid(uid) }
                .getOrNull()?.forEach { add(it) }
        }
        if (packages.isEmpty()) return false
        val dialer = runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()
        if (dialer != null && dialer in packages) return true
        return packages.any { pkg ->
            runCatching {
                val info = context.packageManager.getApplicationInfo(pkg, 0)
                (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            }.getOrDefault(false)
        }
    }

    private companion object {
        const val ACTION_SECRET_CODE = "android.provider.Telephony.SECRET_CODE"
    }
}
