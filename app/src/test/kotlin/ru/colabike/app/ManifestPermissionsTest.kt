package ru.colabike.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the merged manifest asks the person for: the map library and the messenger's SDK bring more
 * than the app wants (docs/adr/0009, docs/adr/0011).
 */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun requested(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()

    @Test
    fun `viewing a route asks for no location and no Wi-Fi state`() {
        val requested = requested()

        assertThat(requested).contains("android.permission.INTERNET")
        assertThat(requested)
            .containsNoneOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
            )
    }

    @Test
    fun `the app asks for notifications for its own push, and the messenger for nothing else`() {
        assertThat(requested()).contains("android.permission.POST_NOTIFICATIONS")
        assertThat(requested())
            .containsNoneOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.CAMERA",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.FOREGROUND_SERVICE",
            )
    }

    @Test
    fun `where a tap on a notification lands is not exported, and the exported activity is one`() {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_ACTIVITIES,
            )
        val byName = info.activities.orEmpty().associateBy { it.name }

        assertThat(byName.getValue("ru.colabike.app.push.NotificationTapActivity").exported)
            .isFalse()
        // Of the app's own activities only the main one is exported (the test manifest of the
        // Compose tooling adds an activity of its own, which is not in a release).
        assertThat(
                info.activities
                    .orEmpty()
                    .filter { it.exported && it.name.startsWith("ru.colabike.app.") }
                    .map { it.name }
            )
            .containsExactly("ru.colabike.app.MainActivity")
    }

    @Test
    fun `no component of the SDKs runs a service in the foreground or opens a preview`() {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_META_DATA,
            )

        val names =
            (info.services.orEmpty().map { it.name } +
                info.receivers.orEmpty().map { it.name } +
                info.activities.orEmpty().map { it.name } +
                info.providers.orEmpty().map { it.name })
        assertThat(names)
            .containsNoneOf(
                "androidx.work.impl.foreground.SystemForegroundService",
                "androidx.compose.ui.tooling.PreviewActivity",
                "io.getstream.chat.android.client.receivers.NotificationMessageReceiver",
                "io.getstream.android.push.delegate.AndroidPushDelegateProvider",
            )
        val startup =
            info.providers.orEmpty().first { it.name == "androidx.startup.InitializationProvider" }
        assertThat(startup.metaData?.keySet().orEmpty())
            .doesNotContain("io.getstream.android.push.permissions.PushPermissionsInitializer")
    }
}
