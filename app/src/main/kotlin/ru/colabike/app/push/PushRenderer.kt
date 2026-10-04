package ru.colabike.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.ColaIcons

/**
 * The one notification of a push, built here from the envelope (the transport carries no ready
 * notification, so nothing else shows it). A stack is one notification: the group of the envelope,
 * for the account, is its tag, so news of the same object replaces the earlier and two rides stay
 * apart; a replacement does not ring again. The lock screen shows a general text, not the title and
 * the body (the page of a private conversation or a ride must not be read over a shoulder). The
 * system removes it itself when it expires. The tap goes to an activity that is not exported.
 */
class PushRenderer(private val context: Context, private val clock: Clock = Clock.systemUTC()) :
    PushSurface {
    private val manager = NotificationManagerCompat.from(context)

    override fun show(envelope: PushEnvelope, account: String): Boolean {
        PushChannel.ensure(context)
        if (!manager.areNotificationsEnabled()) return false
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val channel = PushChannel.of(envelope.category)
        val tag = tagOf(account, envelope.group)
        val replacing = manager.activeNotifications.any { it.tag == tag }
        val tap =
            PendingIntent.getActivity(
                context,
                tag.hashCode(),
                PushTap(envelope.eventId, envelope.type, envelope.target).intent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val general =
            NotificationCompat.Builder(context, channel.id)
                .setSmallIcon(ColaIcons.Notifications)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.push_public_text))
                .build()
        val remaining = Duration.between(Instant.now(clock), envelope.expiresAt)
        val notification =
            NotificationCompat.Builder(context, channel.id)
                .setSmallIcon(ColaIcons.Notifications)
                .setContentTitle(envelope.title)
                .setContentText(envelope.body)
                .apply {
                    envelope.body?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
                }
                .setWhen(envelope.createdAt.toEpochMilli())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setCategory(categoryOf(channel))
                .setOnlyAlertOnce(replacing)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(general)
                .setTimeoutAfter(remaining.toMillis().coerceAtLeast(1))
                .build()
        manager.notify(tag, NOTIFICATION_ID, notification)
        return true
    }

    override fun cancelAll() = manager.cancelAll()

    private fun categoryOf(channel: PushChannel): String =
        when (channel) {
            PushChannel.Chat -> NotificationCompat.CATEGORY_MESSAGE
            PushChannel.Rides,
            PushChannel.Plans,
            PushChannel.Nearby -> NotificationCompat.CATEGORY_EVENT
            PushChannel.Discussions -> NotificationCompat.CATEGORY_SOCIAL
            PushChannel.General -> NotificationCompat.CATEGORY_RECOMMENDATION
        }

    companion object {
        private const val NOTIFICATION_ID = 1

        /**
         * The tag of a stack: the account and the envelope's group, so accounts never share one.
         */
        fun tagOf(account: String, group: String) = "$account|$group"
    }
}
