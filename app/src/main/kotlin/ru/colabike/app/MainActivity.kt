package ru.colabike.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** The only activity: edge-to-edge from the first frame, predictive back via Navigation 3. */
class MainActivity : ComponentActivity() {
    private val graph
        get() = (application as ColaBikeApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { ColaBikeTheme { ColaBikeApp(graph) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString?.let(graph.auth::handleLink)
    }
}
