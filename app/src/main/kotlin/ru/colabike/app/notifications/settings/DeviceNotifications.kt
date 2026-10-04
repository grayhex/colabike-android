package ru.colabike.app.notifications.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import ru.colabike.app.push.PushAvailability
import ru.colabike.app.push.PushChannel
import ru.colabike.app.push.PushProvider

/**
 * Whether this phone lets the app show notifications. Android's own permission is the phone's, not
 * the account's: the server can neither grant nor read it.
 */
enum class OsPermission {
    /**
     * Before Android 13 there is no runtime permission; the app's switch in the system settings.
     */
    NotNeeded,
    Granted,

    /** Not asked yet: the person may be asked, after being told why. */
    NotAsked,

    /** Asked and refused: asking again does nothing, only the system settings change it. */
    Denied,
}

/**
 * The state of this phone alone, apart from the account's settings: the permission, the switch of
 * the app and of each channel in the system settings, and the provider that would deliver push.
 */
data class DeviceNotificationsState(
    val permission: OsPermission,
    /** The system's switch for all notifications of the app (the permission, on Android 13+). */
    val appEnabled: Boolean,
    /** Channels the person turned off in the system settings. */
    val channelsOff: List<PushChannel>,
    val provider: PushAvailability,
) {
    /** Notifications can reach the person on this phone, whatever the provider says. */
    val canShow: Boolean
        get() =
            appEnabled && permission != OsPermission.Denied && permission != OsPermission.NotAsked
}

/** The phone's side of notifications, behind an interface so tests and previews fake it. */
interface DeviceNotifications {
    suspend fun state(): DeviceNotificationsState

    /** Remembers that the system's question was put, so that a refusal is told from "not asked". */
    fun markAsked()

    /**
     * The system's page of this app's notifications; its intent is built by the caller's context.
     */
    fun settingsIntent(): Intent

    /** The system's page of one channel. */
    fun channelIntent(channel: PushChannel): Intent
}

/** [DeviceNotifications] on the real phone. Nothing here leaves the device. */
class AndroidDeviceNotifications(
    private val context: Context,
    private val provider: PushProvider,
    private val preferences: SharedPreferences,
) : DeviceNotifications {
    override suspend fun state(): DeviceNotificationsState {
        val manager = NotificationManagerCompat.from(context)
        val enabled = manager.areNotificationsEnabled()
        val permission =
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> OsPermission.NotNeeded
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> OsPermission.Granted
                preferences.getBoolean(KEY_ASKED, false) -> OsPermission.Denied
                else -> OsPermission.NotAsked
            }
        val off =
            PushChannel.entries.filter { channel ->
                manager.getNotificationChannel(channel.id)?.importance ==
                    android.app.NotificationManager.IMPORTANCE_NONE
            }
        return DeviceNotificationsState(permission, enabled, off, provider.availability())
    }

    override fun markAsked() = preferences.edit { putBoolean(KEY_ASKED, true) }

    override fun settingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    override fun channelIntent(channel: PushChannel): Intent =
        PushChannel.settingsIntent(context, channel)

    private companion object {
        const val KEY_ASKED = "post_notifications_asked"
    }
}
