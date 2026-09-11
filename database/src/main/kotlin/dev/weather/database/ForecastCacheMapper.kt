package dev.weather.database

import dev.weather.core.GeoLocation
import dev.weather.core.ProbabilitySource
import dev.weather.core.ProviderId
import dev.weather.core.WeatherCondition
import dev.weather.core.WeatherForecast
import dev.weather.core.WeatherPoint
import java.time.Duration
import java.time.Instant
import java.util.Locale

fun WeatherForecast.toCacheEntities(
    requestedLocation: GeoLocation = location,
): Pair<ForecastRunEntity, List<ForecastPointEntity>> = toArchiveEntities(
    providerId = provider.name,
    modelName = modelName,
    modelInitializationTime = modelInitializationTime,
    issuedAt = issuedAt,
    forecastLocation = location,
    requestedLocation = requestedLocation,
    zoneId = zoneId,
    points = points,
)

fun toArchiveEntities(
    providerId: String,
    modelName: String,
    modelInitializationTime: Instant?,
    issuedAt: Instant,
    forecastLocation: GeoLocation,
    requestedLocation: GeoLocation,
    zoneId: String,
    points: List<WeatherPoint>,
): Pair<ForecastRunEntity, List<ForecastPointEntity>> {
    val key = requestedLocation.cacheLocationKey()
    val runId = "$providerId:${issuedAt.toEpochMilli()}:$key"
    val run = ForecastRunEntity(
        runId = runId,
        providerId = providerId,
        modelName = modelName,
        modelInitializationEpochMillis = modelInitializationTime?.toEpochMilli(),
        issuedAtEpochMillis = issuedAt.toEpochMilli(),
        latitude = forecastLocation.latitude,
        longitude = forecastLocation.longitude,
        altitudeMeters = requestedLocation.altitudeMeters ?: forecastLocation.altitudeMeters,
        displayName = requestedLocation.displayName,
        locationKey = key,
        zoneId = zoneId,
    )
    val entities = points.map { point ->
        ForecastPointEntity(
            runId = runId,
            targetEpochMillis = point.timestamp.toEpochMilli(),
            leadTimeSeconds = point.leadTime.seconds,
            temperatureC = point.temperatureC,
            apparentTemperatureC = point.apparentTemperatureC,
            minimumTemperatureC = point.minimumTemperatureC,
            maximumTemperatureC = point.maximumTemperatureC,
            relativeHumidityPercent = point.relativeHumidityPercent,
            dewPointC = point.dewPointC,
            pressureHpa = point.pressureHpa,
            precipitationProbabilityPercent = point.precipitationProbabilityPercent,
            probabilitySource = point.probabilitySource.name,
            precipitationMm = point.precipitationMm,
            rainMm = point.rainMm,
            snowfallMm = point.snowfallMm,
            windSpeedMs = point.windSpeedMs,
            windDirectionDegrees = point.windDirectionDegrees,
            windGustMs = point.windGustMs,
            cloudCoverPercent = point.cloudCoverPercent,
            visibilityMeters = point.visibilityMeters,
            uvIndex = point.uvIndex,
            condition = point.condition.name,
            sunriseEpochMillis = point.sunrise?.toEpochMilli(),
            sunsetEpochMillis = point.sunset?.toEpochMilli(),
        )
    }
    return run to entities
}

fun ForecastRunWithPoints.toDomain(): WeatherForecast = WeatherForecast(
    provider = enumValueOrDefault(run.providerId, ProviderId.GFS),
    modelName = run.modelName,
    location = GeoLocation(run.latitude, run.longitude, run.displayName, run.altitudeMeters),
    issuedAt = Instant.ofEpochMilli(run.issuedAtEpochMillis),
    modelInitializationTime = run.modelInitializationEpochMillis?.let(Instant::ofEpochMilli),
    zoneId = run.zoneId,
    points = points.sortedBy { it.targetEpochMillis }.map { point ->
        WeatherPoint(
            timestamp = Instant.ofEpochMilli(point.targetEpochMillis),
            leadTime = Duration.ofSeconds(point.leadTimeSeconds),
            temperatureC = point.temperatureC,
            apparentTemperatureC = point.apparentTemperatureC,
            minimumTemperatureC = point.minimumTemperatureC,
            maximumTemperatureC = point.maximumTemperatureC,
            relativeHumidityPercent = point.relativeHumidityPercent,
            dewPointC = point.dewPointC,
            pressureHpa = point.pressureHpa,
            precipitationProbabilityPercent = point.precipitationProbabilityPercent,
            probabilitySource = enumValueOrDefault(point.probabilitySource, ProbabilitySource.NONE),
            precipitationMm = point.precipitationMm,
            rainMm = point.rainMm,
            snowfallMm = point.snowfallMm,
            windSpeedMs = point.windSpeedMs,
            windDirectionDegrees = point.windDirectionDegrees,
            windGustMs = point.windGustMs,
            cloudCoverPercent = point.cloudCoverPercent,
            visibilityMeters = point.visibilityMeters,
            uvIndex = point.uvIndex,
            condition = enumValueOrDefault(point.condition, WeatherCondition.UNKNOWN),
            sunrise = point.sunriseEpochMillis?.let(Instant::ofEpochMilli),
            sunset = point.sunsetEpochMillis?.let(Instant::ofEpochMilli),
        )
    },
)

fun GeoLocation.cacheLocationKey(): String = String.format(
    Locale.US,
    "%.2f,%.2f",
    latitude,
    longitude,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default
