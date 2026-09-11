package dev.weather.providers.openmeteo

import dev.weather.core.GeoLocation
import dev.weather.core.ProbabilitySource
import dev.weather.core.ProviderId
import dev.weather.core.WeatherForecast
import dev.weather.core.WeatherPoint
import dev.weather.core.WeatherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class OpenMeteoWeatherProvider(
    override val id: ProviderId,
    private val model: OpenMeteoModel,
    private val api: OpenMeteoApi,
    private val clock: () -> Instant = Instant::now,
    private val inMemoryRefreshInterval: Duration = Duration.ofMinutes(15),
) : WeatherProvider {
    private val mutex = Mutex()
    private var lastForecast: WeatherForecast? = null

    init {
        require(id == model.provider)
    }

    override suspend fun getForecast(latitude: Double, longitude: Double): WeatherForecast = mutex.withLock {
        val now = clock()
        lastForecast?.takeIf {
            kotlin.math.abs(it.location.latitude - latitude) < 0.001 &&
                kotlin.math.abs(it.location.longitude - longitude) < 0.001 &&
                Duration.between(it.issuedAt, now) < inMemoryRefreshInterval
        }?.let { return it }

        val response = api.forecast(
            latitude = latitude,
            longitude = longitude,
            model = model.apiModel,
            pastHours = OpenMeteoApi.RECENT_HISTORY_HOURS,
        )
        val mapped = map(response, now)
        check(mapped.points.isNotEmpty()) { "${model.displayName} returned no hourly forecast points" }
        lastForecast = mapped
        mapped
    }

    private fun map(response: OpenMeteoResponse, fetchedAt: Instant): WeatherForecast {
        val zone = runCatching { ZoneId.of(response.timezone) }
            .getOrElse { ZoneOffset.ofTotalSeconds(response.utcOffsetSeconds) }
        val daily = response.daily?.let { dto ->
            dto.time.mapIndexedNotNull { index, rawDate ->
                runCatching { LocalDate.parse(rawDate) }.getOrNull()?.let { date ->
                    date to DailyValues(
                        minimum = dto.minimumTemperature.valueAt(index),
                        maximum = dto.maximumTemperature.valueAt(index),
                        sunrise = dto.sunrise.stringAt(index)?.let { parseInstant(it, zone) },
                        sunset = dto.sunset.stringAt(index)?.let { parseInstant(it, zone) },
                    )
                }
            }.toMap()
        }.orEmpty()

        val points = response.hourly.time.mapIndexedNotNull { index, rawTime ->
            val timestamp = runCatching { parseInstant(rawTime, zone) }.getOrNull()
                ?: return@mapIndexedNotNull null
            val dailyValues = daily[timestamp.atZone(zone).toLocalDate()]
            val probability = response.hourly.precipitationProbability.valueAt(index)
            WeatherPoint(
                timestamp = timestamp,
                leadTime = Duration.between(fetchedAt, timestamp).coerceAtLeast(Duration.ZERO),
                temperatureC = response.hourly.temperature.valueAt(index),
                apparentTemperatureC = response.hourly.apparentTemperature.valueAt(index),
                minimumTemperatureC = dailyValues?.minimum,
                maximumTemperatureC = dailyValues?.maximum,
                relativeHumidityPercent = response.hourly.relativeHumidity.valueAt(index),
                dewPointC = response.hourly.dewPoint.valueAt(index),
                pressureHpa = response.hourly.meanSeaLevelPressure.valueAt(index),
                precipitationProbabilityPercent = probability,
                probabilitySource = if (probability == null) {
                    ProbabilitySource.NONE
                } else {
                    ProbabilitySource.PROVIDER_DERIVED
                },
                precipitationMm = response.hourly.precipitation.valueAt(index),
                rainMm = response.hourly.rain.valueAt(index),
                snowfallMm = response.hourly.snowfall.valueAt(index)?.times(10.0),
                windSpeedMs = response.hourly.windSpeed.valueAt(index),
                windDirectionDegrees = response.hourly.windDirection.valueAt(index),
                windGustMs = response.hourly.windGusts.valueAt(index),
                cloudCoverPercent = response.hourly.cloudCover.valueAt(index),
                visibilityMeters = response.hourly.visibility.valueAt(index),
                uvIndex = response.hourly.uvIndex.valueAt(index),
                condition = WmoConditionMapper.map(response.hourly.weatherCode.intAt(index)),
                sunrise = dailyValues?.sunrise,
                sunset = dailyValues?.sunset,
            )
        }.sortedBy { it.timestamp }

        return WeatherForecast(
            provider = id,
            modelName = model.displayName,
            location = GeoLocation(
                latitude = response.latitude,
                longitude = response.longitude,
                altitudeMeters = response.elevation,
            ),
            issuedAt = fetchedAt,
            modelInitializationTime = null,
            zoneId = zone.id,
            points = points,
        )
    }

    private fun parseInstant(value: String, zone: ZoneId): Instant =
        LocalDateTime.parse(value).atZone(zone).toInstant()

    private data class DailyValues(
        val minimum: Double?,
        val maximum: Double?,
        val sunrise: Instant?,
        val sunset: Instant?,
    )
}

private fun List<Double?>?.valueAt(index: Int): Double? = this?.getOrNull(index)
private fun List<Int?>?.intAt(index: Int): Int? = this?.getOrNull(index)
private fun List<String?>?.stringAt(index: Int): String? = this?.getOrNull(index)
private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
