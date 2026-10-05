package ru.colabike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.push.PushBound
import ru.colabike.app.push.StoredPushBinding

/** What the phone remembers of its registration, and that it is the binding the handler reads. */
@RunWith(RobolectricTestRunner::class)
class StoredPushBindingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences =
        context.getSharedPreferences("stored-binding-test", Context.MODE_PRIVATE)
    private val record =
        StoredPushBinding.Record(
            accountId = "account-1",
            generation = 4,
            projectId = "project-1",
            fingerprint = "0123456789abcdef",
            syncedAtMillis = 1_000L,
        )

    @Before
    fun clean() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `nothing is bound at first, so nothing may be shown`() {
        val binding = StoredPushBinding(preferences)

        assertThat(binding.current()).isNull()
        assertThat(binding.record()).isNull()
    }

    @Test
    fun `a saved registration is the binding the handler reads, and survives a restart`() {
        StoredPushBinding(preferences).save(record)

        val again = StoredPushBinding(preferences)

        assertThat(again.record()).isEqualTo(record)
        assertThat(again.current()).isEqualTo(PushBound("account-1", 4))
    }

    @Test
    fun `clearing forgets the registration and keeps the install`() {
        val binding = StoredPushBinding(preferences)
        val install = binding.installationId()
        binding.save(record)

        binding.clear()

        assertThat(binding.current()).isNull()
        assertThat(binding.installationId()).isEqualTo(install)
    }

    @Test
    fun `the install id is made once, is a uuid, and a damaged one is replaced`() {
        val binding = StoredPushBinding(preferences)
        val first = binding.installationId()

        assertThat(first).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertThat(binding.installationId()).isEqualTo(first)

        preferences.edit().putString("installation_id", "../me").commit()
        val repaired = binding.installationId()

        assertThat(repaired).isNotEqualTo("../me")
        assertThat(repaired).matches("[0-9a-f-]{36}")
    }

    @Test
    fun `a generation below one is no binding`() {
        preferences.edit().putString("account_id", "account-1").putInt("generation", 0).commit()

        assertThat(StoredPushBinding(preferences).current()).isNull()
    }

    @Test
    fun `no secret is written, neither the address nor a text of the record`() {
        StoredPushBinding(preferences).save(record)

        val all = preferences.all.values.joinToString()
        assertThat(all).doesNotContain("token")
        assertThat(record.toString()).doesNotContain("secret")
    }
}
