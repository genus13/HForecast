package dev.weather.environment

import dev.weather.core.GeoLocation
import java.time.Instant

enum class AirQualityLevel {
    GOOD,
    FAIR,
    MODERATE,
    POOR,
    VERY_POOR,
    EXTREMELY_POOR,
    UNKNOWN,
}

data class AirQualityPoint(
    val timestamp: Instant,
    val europeanAqi: Double? = null,
    val pm10: Double? = null,
    val pm25: Double? = null,
    val carbonMonoxide: Double? = null,
    val nitrogenDioxide: Double? = null,
    val sulphurDioxide: Double? = null,
    val ozone: Double? = null,
    val aerosolOpticalDepth: Double? = null,
    val dust: Double? = null,
    val uvIndex: Double? = null,
    val alderPollen: Double? = null,
    val birchPollen: Double? = null,
    val grassPollen: Double? = null,
    val mugwortPollen: Double? = null,
) {
    val level: AirQualityLevel
        get() = when (val aqi = europeanAqi) {
            null -> AirQualityLevel.UNKNOWN
            in 0.0..<20.0 -> AirQualityLevel.GOOD
            in 20.0..<40.0 -> AirQualityLevel.FAIR
            in 40.0..<60.0 -> AirQualityLevel.MODERATE
            in 60.0..<80.0 -> AirQualityLevel.POOR
            in 80.0..<100.0 -> AirQualityLevel.VERY_POOR
            else -> AirQualityLevel.EXTREMELY_POOR
        }
}

data class AirQualityForecast(
    val requestedLocation: GeoLocation,
    val gridLocation: GeoLocation,
    val zoneId: String,
    val fetchedAt: Instant,
    val current: AirQualityPoint,
    val hourly: List<AirQualityPoint>,
)

data class AuroraGridCell(
    val longitude: Double,
    val latitude: Double,
    val probabilityPercent: Double,
)

data class KpForecastPoint(
    val timestamp: Instant,
    val kp: Double,
    val status: String,
    val noaaScale: String?,
)

data class SolarActivity(
    val currentKp: Double?,
    val kpForecast: List<KpForecastPoint>,
    val solarWindSpeedKmS: Double?,
    val interplanetaryFieldBtNt: Double?,
    val interplanetaryFieldBzNt: Double?,
    val solarRadioFluxSfu: Double?,
    val geomagneticStormScale: String?,
    val radioBlackoutScale: String?,
    val solarRadiationScale: String?,
)

data class AuroraForecast(
    val observationTime: Instant,
    val forecastTime: Instant,
    /** NOAA OVATION probability at the nearest 1-degree grid point. */
    val localProbabilityPercent: Double,
    val nearestGridLocation: GeoLocation,
    /** Reduced northern grid used only to render the overview chart. */
    val northernGrid: List<AuroraGridCell>,
    val solarActivity: SolarActivity,
)
