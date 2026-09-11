package dev.weather.core

import java.time.Instant

data class WeatherForecast(
    val provider: ProviderId,
    val modelName: String,
    val location: GeoLocation,
    /** Time this forecast became available to this application. */
    val issuedAt: Instant,
    /** Native model run time, null when the upstream API does not expose it. */
    val modelInitializationTime: Instant?,
    /** IANA zone ID retained for local presentation, for example Europe/Stockholm. */
    val zoneId: String,
    val points: List<WeatherPoint>,
) {
    init {
        require(points.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp }) {
            "Forecast points must be sorted by timestamp"
        }
    }
}

interface WeatherProvider {
    val id: ProviderId

    suspend fun getForecast(latitude: Double, longitude: Double): WeatherForecast
}
