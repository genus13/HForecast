package dev.weather.core

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String? = null,
    val altitudeMeters: Double? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }
}
