package dev.weather.ensemble

import dev.weather.core.ProbabilitySource
import dev.weather.core.ProviderId
import dev.weather.core.WeatherCondition
import dev.weather.core.WeatherForecast
import dev.weather.core.WeatherPoint
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class AlignedForecastPoint(
    val timestamp: Instant,
    val members: Map<ProviderId, WeatherPoint>,
)

class ForecastAligner(
    private val step: Duration = Duration.ofHours(1),
    private val maximumInterpolationGap: Duration = Duration.ofHours(3),
) {
    init {
        require(!step.isZero && !step.isNegative)
    }

    fun align(forecasts: List<WeatherForecast>): List<AlignedForecastPoint> {
        val usable = forecasts.filter { it.points.isNotEmpty() }
        if (usable.isEmpty()) return emptyList()

        val start = usable.minOf { it.points.first().timestamp }
        val end = usable.maxOf { it.points.last().timestamp }
        val output = mutableListOf<AlignedForecastPoint>()
        var target = start
        while (target <= end) {
            val members = usable.mapNotNull { forecast ->
                interpolate(forecast, target)?.let { forecast.provider to it }
            }.toMap()
            if (members.isNotEmpty()) output += AlignedForecastPoint(target, members)
            target = target.plus(step)
        }
        return output
    }

    private fun interpolate(forecast: WeatherForecast, target: Instant): WeatherPoint? {
        forecast.points.binarySearchBy(target) { it.timestamp }.let { index ->
            if (index >= 0) return forecast.points[index]
        }

        val insertion = forecast.points.binarySearchBy(target) { it.timestamp }
            .let { if (it >= 0) it else -it - 1 }
        if (insertion == 0 || insertion >= forecast.points.size) return null
        val before = forecast.points[insertion - 1]
        val after = forecast.points[insertion]
        val gap = Duration.between(before.timestamp, after.timestamp)
        if (gap > maximumInterpolationGap || gap.isZero) return null

        val fraction = Duration.between(before.timestamp, target).toMillis().toDouble() /
            gap.toMillis().toDouble()
        val lead = Duration.between(forecast.issuedAt, target).coerceAtLeast(Duration.ZERO)
        return WeatherPoint(
            timestamp = target,
            leadTime = lead,
            temperatureC = lerp(before.temperatureC, after.temperatureC, fraction),
            apparentTemperatureC = lerp(before.apparentTemperatureC, after.apparentTemperatureC, fraction),
            minimumTemperatureC = lerp(before.minimumTemperatureC, after.minimumTemperatureC, fraction),
            maximumTemperatureC = lerp(before.maximumTemperatureC, after.maximumTemperatureC, fraction),
            relativeHumidityPercent = lerp(before.relativeHumidityPercent, after.relativeHumidityPercent, fraction),
            dewPointC = lerp(before.dewPointC, after.dewPointC, fraction),
            pressureHpa = lerp(before.pressureHpa, after.pressureHpa, fraction),
            precipitationProbabilityPercent = lerp(
                before.precipitationProbabilityPercent,
                after.precipitationProbabilityPercent,
                fraction,
            ),
            probabilitySource = strongest(before.probabilitySource, after.probabilitySource),
            precipitationMm = lerp(before.precipitationMm, after.precipitationMm, fraction),
            rainMm = lerp(before.rainMm, after.rainMm, fraction),
            snowfallMm = lerp(before.snowfallMm, after.snowfallMm, fraction),
            windSpeedMs = lerp(before.windSpeedMs, after.windSpeedMs, fraction),
            windDirectionDegrees = circularLerp(
                before.windDirectionDegrees,
                after.windDirectionDegrees,
                fraction,
            ),
            windGustMs = lerp(before.windGustMs, after.windGustMs, fraction),
            cloudCoverPercent = lerp(before.cloudCoverPercent, after.cloudCoverPercent, fraction),
            visibilityMeters = lerp(before.visibilityMeters, after.visibilityMeters, fraction),
            uvIndex = lerp(before.uvIndex, after.uvIndex, fraction),
            condition = if (fraction < 0.5) before.condition else after.condition,
            sunrise = closest(before.sunrise, after.sunrise, fraction),
            sunset = closest(before.sunset, after.sunset, fraction),
        )
    }

    private fun lerp(a: Double?, b: Double?, fraction: Double): Double? = when {
        a == null -> b
        b == null -> a
        else -> a + (b - a) * fraction
    }

    private fun circularLerp(a: Double?, b: Double?, fraction: Double): Double? {
        if (a == null) return b
        if (b == null) return a
        val ar = a * PI / 180.0
        val br = b * PI / 180.0
        val x = (1.0 - fraction) * cos(ar) + fraction * cos(br)
        val y = (1.0 - fraction) * sin(ar) + fraction * sin(br)
        return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
    }

    private fun strongest(a: ProbabilitySource, b: ProbabilitySource): ProbabilitySource =
        if (a.ordinal >= b.ordinal) a else b

    private fun closest(a: Instant?, b: Instant?, fraction: Double): Instant? =
        if (fraction < 0.5) a ?: b else b ?: a
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
