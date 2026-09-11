package dev.weather.providers.openmeteo

import com.google.common.truth.Truth.assertThat
import dev.weather.core.ProbabilitySource
import dev.weather.core.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant

class OpenMeteoWeatherProviderTest {
    @Test
    fun `maps API fields units and local timestamps into common model`() = runBlocking {
        var requestedPastHours: Int? = null
        val api = object : OpenMeteoApi {
            override suspend fun forecast(
                latitude: Double,
                longitude: Double,
                model: String,
                hourly: String,
                daily: String,
                forecastDays: Int,
                pastHours: Int,
                cellSelection: String,
                timezone: String,
                temperatureUnit: String,
                windSpeedUnit: String,
                precipitationUnit: String,
                timeFormat: String,
            ): OpenMeteoResponse {
                requestedPastHours = pastHours
                return OpenMeteoResponse(
                    latitude = 59.33,
                    longitude = 18.07,
                    elevation = 20.0,
                    timezone = "Europe/Stockholm",
                    utcOffsetSeconds = 7_200,
                    hourly = HourlyDto(
                        time = listOf("2026-09-10T12:00"),
                        temperature = listOf(14.3),
                        meanSeaLevelPressure = listOf(1_016.0),
                        precipitationProbability = listOf(34.0),
                        precipitation = listOf(0.4),
                        snowfall = listOf(0.2),
                        windSpeed = listOf(5.2),
                        weatherCode = listOf(61),
                    ),
                    daily = DailyDto(
                        time = listOf("2026-09-10"),
                        minimumTemperature = listOf(9.0),
                        maximumTemperature = listOf(16.0),
                        sunrise = listOf("2026-09-10T06:10"),
                        sunset = listOf("2026-09-10T19:20"),
                    ),
                )
            }
        }
        val provider = OpenMeteoWeatherProvider(
            ProviderId.ECMWF,
            OpenMeteoModel.ECMWF,
            api,
            clock = { Instant.parse("2026-09-10T09:00:00Z") },
        )

        val point = provider.getForecast(59.33, 18.07).points.single()

        assertThat(point.timestamp).isEqualTo(Instant.parse("2026-09-10T10:00:00Z"))
        assertThat(point.temperatureC).isEqualTo(14.3)
        assertThat(point.pressureHpa).isEqualTo(1_016.0)
        assertThat(point.snowfallMm).isEqualTo(2.0)
        assertThat(point.precipitationProbabilityPercent).isEqualTo(34.0)
        assertThat(point.probabilitySource).isEqualTo(ProbabilitySource.PROVIDER_DERIVED)
        assertThat(requestedPastHours).isEqualTo(72)
    }
}
