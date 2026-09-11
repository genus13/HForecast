package dev.weather.astronomy

import dev.weather.core.GeoLocation
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.Vector
import io.github.cosinekitty.astronomy.constellation
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.rotationEqjEqd
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.cos
import kotlin.math.sin

object AstronomyCalculator {
    fun calculate(location: GeoLocation, instant: Instant = Instant.now()): AstronomySnapshot {
        val time = instant.toAstronomyTime()
        val observer = Observer(location.latitude, location.longitude, location.altitudeMeters ?: 0.0)
        val sun = bodyPosition(Body.Sun, time, observer)
        val moon = bodyPosition(Body.Moon, time, observer)
        val moonLight = illumination(Body.Moon, time)
        val phaseAngle = moonPhase(time)

        val stars = BRIGHT_STARS.map { star ->
            val coordinates = starPosition(star, time, observer)
            SkyObject(
                name = star.name,
                type = SkyObjectType.STAR,
                azimuthDegrees = coordinates.first,
                altitudeDegrees = coordinates.second,
                magnitude = star.magnitude,
                constellation = runCatching { constellation(star.raHours, star.declinationDegrees).name }
                    .getOrNull(),
            )
        }.filter { it.altitudeDegrees > 0.0 }.sortedBy { it.magnitude }

        val planets = PLANETS.mapNotNull { body ->
            val coordinates = bodyPosition(body, time, observer)
            if (coordinates.second <= 0.0) return@mapNotNull null
            SkyObject(
                name = body.name,
                type = SkyObjectType.PLANET,
                azimuthDegrees = coordinates.first,
                altitudeDegrees = coordinates.second,
                magnitude = runCatching { illumination(body, time).mag }.getOrDefault(9.0),
            )
        }.sortedBy { it.magnitude }

        return AstronomySnapshot(
            calculatedAt = instant,
            sunAltitudeDegrees = sun.second,
            moonAltitudeDegrees = moon.second,
            moonAzimuthDegrees = moon.first,
            moonIlluminationPercent = moonLight.phaseFraction * 100.0,
            moonPhaseName = phaseName(phaseAngle),
            nextSunrise = searchRiseSet(Body.Sun, observer, Direction.Rise, time, 2.0)?.toInstant(),
            nextSunset = searchRiseSet(Body.Sun, observer, Direction.Set, time, 2.0)?.toInstant(),
            nextAstronomicalDawn = searchAltitude(
                Body.Sun,
                observer,
                Direction.Rise,
                time,
                3.0,
                -18.0,
            )?.toInstant(),
            nextAstronomicalDusk = searchAltitude(
                Body.Sun,
                observer,
                Direction.Set,
                time,
                3.0,
                -18.0,
            )?.toInstant(),
            brightStarsAboveHorizon = stars,
            planetsAboveHorizon = planets,
        )
    }

    private fun bodyPosition(body: Body, time: Time, observer: Observer): Pair<Double, Double> {
        val equatorial = equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val horizontal = horizon(time, observer, equatorial.ra, equatorial.dec, Refraction.Normal)
        return horizontal.azimuth to horizontal.altitude
    }

    private fun starPosition(star: StarDefinition, time: Time, observer: Observer): Pair<Double, Double> {
        val raRadians = Math.toRadians(star.raHours * 15.0)
        val decRadians = Math.toRadians(star.declinationDegrees)
        val j2000 = Vector(
            cos(decRadians) * cos(raRadians),
            cos(decRadians) * sin(raRadians),
            sin(decRadians),
            time,
        )
        val ofDate = rotationEqjEqd(time).rotate(j2000).toEquatorial()
        val horizontal = horizon(time, observer, ofDate.ra, ofDate.dec, Refraction.Normal)
        return horizontal.azimuth to horizontal.altitude
    }

    private fun phaseName(angle: Double): String = when ((((angle + 22.5) % 360.0) / 45.0).toInt()) {
        0 -> "New Moon"
        1 -> "Waxing Crescent"
        2 -> "First Quarter"
        3 -> "Waxing Gibbous"
        4 -> "Full Moon"
        5 -> "Waning Gibbous"
        6 -> "Last Quarter"
        else -> "Waning Crescent"
    }
}

private data class StarDefinition(
    val name: String,
    val raHours: Double,
    val declinationDegrees: Double,
    val magnitude: Double,
)

private val PLANETS = listOf(
    Body.Mercury,
    Body.Venus,
    Body.Mars,
    Body.Jupiter,
    Body.Saturn,
    Body.Uranus,
    Body.Neptune,
)

private val BRIGHT_STARS = listOf(
    StarDefinition("Sirius", 6.7525, -16.7161, -1.46),
    StarDefinition("Canopus", 6.3992, -52.6957, -0.74),
    StarDefinition("Arcturus", 14.2610, 19.1824, -0.05),
    StarDefinition("Vega", 18.6156, 38.7837, 0.03),
    StarDefinition("Capella", 5.2782, 45.9980, 0.08),
    StarDefinition("Rigel", 5.2423, -8.2016, 0.13),
    StarDefinition("Procyon", 7.6550, 5.2250, 0.34),
    StarDefinition("Betelgeuse", 5.9195, 7.4071, 0.42),
    StarDefinition("Achernar", 1.6286, -57.2368, 0.46),
    StarDefinition("Hadar", 14.0637, -60.3730, 0.61),
    StarDefinition("Altair", 19.8464, 8.8683, 0.77),
    StarDefinition("Acrux", 12.4433, -63.0991, 0.76),
    StarDefinition("Aldebaran", 4.5987, 16.5093, 0.85),
    StarDefinition("Antares", 16.4901, -26.4320, 0.96),
    StarDefinition("Spica", 13.4199, -11.1613, 0.98),
    StarDefinition("Pollux", 7.7553, 28.0262, 1.14),
    StarDefinition("Fomalhaut", 22.9608, -29.6222, 1.16),
    StarDefinition("Deneb", 20.6905, 45.2803, 1.25),
    StarDefinition("Regulus", 10.1395, 11.9672, 1.35),
    StarDefinition("Castor", 7.5767, 31.8883, 1.58),
    StarDefinition("Polaris", 2.5303, 89.2641, 1.98),
)

private fun Instant.toAstronomyTime(): Time {
    val utc = atZone(ZoneOffset.UTC)
    return Time(
        utc.year,
        utc.monthValue,
        utc.dayOfMonth,
        utc.hour,
        utc.minute,
        utc.second + utc.nano / 1_000_000_000.0,
    )
}

private fun Time.toInstant(): Instant = Instant.ofEpochMilli(toMillisecondsSince1970())
