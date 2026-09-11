package dev.weather.verification

import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import java.time.Instant

data class ReferenceObservation(
    val sourceId: String,
    val stationId: String,
    val stationName: String,
    val stationLatitude: Double,
    val stationLongitude: Double,
    val distanceMeters: Double,
    val targetTime: Instant,
    val variable: ForecastVariable,
    val value: Double,
    val quality: String,
)

interface ObservationProvider {
    val sourceId: String
    fun supports(location: GeoLocation): Boolean
    suspend fun latest(location: GeoLocation): List<ReferenceObservation>
}
