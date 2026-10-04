package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The chat SDK ships English only; `values/stream_chat_strings.xml` is its Russian text. A new
 * version of the SDK that adds a string would show English in the middle of the Russian screens, so
 * every string and plural the SDK declares must have the app's own twin.
 */
class StreamStringsTest {
    private val modules =
        listOf("io.getstream.chat.android.compose", "io.getstream.chat.android.ui.common")

    /** Declared by the SDK and not translatable, so not ours to override. */
    private val notTranslatable = setOf("stream_compose_message_list_thread_footnote")

    private fun names(type: String, owner: String): Set<String> =
        Class.forName("$owner.R\$$type").fields.map { it.name }.toSet()

    private fun check(type: String) {
        val ours = names(type, "ru.colabike.app")
        val missing =
            modules
                .flatMap { names(type, it) }
                .filter { it.startsWith("stream_") && it !in notTranslatable }
                .filter { it !in ours }
        assertThat(missing).isEmpty()
    }

    @Test fun `every string of the SDK has a Russian twin`() = check("string")

    @Test fun `every plural of the SDK has a Russian twin`() = check("plurals")
}
