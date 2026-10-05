package ru.colabike.app.notifications.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalTime
import ru.colabike.app.AppDependencies

/**
 * Profile → Notifications. The phone is read again whenever the screen comes back, because the
 * person may have been to Android's own settings; the permission is asked for only after the screen
 * has said why, and the answer is not taken for granted.
 */
@Composable
fun NotificationSettingsRoute(
    dependencies: AppDependencies,
    onBack: () -> Unit,
    onOpenNearby: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel = viewModel {
        NotificationSettingsViewModel(
            repository = dependencies.notificationSettings,
            people = dependencies.people,
            device = dependencies.deviceNotifications,
            clock = dependencies.clock,
            pushSync = dependencies.pushSync,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshDevice()
        }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDevice()
        onPauseOrDispose {}
    }
    fun openSystemSettings() {
        try {
            context.startActivity(dependencies.deviceNotifications.settingsIntent())
        } catch (_: ActivityNotFoundException) {
            // A phone without that page: nothing to open, and the status already says what is
            // wrong.
        }
    }
    fun pickTime(current: LocalTime, picked: (LocalTime) -> Unit) {
        // The platform's own clock dialog: 24-hour, accessible, no extra code to keep.
        TimePickerDialog(
                context,
                { _, hour, minute -> picked(LocalTime.of(hour, minute)) },
                current.hour,
                current.minute,
                true,
            )
            .show()
    }
    val quiet = (state as? NotificationSettingsUiState.Loaded)?.settings?.quietHours
    val actions =
        remember(viewModel, quiet, onOpenNearby) {
            NotificationSettingsActions(
                onBack = onBack,
                onOpenNearby = onOpenNearby,
                onRetryLoad = viewModel::load,
                onRetryChange = viewModel::retry,
                onPush = viewModel::setPush,
                onEmail = viewModel::setEmail,
                onCategoryPush = viewModel::setCategoryPush,
                onCategoryEmail = viewModel::setCategoryEmail,
                onReminders = viewModel::setReminders,
                onQuietEnabled = viewModel::setQuietEnabled,
                onPickQuietFrom = {
                    pickTime(quiet?.from ?: LocalTime.of(22, 0), viewModel::setQuietFrom)
                },
                onPickQuietTo = {
                    pickTime(quiet?.to ?: LocalTime.of(7, 0), viewModel::setQuietTo)
                },
                onQuietCancellations = viewModel::setQuietCancellations,
                onUsePhoneZone = viewModel::usePhoneZone,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onCircle = viewModel::setCircle,
                onConsidering = viewModel::setConsidering,
                onAddMember = viewModel::addMember,
                onRemoveMember = viewModel::removeMember,
                onRemoveMute = viewModel::removeMute,
                onAskPermission = {
                    viewModel.permissionAsked()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenSystemSettings = ::openSystemSettings,
            )
        }
    NotificationSettingsScreen(state, actions)
}
