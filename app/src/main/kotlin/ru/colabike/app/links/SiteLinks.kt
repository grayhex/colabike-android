package ru.colabike.app.links

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.net.toUri
import ru.colabike.core.auth.CustomTabs

/**
 * Pages of the site that the app sends people to because API v1 has no native way to do the same
 * (registration, password recovery, e-mail and account management, the legal texts). They open in
 * the system browser; the app never hands them a token (cola docs/modules/accounts.md).
 */
class SiteLinks(siteUrl: String) {
    private val base = siteUrl.trimEnd('/')

    val register = "$base/register"
    val forgotPassword = "$base/forgot-password"
    val account = "$base/account?tab=account"
    val terms = "$base/legal/terms"
    val privacy = "$base/legal/privacy"

    /**
     * The address of a public bike to share. API v1 gives no public id, so this is the permanent
     * `/b/<uuid>` form, which the site redirects (308) to the canonical `/b/<slug>-<id>`; the
     * canonical address needs the server to expose it (a gap, ADR 0003).
     */
    fun bike(uuid: String): String = "$base/b/$uuid"

    /** A page of the site from known-good path parts (never from a raw incoming string). */
    fun page(vararg segments: String): String = "$base/" + segments.joinToString("/")

    /** A page by an already encoded path that the parser has taken from an allowlisted address. */
    fun pageAt(encodedPath: String): String = base + encodedPath
}

/**
 * Opens a page outside the app. A fake in tests, so a click is observable and no browser starts.
 */
fun interface LinkOpener {
    fun open(url: String)
}

val LocalLinkOpener = staticCompositionLocalOf { LinkOpener {} }

/** Custom Tabs for `https` pages only; if no browser can show it, the click does nothing. */
class CustomTabsLinkOpener(private val context: Context) : LinkOpener {
    override fun open(url: String) {
        if (url.toUri().scheme != "https") return
        runCatching { CustomTabs.open(context, url) }
    }
}
