package ru.colabike.core.network

/** Where and as which app the client talks to the API. Built from the app's BuildConfig. */
data class ApiConfig(
    /** Site origin, e.g. `https://colabike.ru`; the API lives under `/api/v1`. */
    val siteUrl: String,
    /** `versionName` of the app, sent as the device's `appVersion` and in the User-Agent. */
    val appVersion: String,
) {
    val apiBaseUrl: String
        get() = siteUrl.trimEnd('/') + "/api/v1"

    val userAgent: String
        get() = "ColaBike-Android/$appVersion"
}
