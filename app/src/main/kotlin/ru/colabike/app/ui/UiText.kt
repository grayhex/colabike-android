package ru.colabike.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import kotlin.math.ceil
import ru.colabike.app.R
import ru.colabike.core.model.DataError

/** Text for the user, chosen in a ViewModel and resolved in composition. */
@Immutable
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** A message the server wrote for people (already in Russian). */
    data class Plain(val text: String) : UiText
}

@Composable
fun UiText.resolve(): String =
    when (this) {
        is UiText.Res -> stringResource(id, *args.toTypedArray())
        is UiText.Plain -> text
    }

/** One mapping from failures to words for every screen. */
fun DataError.toUiText(): UiText =
    when (this) {
        is DataError.Offline -> UiText.Res(R.string.error_offline)
        is DataError.NotFound -> UiText.Res(R.string.error_not_found)
        is DataError.RateLimited ->
            retryAfterSeconds?.let {
                UiText.Res(
                    R.string.error_rate_limited,
                    listOf(ceil(it / 60.0).toInt().coerceAtLeast(1)),
                )
            } ?: UiText.Res(R.string.error_rate_limited_soon)
        is DataError.Server ->
            requestId?.let { UiText.Res(R.string.error_server, listOf(it)) }
                ?: UiText.Res(R.string.error_server_plain)
        is DataError.Rejected ->
            when {
                code == "invalid_credentials" -> UiText.Res(R.string.login_wrong_credentials)
                code == "email_verification_required" ->
                    UiText.Res(R.string.error_email_verification)
                userMessage.isNotBlank() -> UiText.Plain(userMessage)
                // No words from the server: the code and the request id are what support asks for.
                else ->
                    UiText.Res(
                        R.string.error_rejected_code,
                        listOf(listOfNotNull(status, code, requestId).joinToString(" ")),
                    )
            }
        is DataError.SignedOut,
        is DataError.Unexpected -> UiText.Res(R.string.error_unexpected)
    }
