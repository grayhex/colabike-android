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
