package dev.weather.ensemble

import com.google.common.truth.Truth.assertThat
import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import dev.weather.core.ProbabilitySource
import dev.weather.core.ProviderId
import dev.weather.core.WeatherForecast
import dev.weather.core.WeatherPoint
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class WeightedEnsembleEngineTest {
    private val now = Instant.parse("2026-09-10T10:00:00Z")

    @Test
    fun `uses configured weights and renormalizes when a value is missing`() {
        val weights = EnsembleWeightProvider { provider, _, _, _, _ ->
            when (provider) {
                ProviderId.ECMWF -> 0.6
                ProviderId.ICON -> 0.3
                ProviderId.GFS -> 0.1
                else -> 0.0
            }
        }
        val result = WeightedEnsembleEngine(weights).calculate(
            listOf(
                forecast(ProviderId.ECMWF, temperature = 10.0, humidity = 80.0),
                forecast(ProviderId.ICON, temperature = 20.0, humidity = null),
                forecast(ProviderId.GFS, temperature = 30.0, humidity = 60.0),
            ),
            generatedAt = now,
        )

        assertThat(result.current!!.consensus.temperatureC).isWithin(0.0001).of(15.0)
        assertThat(result.current!!.consensus.relativeHumidityPercent).isWithin(0.0001)
            .of((80.0 * 0.6 + 60.0 * 0.1) / 0.7)
        val effectiveWeights = result.current!!.effectiveWeights.getValue(ForecastVariable.TEMPERATURE)
        assertThat(effectiveWeights.keys).containsExactly(ProviderId.ECMWF, ProviderId.ICON, ProviderId.GFS)
        assertThat(effectiveWeights.getValue(ProviderId.ECMWF)).isWithin(0.0001).of(0.6)
        assertThat(effectiveWeights.getValue(ProviderId.ICON)).isWithin(0.0001).of(0.3)
        assertThat(effectiveWeights.getValue(ProviderId.GFS)).isWithin(0.0001).of(0.1)
    }

    @Test
    fun `does not manufacture precipitation probability from deterministic spread`() {
        val result = WeightedEnsembleEngine(EnsembleWeightProvider { _, _, _, _, _ -> 1.0 }).calculate(
            listOf(
                forecast(ProviderId.ECMWF, precipitation = 0.0),
                forecast(ProviderId.ICON, precipitation = 4.0),
            ),
            generatedAt = now,
        )

        assertThat(result.current!!.consensus.precipitationProbabilityPercent).isNull()
        assertThat(result.current!!.consensus.probabilitySource).isEqualTo(ProbabilitySource.NONE)
    }

    @Test
    fun `daily outlook excludes dates before the generated local date`() {
        val forecast = WeatherForecast(
            provider = ProviderId.ECMWF,
            modelName = "ECMWF",
            location = GeoLocation(59.3, 18.0),
            issuedAt = now,
            modelInitializationTime = null,
            zoneId = "Europe/Stockholm",
            points = listOf(
                WeatherPoint(now.minus(Duration.ofHours(30)), Duration.ZERO, temperatureC = 8.0),
                WeatherPoint(now.plus(Duration.ofHours(1)), Duration.ofHours(1), temperatureC = 12.0),
                WeatherPoint(now.plus(Duration.ofHours(25)), Duration.ofHours(25), temperatureC = 14.0),
            ),
        )

        val result = WeightedEnsembleEngine(EnsembleWeightProvider { _, _, _, _, _ -> 1.0 })
            .calculate(listOf(forecast), generatedAt = now)

        assertThat(result.daily.map { it.date }).containsExactly(
            LocalDate.parse("2026-09-10"),
            LocalDate.parse("2026-09-11"),
        ).inOrder()
    }

    private fun forecast(
        provider: ProviderId,
        temperature: Double? = 10.0,
        humidity: Double? = 70.0,
        precipitation: Double? = 0.0,
    ) = WeatherForecast(
        provider = provider,
        modelName = provider.name,
        location = GeoLocation(59.3, 18.0),
        issuedAt = now,
        modelInitializationTime = null,
        zoneId = "Europe/Stockholm",
        points = listOf(
            WeatherPoint(
                timestamp = now,
                leadTime = Duration.ZERO,
                temperatureC = temperature,
                relativeHumidityPercent = humidity,
                precipitationMm = precipitation,
            ),
        ),
    )
}
