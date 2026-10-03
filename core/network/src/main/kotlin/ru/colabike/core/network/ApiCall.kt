package ru.colabike.core.network

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import ru.colabike.api.infrastructure.ClientError
import ru.colabike.api.infrastructure.ClientException
import ru.colabike.api.infrastructure.Serializer
import ru.colabike.api.infrastructure.ServerError
import ru.colabike.api.infrastructure.ServerException
import ru.colabike.api.models.Error
import ru.colabike.core.model.DataError

/**
 * Runs one blocking call of the generated client off the main thread and maps every failure to
 * [DataError]: one place for error codes, `Retry-After` and `X-Request-ID`.
 */
suspend fun <T> apiCall(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: () -> T): T =
    withContext(dispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientException) {
            throw e.toDataError()
        } catch (e: ServerException) {
            val headers = (e.response as? ServerError<*>)?.headers.orEmpty()
            throw DataError.Server(e.statusCode, headers.first(REQUEST_ID))
        } catch (e: IOException) {
            throw DataError.Offline(e)
        } catch (e: SerializationException) {
            throw DataError.Unexpected(e)
        } catch (e: IllegalArgumentException) {
            throw DataError.Unexpected(e)
        } catch (e: UnsupportedOperationException) {
            throw DataError.Unexpected(e)
        }
    }

/** The API error of a 4xx answer: code and message, or null when the body is not one. */
data class ApiFailure(
    val status: Int,
    val code: String,
    val message: String,
    val requestId: String?,
)

fun ClientException.failure(): ApiFailure {
    val response = response as? ClientError<*>
    val error =
        (response?.body as? String)?.let { body ->
            runCatching {
                Serializer.kotlinxSerializationJson.decodeFromString(Error.serializer(), body)
            }
                .getOrNull()
        }
    return ApiFailure(
        status = statusCode,
        code = error?.error?.code ?: "unknown",
        message = error?.error?.message.orEmpty(),
        requestId = response?.headers.orEmpty().first(REQUEST_ID),
    )
}

internal fun ClientException.toDataError(): DataError {
    val failure = failure()
    val headers = (response as? ClientError<*>)?.headers.orEmpty()
    return when {
        failure.status == 401 && failure.code in SIGNED_OUT_CODES -> DataError.SignedOut()
        failure.status == 404 -> DataError.NotFound()
        failure.status == 429 ->
            DataError.RateLimited(retryAfterSeconds(headers.first(RETRY_AFTER)))
        else -> DataError.Rejected(failure.status, failure.code, failure.message)
    }
}

/** `Retry-After` in seconds; the API sends delta-seconds. An HTTP date is not expected. */
internal fun retryAfterSeconds(value: String?): Long? =
    value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

// OkHttp lower-cases header names in toMultimap(), which the generated client uses.
private fun Map<String, List<String>>.first(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

private const val REQUEST_ID = "x-request-id"
private const val RETRY_AFTER = "retry-after"

/** 401 codes after which only signing in again helps (token_expired is refreshed earlier). */
private val SIGNED_OUT_CODES = setOf("invalid_token", "unauthorized", "token_expired")
