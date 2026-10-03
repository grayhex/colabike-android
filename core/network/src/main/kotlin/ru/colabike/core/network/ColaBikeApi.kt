package ru.colabike.core.network

import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import ru.colabike.api.apis.AccountApi
import ru.colabike.api.apis.BikesApi
import ru.colabike.api.apis.SessionsApi
import ru.colabike.api.infrastructure.Serializer

/**
 * The generated API v1 classes over one [OkHttpClient]. Generated code is never edited: what it
 * needs at runtime is configured here, once, before its JSON instance exists.
 */
class ColaBikeApi(config: ApiConfig, client: OkHttpClient) {
    init {
        configureGeneratedClient()
    }

    val sessions = SessionsApi(config.apiBaseUrl, client)
    val account = AccountApi(config.apiBaseUrl, client)
    val bikes = BikesApi(config.apiBaseUrl, client)

    private companion object {
        val configured = AtomicBoolean(false)

        // Strict request schemas reject `null` in place of an absent optional field, and the
        // generated serializer writes defaults; with explicitNulls = false a null field is left
        // out of the body (cola docs/modules/api-v1.md, "Клиенты").
        fun configureGeneratedClient() {
            if (configured.compareAndSet(false, true)) {
                Serializer.kotlinxSerializationJsonConfiguration = { explicitNulls = false }
            }
        }
    }
}
