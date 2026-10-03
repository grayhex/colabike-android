package ru.colabike.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf

/** A MockWebServer posing as the site, with the app's real client and API classes. */
class TestServer : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val config =
        ApiConfig(siteUrl = server.url("/").toString().trimEnd('/'), appVersion = "0.1.0-test")
    val client = HttpClients.base(config)
    val api = ColaBikeApi(config, client)
    val media = MediaUrls(config.siteUrl)

    fun json(status: Int, body: String, vararg headers: String) =
        server.enqueue(
            MockResponse.Builder()
                .code(status)
                .headers(headersOf("Content-Type", "application/json", *headers))
                .body(body)
                .build()
        )

    fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "no fixture $name" }.readText()

    override fun close() = server.close()
}

fun error(code: String, message: String = "Сообщение для людей") =
    """{"error":{"code":"$code","message":"$message"}}"""
