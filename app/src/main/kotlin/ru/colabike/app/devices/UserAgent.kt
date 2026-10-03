package ru.colabike.app.devices

/** A browser session named for people: "Chrome" on "Windows". Either part may be unknown. */
data class BrowserInfo(val browser: String?, val system: String?) {
    /** "Chrome · Windows", "Firefox", or null when nothing could be told from the header. */
    val label: String?
        get() = listOfNotNull(browser, system).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Names the browser and the system from a `User-Agent`. The header is a pile of historical claims
 * (every browser says "Mozilla" and most say "Safari"), so the specific names are tried before the
 * generic ones. Unknown strings give an empty [BrowserInfo], never a guess.
 */
fun describeUserAgent(userAgent: String): BrowserInfo {
    val ua = userAgent
    val browser =
        when {
            "Edg/" in ua || "EdgA/" in ua || "EdgiOS/" in ua -> "Edge"
            "OPR/" in ua || "Opera" in ua -> "Opera"
            "YaBrowser/" in ua -> "Яндекс Браузер"
            "SamsungBrowser/" in ua -> "Samsung Internet"
            "Firefox/" in ua || "FxiOS/" in ua -> "Firefox"
            "Chrome/" in ua || "CriOS/" in ua -> "Chrome"
            "Safari/" in ua && "Version/" in ua -> "Safari"
            else -> null
        }
    val system =
        when {
            "Android" in ua -> "Android"
            "iPhone" in ua || "iPad" in ua || "iPod" in ua -> "iOS"
            "Windows" in ua -> "Windows"
            "Macintosh" in ua || "Mac OS X" in ua -> "macOS"
            "CrOS" in ua -> "ChromeOS"
            "Linux" in ua || "X11" in ua -> "Linux"
            else -> null
        }
    return BrowserInfo(browser, system)
}
