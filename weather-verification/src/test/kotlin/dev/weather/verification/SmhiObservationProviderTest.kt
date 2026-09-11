package dev.weather.verification

import com.google.common.truth.Truth.assertThat
import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SmhiObservationProviderTest {
    private val now = Instant.parse("2026-09-10T10:30:00Z")
    private val location = GeoLocation(59.33, 18.07)

    @Test
    fun `selects a current quality-controlled nearby station`() {
        val response = SmhiObservationResponse(
            station = listOf(
                station("stale", "Stale station", 59.33, 18.07, now.minus(Duration.ofHours(4)), "G", "8.0"),
                station("near-y", "Nearby provisional", 59.34, 18.07, now.minus(Duration.ofMinutes(30)), "Y", "10.0"),
                station("good", "Quality station", 59.36, 18.07, now.minus(Duration.ofMinutes(30)), "G", "11.0"),
            ),
        )

        val observation = response.nearestObservation(
            location,
            "SMHI_METOBS",
            ForecastVariable.TEMPERATURE,
            now,
            100_000.0,
        )

        assertThat(observation!!.stationId).isEqualTo("good")
        assertThat(observation.value).isEqualTo(11.0)
        assertThat(observation.targetTime).isEqualTo(now.minus(Duration.ofMinutes(30)))
    }

    @Test
    fun `turns measured hourly precipitation into a separate observed event`() = runBlocking {
        val api = object : SmhiObservationApi {
            override suspend fun latestHour(parameter: String): SmhiObservationResponse =
                if (parameter == "7") {
                    SmhiObservationResponse(
                        listOf(station("rain", "Rain gauge", 59.34, 18.07, now.minusSeconds(1_800), "G", "0.3")),
                    )
                } else {
                    SmhiObservationResponse()
                }
        }
        val provider = SmhiObservationProvider(api = api, clock = { now })

        val observations = provider.latest(location)

        assertThat(observations.map { it.variable }).containsExactly(
            ForecastVariable.PRECIPITATION_AMOUNT,
            ForecastVariable.PRECIPITATION_PROBABILITY,
        )
        assertThat(observations.single { it.variable == ForecastVariable.PRECIPITATION_AMOUNT }.value)
            .isEqualTo(0.3)
        assertThat(observations.single { it.variable == ForecastVariable.PRECIPITATION_PROBABILITY }.value)
            .isEqualTo(100.0)
    }

    private fun station(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
        time: Instant,
        quality: String,
        value: String,
    ) = SmhiStationDto(
        key = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        values = listOf(SmhiValueDto(time.toEpochMilli(), value, quality)),
    )
}
