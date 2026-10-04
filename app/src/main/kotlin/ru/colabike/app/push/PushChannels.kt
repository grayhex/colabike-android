package ru.colabike.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import ru.colabike.app.R

/**
 * The permanent system channels: what the person switches in Android's own settings, one row each.
 * The names are the ones the settings of the account use. A channel is created once and then left
 * alone: its importance and sound are the person's choice and are never reset by creating it again.
 */
enum class PushChannel(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val importance: Int,
) {
    /** Invitations, changes and answers on the person's own rides and plans. */
    Rides(
        "rides",
        R.string.push_channel_rides,
        R.string.push_channel_rides_desc,
        NotificationManager.IMPORTANCE_HIGH,
    ),

    /** New plans of friends. */
    Plans(
        "plans",
        R.string.push_channel_plans,
        R.string.push_channel_plans_desc,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),

    /** Rides that fit an area the person chose. */
    Nearby(
        "nearby",
        R.string.push_channel_nearby,
        R.string.push_channel_nearby_desc,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),

    /** Messages of the chat. */
    Chat(
        "chat",
        R.string.push_channel_chat,
        R.string.push_channel_chat_desc,
        NotificationManager.IMPORTANCE_HIGH,
    ),

    /** Comments and replies. */
    Discussions(
        "discussions",
        R.string.push_channel_discussions,
        R.string.push_channel_discussions_desc,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),

    /** A category the app does not know yet: it is shown, in a channel of its own. */
    General(
        "general",
        R.string.push_channel_general,
        R.string.push_channel_general_desc,
        NotificationManager.IMPORTANCE_LOW,
    );

    companion object {
        /** The channel for a category key as the server writes it; an unknown key is [General]. */
        fun of(category: String): PushChannel =
            when (category) {
                "rides" -> Rides
                "plans",
                "intents" -> Plans
                "nearby" -> Nearby
                "chat" -> Chat
                "discussions" -> Discussions
                else -> General
            }

        /**
         * Creates the channels that are not there yet; the ones that are keep the person's choices.
         */
        fun ensure(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.notificationChannels.map { it.id }.toSet()
            entries
                .filter { it.id !in existing }
                .forEach { channel ->
                    manager.createNotificationChannel(
                        NotificationChannel(
                                channel.id,
                                context.getString(channel.title),
                                channel.importance,
                            )
                            .apply { description = context.getString(channel.description) }
                    )
                }
        }

        /** The system's page of one channel, for a link from the app's own settings. */
        fun settingsIntent(context: Context, channel: PushChannel): Intent =
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
    }
}
