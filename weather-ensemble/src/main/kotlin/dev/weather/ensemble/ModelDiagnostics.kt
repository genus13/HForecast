package dev.weather.ensemble

import dev.weather.core.ProviderId
import kotlin.math.sqrt

/** Inspectable diagnostics for one variable at one aligned forecast timestamp. */
data class ModelDiagnostics(
    val availableModels: Int,
    val arithmeticMean: Double?,
    val median: Double?,
    val populationStandardDeviation: Double?,
    val range: Double?,
    val consensusMinusMedian: Double?,
    val effectiveModelCount: Double?,
    val dominantProvider: ProviderId?,
    val dominantWeight: Double?,
)

object ModelDiagnosticsCalculator {
    fun calculate(
        correctedValues: Map<ProviderId, Double>,
        normalizedWeights: Map<ProviderId, Double>,
        consensus: Double?,
    ): ModelDiagnostics {
        val finite = correctedValues.filterValues(Double::isFinite)
        val sorted = finite.values.sorted()
        val mean = sorted.takeIf { it.isNotEmpty() }?.average()
        val median = when {
            sorted.isEmpty() -> null
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        val standardDeviation = mean?.let { center ->
            sqrt(sorted.sumOf { value -> (value - center) * (value - center) } / sorted.size)
        }
        val usableWeights = normalizedWeights
            .filterKeys { it in finite }
            .filterValues { it.isFinite() && it > 0.0 }
        val weightTotal = usableWeights.values.sum()
        val renormalizedWeights = if (weightTotal > 0.0) {
            usableWeights.mapValues { it.value / weightTotal }
        } else {
            emptyMap()
        }
        val dominant = renormalizedWeights.maxByOrNull { it.value }
        val effectiveCount = renormalizedWeights.values
            .sumOf { it * it }
            .takeIf { it > 0.0 }
            ?.let { 1.0 / it }

        return ModelDiagnostics(
            availableModels = finite.size,
            arithmeticMean = mean,
            median = median,
            populationStandardDeviation = standardDeviation,
            range = sorted.takeIf { it.isNotEmpty() }?.let { it.last() - it.first() },
            consensusMinusMedian = if (consensus != null && consensus.isFinite() && median != null) {
                consensus - median
            } else {
                null
            },
            effectiveModelCount = effectiveCount,
            dominantProvider = dominant?.key,
            dominantWeight = dominant?.value,
        )
    }
}
