package ru.colabike.app.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.links.PendingNavigation
import ru.colabike.core.auth.AuthState
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationsRepository

/**
 * What a tap on a notification does. The destination goes to the app's pending navigation: the
 * shell takes it when it is on screen, after sign-in if need be. Opening a notification is what
 * reads it, so the mark goes to the server once the session is known; a session that is not there
 * (signed out, a failure) leaves it unread, and the tap is not retried in the dark.
 */
class PushOpener(
    private val pending: PendingNavigation,
    private val notifications: NotificationsRepository,
    private val auth: AuthActions,
    private val scope: CoroutineScope,
    private val waitForSessionMs: Long = 30_000,
) {
    fun opened(tap: PushTap) {
        pending.offer(tap.destination())
        scope.launch {
            val state =
                withTimeoutOrNull(waitForSessionMs) {
                    auth.state.first { it !is AuthState.Restoring }
                }
            if (state is AuthState.SignedIn) {
                try {
                    notifications.markRead(tap.eventId)
                } catch (_: DataError) {
                    // Still unread: the inbox shows it as new and the next opening marks it.
                }
            }
        }
    }
}
