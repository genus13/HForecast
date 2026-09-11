package dev.weather.ensemble

import com.google.common.truth.Truth.assertThat
import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import dev.weather.core.WeatherForecast
import dev.weather.core.WeatherPoint
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ForecastAlignerTest {
    private val start = Instant.parse("2026-09-10T10:00:00Z")

    @Test
    fun `aligns timestamps and interpolates a coarser forecast`() {
        val hourly = forecast(
            ProviderId.ICON,
            listOf(point(0, 10.0), point(1, 12.0), point(2, 14.0)),
        )
        val coarse = forecast(
            ProviderId.GFS,
            listOf(point(0, 8.0), point(2, 12.0)),
        )

        val aligned = ForecastAligner().align(listOf(hourly, coarse))

        assertThat(aligned).hasSize(3)
        assertThat(aligned[1].members.getValue(ProviderId.GFS).temperatureC).isEqualTo(10.0)
        assertThat(aligned[1].timestamp).isEqualTo(start.plus(Duration.ofHours(1)))
    }

    private fun forecast(provider: ProviderId, points: List<WeatherPoint>) = WeatherForecast(
        provider = provider,
        modelName = provider.name,
        location = GeoLocation(59.3, 18.0),
        issuedAt = start,
        modelInitializationTime = null,
        zoneId = "Europe/Stockholm",
        points = points,
    )

    private fun point(hour: Long, temperature: Double) = WeatherPoint(
        timestamp = start.plus(Duration.ofHours(hour)),
        leadTime = Duration.ofHours(hour),
        temperatureC = temperature,
    )
}
