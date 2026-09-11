package dev.weather.settings

import com.google.common.truth.Truth.assertThat
import dev.weather.core.NotificationPreferences
import org.junit.jupiter.api.Test

class NotificationPreferencesTest {
    @Test
    fun `daytime window includes start and excludes end`() {
        val settings = NotificationPreferences(true, 3, 7, 22)

        assertThat(settings.allowsHour(7)).isTrue()
        assertThat(settings.allowsHour(21)).isTrue()
        assertThat(settings.allowsHour(22)).isFalse()
    }

    @Test
    fun `overnight window crosses midnight`() {
        val settings = NotificationPreferences(true, 3, 22, 7)

        assertThat(settings.allowsHour(23)).isTrue()
        assertThat(settings.allowsHour(5)).isTrue()
        assertThat(settings.allowsHour(12)).isFalse()
    }
}
