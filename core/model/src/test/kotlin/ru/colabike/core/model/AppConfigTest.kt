package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test

class AppConfigTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    @Test
    fun `a function the server does not mention is on, and a flag switches it off`() {
        val features = FeatureAvailability(mapOf("market" to false, "chat" to true))

        assertThat(features.isEnabled(Feature.Market)).isFalse()
        assertThat(features.isEnabled(Feature.Chat)).isTrue()
        assertThat(features.isEnabled(Feature.Rides)).isTrue()
        assertThat(Feature.entries.all { FeatureAvailability.AllOn.isEnabled(it) }).isTrue()
    }

    @Test
    fun `the built-in config announces nothing and switches nothing off`() {
        val config = AppConfig.Builtin

        assertThat(config.launch.enabled).isFalse()
        assertThat(config.onboarding.enabled).isFalse()
        assertThat(config.notice).isNull()
        assertThat(config.links).isEqualTo(ServiceLinks.None)
        assertThat(config.compatibility.updateState(1, null, now)).isEqualTo(UpdateState.None)
    }

    private fun policy(
        minimum: Int? = 5,
        latest: Int? = 9,
        mode: UpdateMode = UpdateMode.Hard,
        url: String? = "https://store.example/app",
    ) = Compatibility(minimum, latest, mode, url, "Обновите приложение")

    @Test
    fun `a build at or above the latest is current, below it a quiet offer`() {
        assertThat(policy().updateState(9, now, now)).isEqualTo(UpdateState.None)
        assertThat(policy().updateState(12, now, now)).isEqualTo(UpdateState.None)
        assertThat(policy().updateState(7, now, now))
            .isEqualTo(UpdateState.Available("https://store.example/app", "Обновите приложение"))
    }

    @Test
    fun `no versions in the policy means nothing to say`() {
        assertThat(policy(minimum = null, latest = null).updateState(1, now, now))
            .isEqualTo(UpdateState.None)
        // Only the latest: below it is an offer, never more.
        assertThat(policy(minimum = null).updateState(1, now, now))
            .isInstanceOf(UpdateState.Available::class.java)
    }

    @Test
    fun `below the minimum a hard policy blocks, a soft one only offers firmly`() {
        assertThat(policy().updateState(4, now, now))
            .isEqualTo(UpdateState.Required("https://store.example/app", "Обновите приложение"))
        assertThat(policy(mode = UpdateMode.Soft).updateState(4, now, now))
            .isInstanceOf(UpdateState.Recommended::class.java)
        // The minimum itself is supported.
        assertThat(policy().updateState(5, now, now))
            .isInstanceOf(UpdateState.Available::class.java)
    }

    @Test
    fun `a block without a way to update is not a block`() {
        assertThat(policy(url = null).updateState(1, now, now))
            .isEqualTo(UpdateState.Recommended(null, "Обновите приложение"))
    }

    @Test
    fun `a block is believed for a day after the server said it, then it softens`() {
        val theBorder = now.minus(HARD_UPDATE_TTL)
        assertThat(policy().updateState(1, theBorder, now))
            .isInstanceOf(UpdateState.Required::class.java)
        assertThat(policy().updateState(1, theBorder.minusSeconds(1), now))
            .isInstanceOf(UpdateState.Recommended::class.java)
        assertThat(HARD_UPDATE_TTL).isEqualTo(Duration.ofHours(24))
    }

    @Test
    fun `a block nobody confirmed, or confirmed in the future, is not believed`() {
        assertThat(policy().updateState(1, null, now))
            .isInstanceOf(UpdateState.Recommended::class.java)
        // A clock set back must not freeze a block in place.
        assertThat(policy().updateState(1, now.plusSeconds(60), now))
            .isInstanceOf(UpdateState.Recommended::class.java)
    }
}
