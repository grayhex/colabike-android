package ru.colabike.core.network

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.PersonalApi
import ru.colabike.api.models.NotificationSettings as SettingsDto
import ru.colabike.api.models.NotificationSettingsPatch
import ru.colabike.api.models.NotificationSettingsPatchCategoriesInner
import ru.colabike.api.models.NotificationSettingsPatchChannels
import ru.colabike.api.models.NotificationSettingsPatchChannelsEmail
import ru.colabike.api.models.NotificationSettingsPatchCircle
import ru.colabike.api.models.NotificationSettingsPatchMutes
import ru.colabike.api.models.NotificationSettingsPatchMutesAddInner
import ru.colabike.api.models.NotificationSettingsPatchQuietHours
import ru.colabike.core.model.AccountChannels
import ru.colabike.core.model.CategorySetting
import ru.colabike.core.model.ChannelFlag
import ru.colabike.core.model.CircleMode
import ru.colabike.core.model.MuteKind
import ru.colabike.core.model.MuteRef
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationMute
import ru.colabike.core.model.NotificationSettings
import ru.colabike.core.model.NotificationSettingsChange
import ru.colabike.core.model.NotificationSettingsRepository
import ru.colabike.core.model.QuietHours

/**
 * The account's notification settings, as the site has them. A change names only what it changes
 * (the server keeps the rest), and the answer is the settings as they are afterwards, so what is
 * shown is what the server holds, never a local guess.
 */
class NetworkNotificationSettingsRepository(
    private val api: PersonalApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotificationSettingsRepository {
    override suspend fun settings(): NotificationSettings =
        apiCall(dispatcher) { api.getNotificationSettings() }.toModel(media)

    override suspend fun change(change: NotificationSettingsChange): NotificationSettings {
        if (change.isEmpty) return settings()
        val patch = change.toPatch()
        return apiCall(dispatcher) { api.updateNotificationSettings(patch) }.toModel(media)
    }
}

/**
 * The request body for a change. Nothing is sent that was not named: the generated client leaves a
 * null out of the body, which is what "unchanged" means here (and lifting a pause is `resume`,
 * never a null).
 */
internal fun NotificationSettingsChange.toPatch(): NotificationSettingsPatch {
    fun email(on: Boolean?) = on?.let { NotificationSettingsPatchChannelsEmail(enabled = it) }
    val channels =
        if (emailEnabled != null || pushEnabled != null)
            NotificationSettingsPatchChannels(
                email = email(emailEnabled),
                push = email(pushEnabled),
            )
        else null
    val categories =
        (categoryPush.keys + categoryEmail.keys)
            .sorted()
            .mapNotNull { key ->
                NotificationSettingsPatchCategoriesInner.Key.entries
                    .firstOrNull { it.value == key }
                    ?.takeIf {
                        it != NotificationSettingsPatchCategoriesInner.Key.unknown_default_open_api
                    }
                    ?.let { known ->
                        NotificationSettingsPatchCategoriesInner(
                            key = known,
                            email = categoryEmail[key],
                            push = categoryPush[key],
                        )
                    }
            }
            .ifEmpty { null }
    val quiet =
        if (
            quietEnabled != null ||
                quietFrom != null ||
                quietTo != null ||
                quietAllowCancellations != null
        )
            NotificationSettingsPatchQuietHours(
                enabled = quietEnabled,
                from = quietFrom?.clock(),
                to = quietTo?.clock(),
                allowCancellations = quietAllowCancellations,
            )
        else null
    val circle =
        if (circleMode != null || circleAdd.isNotEmpty() || circleRemove.isNotEmpty())
            NotificationSettingsPatchCircle(
                mode =
                    circleMode
                        ?.takeIf { it != CircleMode.Unknown }
                        ?.let { mode ->
                            NotificationSettingsPatchCircle.Mode.entries.first {
                                it.value == mode.key
                            }
                        },
                add = circleAdd.mapNotNull { it.uuid() }.ifEmpty { null },
                remove = circleRemove.mapNotNull { it.uuid() }.ifEmpty { null },
            )
        else null
    val mutes =
        if (muteAdd.isNotEmpty() || muteRemove.isNotEmpty())
            NotificationSettingsPatchMutes(
                add = muteAdd.mapNotNull { it.toDto() }.ifEmpty { null },
                remove = muteRemove.mapNotNull { it.toDto() }.ifEmpty { null },
            )
        else null
    return NotificationSettingsPatch(
        channels = channels,
        categories = categories,
        reminders = reminders,
        timeZone = timeZone?.id,
        quietHours = quiet,
        pausedUntil = pauseUntil?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
        resume = if (resume) true else null,
        circle = circle,
        considering = considering,
        mutes = mutes,
    )
}

private fun LocalTime.clock(): String = "%02d:%02d".format(hour, minute)

private fun String.uuid(): UUID? =
    try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun MuteRef.toDto(): NotificationSettingsPatchMutesAddInner? {
    val known =
        NotificationSettingsPatchMutesAddInner.Kind.entries.firstOrNull { it.value == kind.key }
            ?: return null
    val uuid = id.uuid() ?: return null
    return NotificationSettingsPatchMutesAddInner(kind = known, id = uuid)
}

internal fun SettingsDto.toModel(media: MediaUrls): NotificationSettings =
    NotificationSettings(
        channels =
            AccountChannels(
                emailAvailable = channels.email.available,
                emailVerified = channels.email.verified,
                emailEnabled = channels.email.enabled,
                pushAvailable = channels.push.available,
                pushEnabled = channels.push.enabled,
            ),
        categories =
            categories.map {
                CategorySetting(
                    category = NotificationCategory.of(it.key),
                    key = it.key,
                    label = it.label,
                    email = ChannelFlag(it.email.supported, it.email.enabled),
                    push = ChannelFlag(it.push.supported, it.push.enabled),
                )
            },
        reminders = reminders,
        timeZone = timeZone?.let { zone -> zone.toZoneOrNull() },
        quietHours =
            QuietHours(
                enabled = quietHours.enabled,
                from = quietHours.from.toLocalTimeOr(DEFAULT_FROM),
                to = quietHours.to.toLocalTimeOr(DEFAULT_TO),
                allowCancellations = quietHours.allowCancellations,
            ),
        pausedUntil = pausedUntil?.let { Instant.from(it) },
        circleMode = CircleMode.of(circle.mode.value),
        circleMembers = circle.members.map { it.toModel(media) },
        considering = considering,
        mutes =
            mutes.map {
                NotificationMute(
                    MuteKind.of(it.kind.value),
                    it.id,
                    it.label?.takeIf { l -> l.isNotBlank() },
                )
            },
        updatedAt = updatedAt?.let { Instant.from(it) },
    )

private val DEFAULT_FROM: LocalTime = LocalTime.of(22, 0)
private val DEFAULT_TO: LocalTime = LocalTime.of(7, 0)

private fun String.toZoneOrNull(): ZoneId? =
    try {
        ZoneId.of(this)
    } catch (_: DateTimeException) {
        null
    }

private fun String.toLocalTimeOr(default: LocalTime): LocalTime =
    try {
        LocalTime.parse(this)
    } catch (_: DateTimeException) {
        default
    }
