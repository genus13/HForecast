package dev.weather.ensemble

import com.google.common.truth.Truth.assertThat
import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class BiasCorrectorTest {
    @Test
    fun `subtracts historical mean bias from raw value`() {
        val corrector = FixedBiasCorrector(
            mapOf((ProviderId.ICON to ForecastVariable.TEMPERATURE) to 1.2),
        )

        val corrected = corrector.correct(
            ProviderId.ICON,
            ForecastVariable.TEMPERATURE,
            Duration.ofHours(24),
            16.0,
            GeoLocation(59.3, 18.0),
            Instant.parse("2026-09-11T10:00:00Z"),
        )

        assertThat(corrected).isWithin(0.0001).of(14.8)
    }
}
