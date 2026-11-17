package com.nekosu.ui.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.shell.KsuCliRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Boot-time synchronization: make sure the root-persistent stealth dialer code
 * and the app setting agree after an uninstall/reinstall or a data restore.
 * The kernel itself reloads `/data/adb/ksu/stealth` during post-fs-data.
 */
class StealthBootReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: AppSettingsRepository by inject()
    private val ksuCli: KsuCliRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Thread {
            runCatching { StealthCodeStore.sync(settings, ksuCli) }
        }.start()
    }
}
