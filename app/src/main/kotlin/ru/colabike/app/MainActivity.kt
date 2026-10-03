package ru.colabike.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.colabike.app.links.AppLinkParser
import ru.colabike.app.links.CustomTabsLinkOpener
import ru.colabike.app.links.LinkHandler
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.LocalSharer
import ru.colabike.app.links.SheetSharer
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** The only activity: edge-to-edge from the first frame, predictive back via Navigation 3. */
class MainActivity : ComponentActivity() {
    private val graph
        get() = (application as ColaBikeApplication).graph

    /**
     * Every address that reaches the activity goes through the allowlist of [AppLinkParser]; the
     * sign-in code to sign-in, the rest to the pending navigation (or the site).
     */
    private val linkHandler by lazy {
        LinkHandler(
            parser = AppLinkParser(BuildConfig.SITE_URL),
            site = graph.links,
            pending = graph.pending,
            onAuthLink = graph.auth::handleLink,
            onSite = CustomTabsLinkOpener(this)::open,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            // The person's own choice (system, light, dark) decides the palette, not only the
            // system.
            val mode by graph.settings.themeMode.collectAsStateWithLifecycle()
            val dark = mode.isDark(isSystemInDarkTheme())
            // The system bars follow the app's palette: dark icons on a light page and the reverse,
            // also when the app and the system disagree.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            val opener = remember { CustomTabsLinkOpener(this) }
            val sharer = remember { SheetSharer(this) }
            CompositionLocalProvider(
                LocalLinkOpener provides opener,
                LocalSharer provides sharer,
            ) {
                ColaBikeTheme(darkTheme = dark) { ColaBikeApp(graph) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Only the address is read; nothing else of the Intent is used or forwarded.
        if (intent?.action == Intent.ACTION_VIEW) linkHandler.handle(intent.dataString)
    }

    private companion object {
        // The scrims of the three-button navigation bar, as Compose's own default uses them.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
