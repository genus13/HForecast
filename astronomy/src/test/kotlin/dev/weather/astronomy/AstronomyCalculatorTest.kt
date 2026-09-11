package dev.weather.astronomy

import com.google.common.truth.Truth.assertThat
import dev.weather.core.GeoLocation
import org.junit.jupiter.api.Test
import java.time.Instant

class AstronomyCalculatorTest {
    @Test
    fun `objects use local horizon and valid compass bearings`() {
        val sky = AstronomyCalculator.calculate(
            GeoLocation(59.3293, 18.0686),
            Instant.parse("2026-01-15T22:00:00Z"),
        )

        assertThat(sky.brightStarsAboveHorizon).isNotEmpty()
        assertThat(sky.brightStarsAboveHorizon.all { it.azimuthDegrees in 0.0..360.0 }).isTrue()
        assertThat(sky.moonIlluminationPercent).isAtLeast(0.0)
        assertThat(sky.moonIlluminationPercent).isAtMost(100.0)
    }
}
