package dev.weather.core

import java.time.Duration
import java.time.Instant

/**
 * Provider-independent weather data. All values use SI-like display units:
 * Celsius, metres/second, millimetres and hectopascals.
 */
data class WeatherPoint(
    val timestamp: Instant,
    val leadTime: Duration,
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
    val minimumTemperatureC: Double? = null,
    val maximumTemperatureC: Double? = null,
    val relativeHumidityPercent: Double? = null,
    val dewPointC: Double? = null,
    val pressureHpa: Double? = null,
    val precipitationProbabilityPercent: Double? = null,
    val probabilitySource: ProbabilitySource = ProbabilitySource.NONE,
    val precipitationMm: Double? = null,
    val rainMm: Double? = null,
    val snowfallMm: Double? = null,
    val windSpeedMs: Double? = null,
    val windDirectionDegrees: Double? = null,
    val windGustMs: Double? = null,
    val cloudCoverPercent: Double? = null,
    val visibilityMeters: Double? = null,
    val uvIndex: Double? = null,
    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    val sunrise: Instant? = null,
    val sunset: Instant? = null,
)
