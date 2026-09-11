package dev.weather.astronomy

import java.time.Instant

enum class SkyObjectType {
    STAR,
    PLANET,
    MOON,
}

data class SkyObject(
    val name: String,
    val type: SkyObjectType,
    /** Degrees clockwise from true north. */
    val azimuthDegrees: Double,
    /** Degrees above the astronomical horizon. */
    val altitudeDegrees: Double,
    val magnitude: Double,
    val constellation: String? = null,
)

data class AstronomySnapshot(
    val calculatedAt: Instant,
    val sunAltitudeDegrees: Double,
    val moonAltitudeDegrees: Double,
    val moonAzimuthDegrees: Double,
    val moonIlluminationPercent: Double,
    val moonPhaseName: String,
    val nextSunrise: Instant?,
    val nextSunset: Instant?,
    val nextAstronomicalDawn: Instant?,
    val nextAstronomicalDusk: Instant?,
    val brightStarsAboveHorizon: List<SkyObject>,
    val planetsAboveHorizon: List<SkyObject>,
) {
    val isAstronomicallyDark: Boolean get() = sunAltitudeDegrees <= -18.0
}
