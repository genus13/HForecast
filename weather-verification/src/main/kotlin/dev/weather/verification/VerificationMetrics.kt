package dev.weather.verification

import kotlin.math.sqrt

data class ContinuousMetrics(
    val sampleCount: Int,
    val meanAbsoluteError: Double,
    val rootMeanSquaredError: Double,
    /** Mean forecast minus observation. Positive means the model over-forecast. */
    val meanBias: Double,
    val correlation: Double?,
)

object VerificationMetrics {
    fun continuous(forecasts: List<Double>, observations: List<Double>): ContinuousMetrics {
        require(forecasts.size == observations.size) { "Forecast and observation sizes differ" }
        require(forecasts.isNotEmpty()) { "At least one pair is required" }
        val pairs = forecasts.zip(observations).filter { (forecast, observation) ->
            forecast.isFinite() && observation.isFinite()
        }
        require(pairs.isNotEmpty()) { "At least one finite pair is required" }
        val errors = pairs.map { (forecast, observation) -> forecast - observation }
        return ContinuousMetrics(
            sampleCount = pairs.size,
            meanAbsoluteError = errors.sumOf { kotlin.math.abs(it) } / errors.size,
            rootMeanSquaredError = sqrt(errors.sumOf { it * it } / errors.size),
            meanBias = errors.average(),
            correlation = pearson(
                pairs.map { it.first },
                pairs.map { it.second },
            ),
        )
    }

    fun brierScore(probabilitiesPercent: List<Double>, outcomes: List<Boolean>): Double {
        require(probabilitiesPercent.size == outcomes.size) { "Probability and outcome sizes differ" }
        require(probabilitiesPercent.isNotEmpty()) { "At least one pair is required" }
        probabilitiesPercent.forEach { require(it in 0.0..100.0) }
        return probabilitiesPercent.zip(outcomes).sumOf { (probability, occurred) ->
            val error = probability / 100.0 - if (occurred) 1.0 else 0.0
            error * error
        } / probabilitiesPercent.size
    }

    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.size < 2) return null
        val meanX = x.average()
        val meanY = y.average()
        val numerator = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) }
        val denominatorX = x.sumOf { (it - meanX) * (it - meanX) }
        val denominatorY = y.sumOf { (it - meanY) * (it - meanY) }
        val denominator = sqrt(denominatorX * denominatorY)
        return if (denominator == 0.0) null else numerator / denominator
    }
}

/** Initial skill rule. The caller still chooses variable, horizon, region and season buckets. */
object InverseErrorWeights {
    fun fromRmse(rmseByModel: Map<String, Double>, epsilon: Double = 1e-6): Map<String, Double> {
        require(rmseByModel.isNotEmpty())
        require(rmseByModel.values.all { it >= 0.0 && it.isFinite() })
        val raw = rmseByModel.mapValues { (_, rmse) -> 1.0 / (rmse * rmse + epsilon) }
        val total = raw.values.sum()
        return raw.mapValues { (_, value) -> value / total }
    }
}
