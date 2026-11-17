package com.nekosu.ui.security

import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.shell.KsuCliRepository
import com.topjohnwu.superuser.ShellUtils

/**
 * Cross-uninstall backup for the stealth dialer code.
 *
 * The kernel keeps the stealth flag across app uninstalls, so the dialer code
 * must also survive that. The default code is compiled into the manifest
 * SECRET_CODE host; a custom code can additionally be written to
 * `/data/adb/ksu/stealth_code` (root-only) and to app settings.
 */
object StealthCodeStore {
    /** Must match the SECRET_CODE host in AndroidManifest.xml. */
    const val DEFAULT_CODE = "736284"

    const val SETTINGS_KEY = "stealth_code"

    private const val DIR = "/data/adb/ksu"
    private const val PATH = "$DIR/stealth_code"

    fun sanitize(raw: String?): String? =
        raw?.trim()?.filter { it.isDigit() }?.take(8)?.takeIf { it.length in 4..8 }

    /** Reads the root-persistent copy; null when unavailable. */
    fun read(ksuCli: KsuCliRepository): String? = runCatching {
        sanitize(ShellUtils.fastCmd(ksuCli.getRootShell(), "cat $PATH 2>/dev/null"))
    }.getOrNull()

    /** Writes and verifies the root-persistent copy. */
    fun write(ksuCli: KsuCliRepository, code: String): Boolean = runCatching {
        val safe = sanitize(code) ?: return@runCatching false
        val script = """
            mkdir -p $DIR
            printf '%s' '$safe' > $PATH.new
            chmod 600 $PATH.new 2>/dev/null
            chcon u:object_r:ksu_file:s0 $PATH.new 2>/dev/null || restorecon $PATH.new 2>/dev/null || true
            mv -f $PATH.new $PATH
            chmod 600 $PATH 2>/dev/null
        """.trimIndent()
        ShellUtils.fastCmd(ksuCli.getRootShell(), script)
        sanitize(ShellUtils.fastCmd(ksuCli.getRootShell(), "cat $PATH 2>/dev/null")) == safe
    }.getOrDefault(false)

    /** Disk copy first, then app settings, then the built-in default. */
    fun effectiveCode(
        settings: AppSettingsRepository,
        ksuCli: KsuCliRepository,
    ): String = read(ksuCli)
        ?: sanitize(settings.getString(SETTINGS_KEY))
        ?: DEFAULT_CODE

    /**
     * Codes accepted by the dialer. The built-in manifest code is always
     * included so a stale/corrupt custom code can never lock the user out.
     */
    fun acceptedCodes(
        settings: AppSettingsRepository,
        ksuCli: KsuCliRepository,
    ): Set<String> = buildSet {
        add(DEFAULT_CODE)
        read(ksuCli)?.let { add(it) }
        sanitize(settings.getString(SETTINGS_KEY))?.let { add(it) }
    }

    /**
     * Synchronizes the root-persistent copy and app settings.
     * @return true when app settings changed and UI should refresh.
     */
    fun sync(
        settings: AppSettingsRepository,
        ksuCli: KsuCliRepository,
    ): Boolean = runCatching {
        val disk = read(ksuCli)
        val pref = sanitize(settings.getString(SETTINGS_KEY))
        when {
            disk == null && pref != null -> {
                write(ksuCli, pref)
                false
            }

            disk != null && disk != pref -> {
                settings.putString(SETTINGS_KEY, disk)
                true
            }

            else -> false
        }
    }.getOrDefault(false)
}
