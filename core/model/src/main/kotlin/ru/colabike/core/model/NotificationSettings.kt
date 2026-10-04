package ru.colabike.core.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Whose new plans and intents a person is told about. The set is open: a mode this version does not
 * know is [Unknown], shown as it is and neither offered nor changed.
 */
enum class CircleMode(val key: String) {
    /** Mutual follows; the server's default. */
    Friends("friends"),

    /** Everyone the person follows. */
    Follows("follows"),

    /** The people the person picked; it is a choice for notifications, not a social tie. */
    Selected("selected"),

    /** Nobody. */
    Off("off"),
    Unknown("");

    companion object {
        fun of(key: String): CircleMode =
            entries.firstOrNull { it != Unknown && it.key == key } ?: Unknown

        /** The modes a person can choose between. */
        val Choices: List<CircleMode> = entries.filter { it != Unknown }
    }
}

/** What a mute is about: everything an author does, everything about a ride, or one discussion. */
enum class MuteKind(val key: String) {
    Author("author"),
    Ride("ride"),
    Discussion("discussion"),
    Unknown("");

    companion object {
        fun of(key: String): MuteKind =
            entries.firstOrNull { it != Unknown && it.key == key } ?: Unknown
    }
}

/** A switch of one channel in one category: whether the channel can carry it, and if it is on. */
data class ChannelFlag(val supported: Boolean, val enabled: Boolean)

/**
 * One category as the account has it. [label] is the server's wording, shown as it is, so that the
 * app and the site name a category alike; [category] is [NotificationCategory.Other] for a key this
 * version does not know (it is still listed, with its own switches).
 */
data class CategorySetting(
    val category: NotificationCategory,
    val key: String,
    val label: String,
    val email: ChannelFlag,
    val push: ChannelFlag,
)

/**
 * The account's channels. [pushAvailable] says whether the server can deliver push at all; the
 * permission and the registration of one phone are not here (the server cannot grant them).
 */
data class AccountChannels(
    val emailAvailable: Boolean,
    val emailVerified: Boolean,
    val emailEnabled: Boolean,
    val pushAvailable: Boolean,
    val pushEnabled: Boolean,
)

/**
 * While the window is open the channels that interrupt wait for its end. [allowCancellations] is
 * the person's own choice that a close, confirmed cancellation is not held back.
 */
data class QuietHours(
    val enabled: Boolean,
    val from: LocalTime,
    val to: LocalTime,
    val allowCancellations: Boolean,
)

/**
 * An author, a ride or a discussion the person has muted; [label] is null when it may not be named.
 */
data class NotificationMute(val kind: MuteKind, val id: String, val label: String?)

/**
 * The settings of the account, the same on the site and in the app. [pausedUntil] is null when no
 * pause is on; [timeZone] is null until it was said (quiet hours need it).
 */
data class NotificationSettings(
    val channels: AccountChannels,
    val categories: List<CategorySetting>,
    val reminders: Boolean,
    val timeZone: ZoneId?,
    val quietHours: QuietHours,
    val pausedUntil: Instant?,
    val circleMode: CircleMode,
    val circleMembers: List<Person>,
    val considering: Boolean,
    val mutes: List<NotificationMute>,
    val updatedAt: Instant?,
)

/** A mute to add or lift. */
data class MuteRef(val kind: MuteKind, val id: String)

/**
 * A change to the settings: only what is named changes, so two devices changing different switches
 * do not undo each other. An empty change asks for nothing.
 */
data class NotificationSettingsChange(
    val emailEnabled: Boolean? = null,
    val pushEnabled: Boolean? = null,
    /** Category key to the push switch. */
    val categoryPush: Map<String, Boolean> = emptyMap(),
    /** Category key to the e-mail switch. */
    val categoryEmail: Map<String, Boolean> = emptyMap(),
    val reminders: Boolean? = null,
    val timeZone: ZoneId? = null,
    val quietEnabled: Boolean? = null,
    val quietFrom: LocalTime? = null,
    val quietTo: LocalTime? = null,
    val quietAllowCancellations: Boolean? = null,
    /** Pause until this moment. */
    val pauseUntil: Instant? = null,
    /** Lift the pause. */
    val resume: Boolean = false,
    val circleMode: CircleMode? = null,
    val circleAdd: List<String> = emptyList(),
    val circleRemove: List<String> = emptyList(),
    val considering: Boolean? = null,
    val muteAdd: List<MuteRef> = emptyList(),
    val muteRemove: List<MuteRef> = emptyList(),
) {
    val isEmpty: Boolean
        get() = this == NotificationSettingsChange()
}
