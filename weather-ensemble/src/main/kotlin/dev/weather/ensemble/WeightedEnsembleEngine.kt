package dev.weather.ensemble

import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import dev.weather.core.ProbabilitySource
import dev.weather.core.ProviderId
import dev.weather.core.WeatherCondition
import dev.weather.core.WeatherForecast
import dev.weather.core.WeatherPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class WeightedEnsembleEngine(
    private val weights: EnsembleWeightProvider,
    private val biasCorrector: BiasCorrector = NoBiasCorrection,
    private val aligner: ForecastAligner = ForecastAligner(),
    private val confidencePolicy: TransparentConfidencePolicy = TransparentConfidencePolicy(),
) {
    fun calculate(
        forecasts: List<WeatherForecast>,
        generatedAt: Instant = Instant.now(),
        location: GeoLocation = forecasts.first().location,
    ): EnsembleResult {
        require(forecasts.isNotEmpty()) { "At least one forecast is required" }
        val aligned = aligner.align(forecasts)
        val points = aligned.map { calculatePoint(it, generatedAt, location) }
        val zoneId = forecasts.first().zoneId
        return EnsembleResult(
            location = location,
            zoneId = zoneId,
            generatedAt = generatedAt,
            points = points,
            daily = aggregateDaily(points, ZoneId.of(zoneId), generatedAt),
            providerIssuedAt = forecasts.associate { it.provider to it.issuedAt },
            providerLocations = forecasts.associate { it.provider to it.location },
        )
    }

    private fun calculatePoint(
        aligned: AlignedForecastPoint,
        generatedAt: Instant,
        location: GeoLocation,
    ): ConsensusPoint {
        val lead = Duration.between(generatedAt, aligned.timestamp).coerceAtLeast(Duration.ZERO)
        val members = aligned.members
        val valueSources = mapOf<ForecastVariable, (WeatherPoint) -> Double?>(
            ForecastVariable.TEMPERATURE to { it.temperatureC },
            ForecastVariable.APPARENT_TEMPERATURE to { it.apparentTemperatureC },
            ForecastVariable.MINIMUM_TEMPERATURE to { it.minimumTemperatureC },
            ForecastVariable.MAXIMUM_TEMPERATURE to { it.maximumTemperatureC },
            ForecastVariable.HUMIDITY to { it.relativeHumidityPercent },
            ForecastVariable.DEW_POINT to { it.dewPointC },
            ForecastVariable.PRESSURE to { it.pressureHpa },
            ForecastVariable.PRECIPITATION_PROBABILITY to {
                it.precipitationProbabilityPercent.takeIf { _ -> it.probabilitySource != ProbabilitySource.NONE }
            },
            ForecastVariable.PRECIPITATION_AMOUNT to { it.precipitationMm },
            ForecastVariable.RAIN to { it.rainMm },
            ForecastVariable.SNOWFALL to { it.snowfallMm },
            ForecastVariable.WIND_SPEED to { it.windSpeedMs },
            ForecastVariable.WIND_DIRECTION to { it.windDirectionDegrees },
            ForecastVariable.WIND_GUST to { it.windGustMs },
            ForecastVariable.CLOUD_COVER to { it.cloudCoverPercent },
            ForecastVariable.VISIBILITY to { it.visibilityMeters },
            ForecastVariable.UV_INDEX to { it.uvIndex },
        )
        val values = valueSources.mapValues { (variable, getter) ->
            if (variable == ForecastVariable.WIND_DIRECTION) {
                weightedDirection(members, variable, lead, location, aligned.timestamp, getter)
            } else {
                weightedMean(members, variable, lead, location, aligned.timestamp, getter)
            }
        }
        val effectiveWeights = valueSources.mapValues { (variable, getter) ->
            normalizedWeights(members, variable, lead, location, aligned.timestamp, getter)
        }
        val correctedMemberValues = valueSources.mapValues { (variable, getter) ->
            members.mapNotNull { (provider, point) ->
                getter(point)?.takeIf(Double::isFinite)?.let { raw ->
                    provider to biasCorrector.correct(
                        provider,
                        variable,
                        lead,
                        raw,
                        location,
                        aligned.timestamp,
                    )
                }
            }.toMap()
        }
        val spreads = valueSources.mapNotNull { (variable, _) ->
            val corrected = correctedMemberValues.getValue(variable).values
            val spread = if (variable == ForecastVariable.WIND_DIRECTION) {
                circularRange(corrected.map { it })
            } else {
                SpreadCalculator.range(corrected.map { it })
            }
            spread?.let { variable to it }
        }.toMap()
        val confidence = valueSources.mapValues { (variable, getter) ->
            val count = members.values.map(getter).count { it != null }
            val expectedModels = if (lead < Duration.ofHours(60)) 4 else 3
            confidencePolicy.evaluate(variable, count, spreads[variable], expectedModels)
        }
        val probabilitySource = when {
            members.values.any { it.probabilitySource == ProbabilitySource.ENSEMBLE_MEMBERS } ->
                ProbabilitySource.ENSEMBLE_MEMBERS
            values[ForecastVariable.PRECIPITATION_PROBABILITY] != null -> ProbabilitySource.PROVIDER_DERIVED
            else -> ProbabilitySource.NONE
        }

        val consensus = WeatherPoint(
            timestamp = aligned.timestamp,
            leadTime = lead,
            temperatureC = values[ForecastVariable.TEMPERATURE],
            apparentTemperatureC = values[ForecastVariable.APPARENT_TEMPERATURE],
            minimumTemperatureC = values[ForecastVariable.MINIMUM_TEMPERATURE],
            maximumTemperatureC = values[ForecastVariable.MAXIMUM_TEMPERATURE],
            relativeHumidityPercent = values[ForecastVariable.HUMIDITY],
            dewPointC = values[ForecastVariable.DEW_POINT],
            pressureHpa = values[ForecastVariable.PRESSURE],
            precipitationProbabilityPercent = values[ForecastVariable.PRECIPITATION_PROBABILITY],
            probabilitySource = probabilitySource,
            precipitationMm = values[ForecastVariable.PRECIPITATION_AMOUNT],
            rainMm = values[ForecastVariable.RAIN],
            snowfallMm = values[ForecastVariable.SNOWFALL],
            windSpeedMs = values[ForecastVariable.WIND_SPEED],
            windDirectionDegrees = values[ForecastVariable.WIND_DIRECTION],
            windGustMs = values[ForecastVariable.WIND_GUST],
            cloudCoverPercent = values[ForecastVariable.CLOUD_COVER],
            visibilityMeters = values[ForecastVariable.VISIBILITY],
            uvIndex = values[ForecastVariable.UV_INDEX],
            condition = weightedCondition(members, lead, location, aligned.timestamp),
            sunrise = members.values.mapNotNull { it.sunrise }.minOrNull(),
            sunset = members.values.mapNotNull { it.sunset }.minOrNull(),
        )
        return ConsensusPoint(
            aligned.timestamp,
            consensus,
            members,
            spreads,
            confidence,
            effectiveWeights,
            correctedMemberValues,
        )
    }

    private fun weightedMean(
        members: Map<ProviderId, WeatherPoint>,
        variable: ForecastVariable,
        lead: Duration,
        location: GeoLocation,
        targetTime: Instant,
        value: (WeatherPoint) -> Double?,
    ): Double? {
        val inputs = members.mapNotNull { (provider, point) ->
            value(point)?.takeIf(Double::isFinite)?.let { raw ->
                val corrected = biasCorrector.correct(provider, variable, lead, raw, location, targetTime)
                Triple(
                    provider,
                    corrected,
                    weights.weight(provider, variable, lead, location, targetTime).coerceAtLeast(0.0),
                )
            }
        }
        if (inputs.isEmpty()) return null
        val weightSum = inputs.sumOf { it.third }
        return if (weightSum > 0.0) {
            inputs.sumOf { it.second * it.third } / weightSum
        } else {
            inputs.map { it.second }.average()
        }
    }

    private fun weightedDirection(
        members: Map<ProviderId, WeatherPoint>,
        variable: ForecastVariable,
        lead: Duration,
        location: GeoLocation,
        targetTime: Instant,
        value: (WeatherPoint) -> Double?,
    ): Double? {
        val inputs = members.mapNotNull { (provider, point) ->
            value(point)?.let { direction ->
                val corrected = biasCorrector.correct(provider, variable, lead, direction, location, targetTime)
                val weight = weights.weight(provider, variable, lead, location, targetTime).coerceAtLeast(0.0)
                corrected to weight
            }
        }
        if (inputs.isEmpty()) return null
        val effective = if (inputs.sumOf { it.second } == 0.0) inputs.map { it.first to 1.0 } else inputs
        val x = effective.sumOf { (degrees, weight) -> cos(degrees * PI / 180.0) * weight }
        val y = effective.sumOf { (degrees, weight) -> sin(degrees * PI / 180.0) * weight }
        return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
    }

    private fun weightedCondition(
        members: Map<ProviderId, WeatherPoint>,
        lead: Duration,
        location: GeoLocation,
        targetTime: Instant,
    ): WeatherCondition = members.entries
        .filter { it.value.condition != WeatherCondition.UNKNOWN }
        .groupBy { it.value.condition }
        .maxByOrNull { (_, entries) ->
            entries.sumOf {
                weights.weight(it.key, ForecastVariable.PRECIPITATION_AMOUNT, lead, location, targetTime)
            }
        }
        ?.key ?: WeatherCondition.UNKNOWN

    private fun normalizedWeights(
        members: Map<ProviderId, WeatherPoint>,
        variable: ForecastVariable,
        lead: Duration,
        location: GeoLocation,
        targetTime: Instant,
        value: (WeatherPoint) -> Double?,
    ): Map<ProviderId, Double> {
        val raw = members.mapNotNull { (provider, point) ->
            value(point)?.takeIf(Double::isFinite)?.let {
                provider to weights.weight(provider, variable, lead, location, targetTime).coerceAtLeast(0.0)
            }
        }.toMap()
        if (raw.isEmpty()) return emptyMap()
        val total = raw.values.sum()
        return if (total > 0.0) raw.mapValues { it.value / total }
        else raw.mapValues { 1.0 / raw.size }
    }

    private fun circularRange(values: Collection<Double?>): Double? {
        val directions = values.filterNotNull()
        if (directions.isEmpty()) return null
        return directions.maxOf { a ->
            directions.maxOf { b -> kotlin.math.abs((a - b + 540.0) % 360.0 - 180.0) }
        }
    }

    private fun aggregateDaily(
        points: List<ConsensusPoint>,
        zoneId: ZoneId,
        generatedAt: Instant,
    ): List<DailyConsensus> {
        val currentLocalDate = generatedAt.atZone(zoneId).toLocalDate()
        return points
            .filter { it.timestamp.atZone(zoneId).toLocalDate() >= currentLocalDate }
            .groupBy { it.timestamp.atZone(zoneId).toLocalDate() }
            .map { (date, dayPoints) ->
                val weather = dayPoints.map { it.consensus }
                val confidence = dayPoints.map { it.overallConfidence }
                    .minByOrNull { it.rank } ?: ConfidenceBand.LOW
                DailyConsensus(
                    date = date,
                    minimumTemperatureC = weather.mapNotNull { it.minimumTemperatureC }.minOrNull()
                        ?: weather.mapNotNull { it.temperatureC }.minOrNull(),
                    maximumTemperatureC = weather.mapNotNull { it.maximumTemperatureC }.maxOrNull()
                        ?: weather.mapNotNull { it.temperatureC }.maxOrNull(),
                    precipitationProbabilityPercent = weather.mapNotNull {
                        it.precipitationProbabilityPercent
                    }.maxOrNull(),
                    precipitationMm = weather.mapNotNull { it.precipitationMm }.takeIf { it.isNotEmpty() }?.sum(),
                    windSpeedMs = weather.mapNotNull { it.windSpeedMs }.takeIf { it.isNotEmpty() }?.average(),
                    windGustMs = weather.mapNotNull { it.windGustMs }.maxOrNull(),
                    condition = weather.filter { it.condition != WeatherCondition.UNKNOWN }
                        .groupingBy { it.condition }.eachCount().maxByOrNull { it.value }?.key
                        ?: WeatherCondition.UNKNOWN,
                    confidence = confidence,
                )
            }.sortedBy { it.date }
    }
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
