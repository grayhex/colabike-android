package ru.colabike.core.auth

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens a sign-in page in the system browser (Custom Tabs, never a WebView): the password and the
 * provider's cookies stay with the browser, the app only gets the one-time code back.
 */
object CustomTabs {
    fun open(context: Context, url: String) {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
    }
}
