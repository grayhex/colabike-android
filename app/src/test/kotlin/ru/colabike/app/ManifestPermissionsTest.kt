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
 * than the app wants (docs/adr/0009, docs/adr/0011), and the one place it reads is the approximate
 * one (docs/adr/0018).
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
    fun `the only place the app can read is the approximate one, and it never asks for the background`() {
        val requested = requested()

        assertThat(requested).contains("android.permission.INTERNET")
        assertThat(requested).contains("android.permission.ACCESS_COARSE_LOCATION")
        assertThat(requested)
            .containsNoneOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.ACCESS_WIFI_STATE",
            )
    }

    @Test
    fun `a phone without location hardware can still install the app`() {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_CONFIGURATIONS,
            )
        val required =
            info.reqFeatures.orEmpty().filter {
                it.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED != 0
            }

        assertThat(required.mapNotNull { it.name })
            .containsNoneOf("android.hardware.location", "android.hardware.location.network")
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
    fun `the one service the app exports is where RuStore delivers, and it answers one action`() {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SERVICES,
            )
        val exported =
            info.services.orEmpty().filter { it.exported && it.name.startsWith("ru.colabike.app.") }

        assertThat(exported.map { it.name }).containsExactly("ru.colabike.app.push.ColaPushService")
        val resolved =
            context.packageManager.queryIntentServices(
                android.content
                    .Intent("ru.rustore.sdk.pushclient.MESSAGING_EVENT")
                    .setPackage(context.packageName),
                0,
            )
        assertThat(resolved.map { it.serviceInfo.name })
            .containsExactly("ru.colabike.app.push.ColaPushService")
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
