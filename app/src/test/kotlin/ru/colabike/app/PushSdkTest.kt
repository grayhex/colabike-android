package ru.colabike.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.rustore.sdk.pushclient.RuStorePushClient

/**
 * What the RuStore SDK brings with it and what it is allowed to do (docs/adr/0017): the app takes
 * its push delivery and nothing of its crash reporting, and starts it only when there is a reason.
 */
@RunWith(RobolectricTestRunner::class)
class PushSdkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the crash reporter the SDK would send its crashes through is not in the app`() {
        listOf(
                "ru.ok.tracer.lite.TracerLite",
                "ru.ok.tracer.lite.crash.report.TracerCrashReportLite",
                "ru.ok.tracer.base.http.HttpClient",
            )
            .forEach { name ->
                assertThrows(name, ClassNotFoundException::class.java) { Class.forName(name) }
            }
    }

    @Test
    fun `a build without the owner's project starts nothing, whatever the person chose`() {
        assertThat(BuildConfig.RUSTORE_PROJECT_ID).isEmpty()
        assertThat(RuStorePushClient.isInitialized).isFalse()
    }

    @Test
    fun `the manifest gives the SDK no project, the app has to start it`() {
        val info =
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )

        val keys = info.metaData?.keySet().orEmpty()
        assertThat(keys.filter { it.contains("rustore", ignoreCase = true) }).isEmpty()
        // The SDK's own note of the process it works in (the app's own name), and no project.
        assertThat(keys.filter { it.contains("vk.push", ignoreCase = true) })
            .containsExactly("com.vk.push.pushsdk.process_name")
        assertThat(info.metaData.getString("com.vk.push.pushsdk.process_name"))
            .isEqualTo(context.packageName)
    }

    @Test
    fun `the only exported receivers are the system's and the SDK's one answer to the host`() {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_RECEIVERS,
            )

        val exported =
            info.receivers
                .orEmpty()
                .filter { it.exported && it.name.startsWith("ru.") }
                .map { it.name }

        assertThat(exported)
            .containsExactly("ru.rustore.sdk.pushclient.internal.arbiter.ArbiterBroadcastReceiver")
        val answering =
            context.packageManager
                .queryBroadcastReceivers(
                    Intent("com.vk.push.ACTION_MASTER_HOST_UPDATE").setPackage(context.packageName),
                    0,
                )
                .map { it.activityInfo.name }
        assertThat(answering)
            .containsExactly("ru.rustore.sdk.pushclient.internal.arbiter.ArbiterBroadcastReceiver")
    }
}
