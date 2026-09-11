package dev.weather.ensemble

import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import dev.weather.core.WeatherCondition
import dev.weather.core.WeatherPoint
import java.time.Instant
import java.time.LocalDate

enum class ConfidenceBand(val rank: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2),
}

data class VariableConfidence(
    val band: ConfidenceBand,
    val availableModels: Int,
    val expectedModels: Int,
    val modelRange: Double?,
    val explanation: String,
)

data class ConsensusPoint(
    val timestamp: Instant,
    val consensus: WeatherPoint,
    val members: Map<ProviderId, WeatherPoint>,
    /** Range (maximum minus minimum), in the variable's internal unit. */
    val modelSpread: Map<ForecastVariable, Double>,
    val confidence: Map<ForecastVariable, VariableConfidence>,
    /** Normalized weights actually used for each available variable and model. */
    val effectiveWeights: Map<ForecastVariable, Map<ProviderId, Double>> = emptyMap(),
    /** Values after any learned bias correction and before weighted aggregation. */
    val correctedMemberValues: Map<ForecastVariable, Map<ProviderId, Double>> = emptyMap(),
) {
    val overallConfidence: ConfidenceBand
        get() = listOfNotNull(
            confidence[ForecastVariable.TEMPERATURE]?.band,
            confidence[ForecastVariable.PRECIPITATION_AMOUNT]?.band,
            confidence[ForecastVariable.WIND_SPEED]?.band,
        ).minByOrNull { it.rank } ?: ConfidenceBand.LOW
}

data class DailyConsensus(
    val date: LocalDate,
    val minimumTemperatureC: Double?,
    val maximumTemperatureC: Double?,
    val precipitationProbabilityPercent: Double?,
    val precipitationMm: Double?,
    val windSpeedMs: Double?,
    val windGustMs: Double?,
    val condition: WeatherCondition,
    val confidence: ConfidenceBand,
)

data class EnsembleResult(
    val location: GeoLocation,
    val zoneId: String,
    val generatedAt: Instant,
    val points: List<ConsensusPoint>,
    val daily: List<DailyConsensus>,
    val providerIssuedAt: Map<ProviderId, Instant>,
    /** Actual grid point returned by each provider, which can differ from the requested point. */
    val providerLocations: Map<ProviderId, GeoLocation>,
) {
    val current: ConsensusPoint?
        get() = points.minByOrNull { point ->
            kotlin.math.abs(point.timestamp.epochSecond - generatedAt.epochSecond)
        }
}
