package dev.weather.ensemble

import dev.weather.core.ForecastVariable

object SpreadCalculator {
    fun range(values: Collection<Double?>): Double? {
        val present = values.filterNotNull().filter { it.isFinite() }
        return if (present.isEmpty()) null else present.max() - present.min()
    }
}

data class ConfidenceThreshold(
    val highMaximumRange: Double,
    val mediumMaximumRange: Double,
)

class TransparentConfidencePolicy(
    private val expectedModels: Int = 3,
    private val thresholds: Map<ForecastVariable, ConfidenceThreshold> = defaultThresholds,
) {
    fun evaluate(
        variable: ForecastVariable,
        availableModels: Int,
        modelRange: Double?,
        requiredModels: Int = expectedModels,
    ): VariableConfidence {
        val threshold = thresholds.getValue(variable)
        val band = when {
            modelRange == null || availableModels < 2 -> ConfidenceBand.LOW
            availableModels >= requiredModels && modelRange <= threshold.highMaximumRange -> ConfidenceBand.HIGH
            modelRange <= threshold.mediumMaximumRange -> ConfidenceBand.MEDIUM
            else -> ConfidenceBand.LOW
        }
        val explanation = when (band) {
            ConfidenceBand.HIGH -> "$availableModels models; range within ${threshold.highMaximumRange}"
            ConfidenceBand.MEDIUM -> "$availableModels models; range within ${threshold.mediumMaximumRange}"
            ConfidenceBand.LOW -> if (availableModels < 2) {
                "Only $availableModels model available"
            } else {
                "$availableModels models; range exceeds ${threshold.mediumMaximumRange}"
            }
        }
        return VariableConfidence(band, availableModels, requiredModels, modelRange, explanation)
    }

    companion object {
        val defaultThresholds = ForecastVariable.entries.associateWith { variable ->
            when (variable) {
                ForecastVariable.TEMPERATURE,
                ForecastVariable.APPARENT_TEMPERATURE,
                ForecastVariable.MINIMUM_TEMPERATURE,
                ForecastVariable.MAXIMUM_TEMPERATURE,
                ForecastVariable.DEW_POINT -> ConfidenceThreshold(2.0, 4.0)

                ForecastVariable.PRECIPITATION_AMOUNT,
                ForecastVariable.RAIN,
                ForecastVariable.SNOWFALL -> ConfidenceThreshold(1.0, 3.0)

                ForecastVariable.PRECIPITATION_PROBABILITY -> ConfidenceThreshold(15.0, 35.0)
                ForecastVariable.WIND_SPEED,
                ForecastVariable.WIND_GUST -> ConfidenceThreshold(2.0, 5.0)

                ForecastVariable.WIND_DIRECTION -> ConfidenceThreshold(30.0, 75.0)
                ForecastVariable.HUMIDITY,
                ForecastVariable.CLOUD_COVER -> ConfidenceThreshold(10.0, 25.0)

                ForecastVariable.PRESSURE -> ConfidenceThreshold(3.0, 8.0)
                ForecastVariable.VISIBILITY -> ConfidenceThreshold(2_000.0, 6_000.0)
                ForecastVariable.UV_INDEX -> ConfidenceThreshold(1.0, 3.0)
            }
        }
    }
}
