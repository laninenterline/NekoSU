package com.nekosu.data.module

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import java.net.URI
import com.nekosu.R
import com.nekosu.data.AppSettingsRepository
import com.nekosu.domain.model.RepositoryApiType
import com.nekosu.domain.model.RepositoryError
import com.nekosu.domain.model.RepositoryException
import com.nekosu.domain.model.RepositorySource

class RepositorySourceStore(
    private val settings: AppSettingsRepository,
    private val context: Context,
) {
    /**
     * The build-time registry source. NekoSU builds may point [RepositorySource.KERNEL_SU_URL]
     * at their own registry; then the display name follows that host instead of the official
     * KernelSU label.
     */
    private fun builtInSource(): RepositorySource {
        val url = RepositorySource.KERNEL_SU_URL
        val official = url.trimEnd('/').equals(RepositorySource.OFFICIAL_KERNEL_SU_URL, ignoreCase = true)
        val host = runCatching { URI(url.trim()).host }.getOrNull()?.takeIf(String::isNotBlank)
        return RepositorySource(
            url = url,
            name = if (official) {
                context.getString(R.string.repo_kernel_su)
            } else {
                host ?: context.getString(R.string.repo_registry_builtin)
            },
            description = context.getString(
                if (official) R.string.repo_kernel_su_description else R.string.repo_registry_builtin_description,
            ),
        )
    }

    fun load(): List<RepositorySource> {
        // NekoSU's built-in registry is optional: an empty NEKOSU_REGISTRY_URL disables it,
        // users can then only add their own repositories below.
        val builtIn: List<RepositorySource> = if (RepositorySource.isBuiltInRegistryEnabled) {
            listOf(builtInSource())
        } else {
            emptyList()
        }
        val saved = runCatching {
            JsonParser.parseString(settings.getString(KEY) ?: "[]").asJsonArray.mapNotNull { item ->
                runCatching {
                    val fields = item.asJsonObject
                    RepositorySource(
                        url = MmrlRepositoryParser.canonicalUrl(fields.text("url")),
                        name = fields.text("name"),
                        id = fields.text("id"),
                        description = fields.text("description"),
                        customName = fields.text("customName").trim().take(100),
                        moduleCount = fields.get("moduleCount")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                            ?.asString?.toIntOrNull()?.takeIf { it >= 0 },
                        apiType = fields.text("apiType").takeIf(String::isNotBlank)?.let(RepositoryApiType::valueOf)
                            ?: RepositoryApiType.MMRL,
                    ).takeIf { !it.isBuiltIn && it.name.isNotBlank() }
                }.getOrNull()
            }.distinctBy { it.url }
        }.getOrDefault(emptyList())
        return builtIn + saved
    }

    suspend fun save(sources: List<RepositorySource>) {
        try {
            val array = JsonArray()
            sources.filterNot { it.isBuiltIn }.forEach { source ->
                array.add(
                    JsonObject().apply {
                        addProperty("url", source.url)
                        addProperty("name", source.name)
                        addProperty("customName", source.customName)
                        addProperty("apiType", source.apiType.name)
                        addProperty("id", source.id)
                        addProperty("description", source.description)
                        source.moduleCount?.let { addProperty("moduleCount", it) }
                    },
                )
            }
            settings.editBlocking {
                it[stringPreferencesKey(KEY)] = array.toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw RepositoryException(RepositoryError.STORAGE)
        }
    }

    private fun JsonElement?.text(): String = this?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString.orEmpty()
    private fun JsonObject.text(key: String) = get(key).text()

    private companion object {
        const val KEY = "module_repository_sources"
    }
}
