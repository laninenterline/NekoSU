package com.nekosu.data.module.registry

import java.net.URI

object RegistryUrls {

    /**
     * Site root of the module registry. Configured by NekoSU at build time via
     * `NEKOSU_REGISTRY_URL`; an empty value disables the built-in registry.
     */
    val DEFAULT_BASE_URL: String get() = com.nekosu.BuildConfig.MODULE_REGISTRY_URL

    const val INDEX_PATH = "api/v1/index.json"

    private val API_PATH = Regex("^[A-Za-z0-9._\\-/]+$")

    /** Ensures an https base URL ending with '/'. */
    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()) {
            "Registry base URL must be an https URL: $baseUrl"
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /** Same rule as `ApiPath` in api.ts: relative, no "..", restricted charset. */
    fun isValidApiPath(path: String): Boolean = path.isNotEmpty() && API_PATH.matches(path) && !path.startsWith("/") && !path.contains("..")

    /** Resolves a document path against the site root. Throws on unsafe paths. */
    fun resolve(baseUrl: String, path: String): String {
        if (!isValidApiPath(path)) throw RegistryFormatException("Invalid path in registry document: $path")
        return normalizeBaseUrl(baseUrl) + path
    }

    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    /** An absolute https URL with an ASCII host and no user info (safe to hand to a browser). */
    fun isHttpsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.isAbsolute && !uri.isOpaque &&
            uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrEmpty() && uri.rawUserInfo == null
    }

    /** An absolute http(s) URL with a host and no user info (homepage / support links). */
    fun isWebUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.isAbsolute && !uri.isOpaque &&
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
            !uri.host.isNullOrEmpty() && uri.rawUserInfo == null
    }

    /** A link to a `#fragment` of the document that was loaded with [documentUrl] as its URL. */
    fun isInPageAnchor(url: String, documentUrl: String): Boolean {
        val hash = url.indexOf('#')
        if (hash <= 0 || hash == url.length - 1) return false
        return url.substring(0, hash) == documentUrl
    }

    /** Port with the scheme default filled in, or -1 for unknown schemes. */
    fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }

    /**
     * Same origin: scheme, host and (effective) port are equal. Hosts are compared as written
     * in ASCII; a non-ASCII (IDN) host does not parse as a server authority and never matches,
     * and a punycode host only matches the identical punycode host.
     */
    fun isSameOrigin(url: String, origin: String): Boolean {
        val a = runCatching { URI(url) }.getOrNull() ?: return false
        val b = runCatching { URI(origin) }.getOrNull() ?: return false
        val hostA = a.host ?: return false
        val hostB = b.host ?: return false
        if (a.scheme == null || b.scheme == null) return false
        return a.scheme.equals(b.scheme, ignoreCase = true) &&
            hostA.equals(hostB, ignoreCase = true) &&
            effectivePort(a) != -1 &&
            effectivePort(a) == effectivePort(b)
    }

    /**
     * Sub-resource rule of the restricted (registry) renderer: https GET from the registry
     * origin only. Applied to the request and to every redirect hop, so a same-origin URL can
     * not redirect to another host.
     */
    fun isAllowedRestrictedResource(url: String, registryOrigin: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return false
        return isSameOrigin(url, registryOrigin)
    }

    /**
     * Registry release downloads: any absolute https URL without user info is accepted.
     *
     * The registry document pins the archive's byte size and SHA-256, and the downloader
     * verifies both before the file is passed to `ksud`, so the host does not have to be
     * GitHub (self-hosted registries may serve ZIPs from their own server or CDN).
     */
    fun isAllowedReleaseDownloadUrl(url: String): Boolean = isHttpsUrl(url)

    /**
     * Checks one hop of a registry download (the initial request or any redirect target):
     * https only, no user info. Integrity comes from the pinned size + SHA-256, not the host,
     * so redirects to a self-hosted CDN are allowed.
     */
    fun isAllowedDownloadHop(url: String): Boolean = isHttpsUrl(url)
}
