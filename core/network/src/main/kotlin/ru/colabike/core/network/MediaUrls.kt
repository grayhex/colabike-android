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
}
