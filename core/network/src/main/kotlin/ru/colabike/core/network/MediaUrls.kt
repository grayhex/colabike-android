package ru.colabike.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Absolute addresses for media paths of the API. Photos come as site-relative paths; the app only
 * loads http(s) URLs, so anything else (a `javascript:` value, garbage) becomes null.
 */
class MediaUrls(siteUrl: String) {
    private val site: HttpUrl = siteUrl.toHttpUrl()

    fun resolve(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return site.resolve(path.trim())?.toString()
    }

    /**
     * An address on the site itself and nowhere else, for content the server manages remotely (the
     * pictures of the app config): [path] must begin with one slash and join into an address of the
     * site's scheme, host and port, with no credentials and no `.` or `..` segment. [width] picks a
     * size variant (`?width=`).
     */
    fun resolveOnSite(path: String?, width: Int? = null): String? {
        val raw = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith("/") || raw.startsWith("//")) return null
        if (raw.any { it.isISOControl() || it == '\\' || it.isWhitespace() }) return null
        // A path that climbs is not one the server writes; it is not followed to wherever it lands.
        if (raw.substringBefore('?').split('/').any { it == ".." || it == "." }) return null
        val url = site.resolve(raw) ?: return null
        val sameSite =
            url.scheme == site.scheme &&
                url.host.equals(site.host, ignoreCase = true) &&
                url.port == site.port &&
                url.username.isEmpty() &&
                url.password.isEmpty()
        if (!sameSite) return null
        return (if (width == null) url
            else url.newBuilder().setQueryParameter("width", "$width").build())
            .toString()
    }
}
