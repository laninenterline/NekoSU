package com.nekosu.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.settings.ANNOUNCEMENT_SOURCE_URL
import com.nekosu.data.settings.AnnouncementPrefs
import com.nekosu.data.settings.RemoteAnnouncement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Remote announcement popup, mounted once at the root layer.
 *
 * - cold start only (`freshStart`);
 * - "never show" disables it;
 * - fetches [ANNOUNCEMENT_SOURCE_URL] on a background thread, caches the payload,
 *   and shows a new `id` exactly once; network failures are silent.
 */
@Composable
fun AnnouncementPopup(freshStart: Boolean) {
    if (!freshStart) return

    val settings = koinInject<AppSettingsRepository>()
    if (AnnouncementPrefs.neverShow(settings)) return

    var announcement: RemoteAnnouncement? by remember {
        mutableStateOf(AnnouncementPrefs.cached(settings))
    }

    LaunchedEffect(Unit) {
        val remote = withContext(Dispatchers.IO) { AnnouncementPrefs.fetch(ANNOUNCEMENT_SOURCE_URL) }
        if (remote != null) {
            AnnouncementPrefs.saveCache(settings, remote)
            announcement = remote
        }
    }

    val current = announcement ?: return
    val key = current.id
    if (AnnouncementPrefs.seenKey(settings) == key) return

    val confirm = stringResource(R.string.personalize_announcement_got_it)
    val dialog = rememberConfirmDialog(
        onConfirm = { AnnouncementPrefs.markSeen(settings, key) },
        onDismiss = { AnnouncementPrefs.markSeen(settings, key) },
    )
    LaunchedEffect(key) {
        dialog.showConfirm(
            title = current.title,
            content = current.content,
            markdown = true,
            confirm = confirm,
        )
    }
}
