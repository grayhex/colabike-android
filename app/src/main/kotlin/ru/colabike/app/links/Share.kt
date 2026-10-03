package ru.colabike.app.links

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf

/** Offers a page to other apps through the system share sheet. A fake in tests. */
fun interface Sharer {
    /** [title] names the thing shared, [url] is the only address in the text. */
    fun share(title: String, url: String)
}

val LocalSharer = staticCompositionLocalOf { Sharer { _, _ -> } }

/**
 * The system share sheet. Only the text (the name and the address) leaves the app; the chooser is
 * an explicit one, so no app is picked for the person.
 */
class SheetSharer(private val context: Context) : Sharer {
    override fun share(title: String, url: String) {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            }
        val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}
