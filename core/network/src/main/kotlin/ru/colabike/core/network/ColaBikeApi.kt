package ru.colabike.core.network

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
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
        BikesApi(config.apiBaseUrl, clientWithNulls(nulls))

    /** The journal for a `PATCH` that names [nulls] as `null`: a date, a mileage taken away. */
    fun journalWithNulls(nulls: Set<String>): JournalApi =
        JournalApi(config.apiBaseUrl, clientWithNulls(nulls))

    private fun clientWithNulls(nulls: Set<String>): OkHttpClient =
        client
            .newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val body = request.body
                if (nulls.isEmpty() || body == null) return@addInterceptor chain.proceed(request)
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
            .build()

    /**
     * Bikes for sending a picture: a call that may take minutes (the base client allows one, for a
     * page, and a phone's photo over a bad connection is more), a body that says how much of it has
     * left ([onProgress], 0 to 1) and the call itself handed over ([onCall]), so that a cancelled
     * upload really stops the transfer.
     */
    fun bikesUploading(onProgress: (Float) -> Unit, onCall: (Call) -> Unit): BikesApi =
        BikesApi(config.apiBaseUrl, uploadingClient(onProgress, onCall))

    /** The journal for sending a picture of an entry, as [bikesUploading]. */
    fun journalUploading(onProgress: (Float) -> Unit, onCall: (Call) -> Unit): JournalApi =
        JournalApi(config.apiBaseUrl, uploadingClient(onProgress, onCall))

    private fun uploadingClient(onProgress: (Float) -> Unit, onCall: (Call) -> Unit): OkHttpClient =
        client
            .newBuilder()
            .callTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                onCall(chain.call())
                val request = chain.request()
                val body = request.body
                chain.proceed(
                    if (body == null) request
                    else
                        request
                            .newBuilder()
                            .method(request.method, ProgressBody(body, onProgress))
                            .build()
                )
            }
            .build()

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
        const val UPLOAD_TIMEOUT_MINUTES = 5L
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

/** A request body that tells how much of it has been written to the connection. */
private class ProgressBody(
    private val delegate: RequestBody,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun isOneShot() = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1L)
        var written = 0L
        val counting =
            object : ForwardingSink(sink) {
                    override fun write(source: Buffer, byteCount: Long) {
                        super.write(source, byteCount)
                        written += byteCount
                        onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                .buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
