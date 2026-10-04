package ru.colabike.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the merged manifest asks the person for: the map library brings more than the app wants. */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {
    @Test
    fun `viewing a route asks for no location and no Wi-Fi state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val requested =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .toList()

        assertThat(requested).contains("android.permission.INTERNET")
        assertThat(requested)
            .containsNoneOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
            )
    }
}
