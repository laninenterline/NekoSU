package com.nekosu.data.settings

import com.nekosu.data.AppSettingsRepository
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Remote announcement payload. */
data class RemoteAnnouncement(
    val id: String,
    val title: String,
    val content: String,
    val url: String = "",
)

/**
 * Cold-start announcement state.
 *
 * Announcement text is fetched from [ANNOUNCEMENT_SOURCE_URL] on a background
 * thread and cached in settings. "Never show" disables it completely; otherwise
 * a new `id` is shown exactly once.
 */
object AnnouncementPrefs {

    const val KEY_NEVER_SHOW = "personalize_announcement_never_show"
    const val KEY_SEEN = "personalize_announcement_seen_key"
    private const val KEY_CACHE_ID = "personalize_announcement_cache_id"
    private const val KEY_CACHE_TITLE = "personalize_announcement_cache_title"
    private const val KEY_CACHE_CONTENT = "personalize_announcement_cache_content"
    private const val KEY_CACHE_URL = "personalize_announcement_cache_url"

    val ALL_KEYS: List<String> = listOf(
        KEY_NEVER_SHOW,
        KEY_SEEN,
        KEY_CACHE_ID,
        KEY_CACHE_TITLE,
        KEY_CACHE_CONTENT,
        KEY_CACHE_URL,
    )

    fun neverShow(settings: AppSettingsRepository): Boolean =
        settings.getBoolean(KEY_NEVER_SHOW, false)

    fun setNeverShow(settings: AppSettingsRepository, value: Boolean) {
        settings.putBoolean(KEY_NEVER_SHOW, value)
    }

    fun seenKey(settings: AppSettingsRepository): String =
        settings.getString(KEY_SEEN, "") ?: ""

    fun markSeen(settings: AppSettingsRepository, key: String) {
        settings.putString(KEY_SEEN, key)
    }

    fun cached(settings: AppSettingsRepository): RemoteAnnouncement? {
        val id = settings.getString(KEY_CACHE_ID, "") ?: ""
        val title = settings.getString(KEY_CACHE_TITLE, "") ?: ""
        val content = settings.getString(KEY_CACHE_CONTENT, "") ?: ""
        if (id.isBlank() || title.isBlank() || content.isBlank()) return null
        return RemoteAnnouncement(
            id = id,
            title = title,
            content = content,
            url = settings.getString(KEY_CACHE_URL, "") ?: "",
        )
    }

    fun saveCache(settings: AppSettingsRepository, announcement: RemoteAnnouncement) {
        settings.putString(KEY_CACHE_ID, announcement.id)
        settings.putString(KEY_CACHE_TITLE, announcement.title)
        settings.putString(KEY_CACHE_CONTENT, announcement.content)
        settings.putString(KEY_CACHE_URL, announcement.url)
    }

    private fun fetchJson(url: String): RemoteAnnouncement? {
        if (url.isBlank()) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NekoSU")
            }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(body)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    fun parse(body: String): RemoteAnnouncement? = try {
        val obj = JSONObject(body)
        val id = obj.optString("id").ifBlank { obj.optString("version") }
        val title = obj.optString("title")
        val content = obj.optString("content")
        if (id.isBlank() || title.isBlank() || content.isBlank()) {
            null
        } else {
            RemoteAnnouncement(
                id = id,
                title = title,
                content = content,
                url = obj.optString("url"),
            )
        }
    } catch (_: Throwable) {
        null
    }

    fun fetch(url: String): RemoteAnnouncement? = fetchJson(url)
}

/** Set this to a raw JSON URL, e.g. the repository's announcements.json. */
const val ANNOUNCEMENT_SOURCE_URL: String =
    "https://raw.githubusercontent.com/laninenterline/NekoSU/main/announcements.json"
