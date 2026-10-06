package ru.colabike.core.network

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import ru.colabike.api.apis.AccountApi
import ru.colabike.api.apis.AppApi
import ru.colabike.api.apis.BikesApi
import ru.colabike.api.apis.ChatApi
import ru.colabike.api.apis.CommentsApi
import ru.colabike.api.apis.ComponentsApi
import ru.colabike.api.apis.JournalApi
import ru.colabike.api.apis.MarketApi
import ru.colabike.api.apis.PersonalApi
import ru.colabike.api.apis.PlanningApi
import ru.colabike.api.apis.RidesApi
import ru.colabike.api.apis.SafetyApi
import ru.colabike.api.apis.SearchApi
import ru.colabike.api.apis.SessionsApi
import ru.colabike.api.apis.UsersApi
import ru.colabike.api.infrastructure.Serializer

/**
 * The generated API v1 classes over one [OkHttpClient]. Generated code is never edited: what it
 * needs at runtime is configured here, once, before its JSON instance exists.
 */
class ColaBikeApi(private val config: ApiConfig, private val client: OkHttpClient) {
    init {
        configureGeneratedClient()
    }

    val sessions = SessionsApi(config.apiBaseUrl, client)
    val account = AccountApi(config.apiBaseUrl, client)
    val bikes = BikesApi(config.apiBaseUrl, client)
    val users = UsersApi(config.apiBaseUrl, client)
    val safety = SafetyApi(config.apiBaseUrl, client)
    val search = SearchApi(config.apiBaseUrl, client)
    val journal = JournalApi(config.apiBaseUrl, client)
    val personal = PersonalApi(config.apiBaseUrl, client)
    val planning = PlanningApi(config.apiBaseUrl, client)
    val comments = CommentsApi(config.apiBaseUrl, client)
    val rides = RidesApi(config.apiBaseUrl, client)
    val chat = ChatApi(config.apiBaseUrl, client)
    val components = ComponentsApi(config.apiBaseUrl, client)
    val market = MarketApi(config.apiBaseUrl, client)
    val app = AppApi(config.apiBaseUrl, client)

    /**
     * Comments with an `Idempotency-Key` on every request made through it. The contract describes
     * the header but does not declare it as a parameter, so the generated methods cannot send it; a
     * client derived for one key does (same pool, same dispatcher, same interceptors).
     */
    fun commentsWithKey(key: String): CommentsApi =
        CommentsApi(
            config.apiBaseUrl,
            client
                .newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header(IDEMPOTENCY_KEY, key).build())
                }
                .build(),
        )

    /**
     * Planning with an `Idempotency-Key` on every request made through it: the key is the identity
     * of the intention a create makes, and the contract does not declare it as a parameter.
     */
    fun planningWithKey(key: String): PlanningApi =
        PlanningApi(
            config.apiBaseUrl,
            client
                .newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header(IDEMPOTENCY_KEY, key).build())
                }
                .build(),
        )

    /**
     * Bikes for a `PATCH` that names [nulls] as `null`: a weight or a price taken away is `null` in
     * the body, and the generated serializer leaves a null out (see [configureGeneratedClient]), so
     * the body is completed here, once, in one place.
     */
    fun bikesWithNulls(nulls: Set<String>): BikesApi =
        BikesApi(
            config.apiBaseUrl,
            client
                .newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val body = request.body
                    if (nulls.isEmpty() || body == null)
                        return@addInterceptor chain.proceed(request)
                    val sent = Buffer().also { body.writeTo(it) }.readUtf8()
                    val whole = Json.parseToJsonElement(sent).jsonObject
                    val completed = JsonObject(whole + nulls.associateWith { JsonNull })
                    chain.proceed(
                        request
                            .newBuilder()
                            .method(
                                request.method,
                                completed.toString().toRequestBody(body.contentType()),
                            )
                            .build()
                    )
                }
                .build(),
        )

    /** The chat bridge with an `Idempotency-Key` on every request made through it. */
    fun chatWithKey(key: String): ChatApi =
        ChatApi(
            config.apiBaseUrl,
            client
                .newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header(IDEMPOTENCY_KEY, key).build())
                }
                .build(),
        )

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
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
