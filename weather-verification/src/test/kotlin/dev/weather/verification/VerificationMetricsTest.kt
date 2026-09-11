package dev.weather.verification

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class VerificationMetricsTest {
    @Test
    fun `calculates continuous forecast metrics with forecast minus observed bias`() {
        val metrics = VerificationMetrics.continuous(
            forecasts = listOf(1.0, 3.0, 5.0),
            observations = listOf(2.0, 3.0, 3.0),
        )

        assertThat(metrics.meanAbsoluteError).isWithin(1e-9).of(1.0)
        assertThat(metrics.rootMeanSquaredError).isWithin(1e-9).of(sqrt(5.0 / 3.0))
        assertThat(metrics.meanBias).isWithin(1e-9).of(1.0 / 3.0)
        assertThat(metrics.correlation).isWithin(1e-9).of(0.8660254038)
    }

    @Test
    fun `calculates Brier score from real probabilities`() {
        val score = VerificationMetrics.brierScore(
            probabilitiesPercent = listOf(80.0, 20.0),
            outcomes = listOf(true, false),
        )

        assertThat(score).isWithin(1e-9).of(0.04)
    }

    @Test
    fun `normalizes inverse squared error weights`() {
        val weights = InverseErrorWeights.fromRmse(mapOf("A" to 1.0, "B" to 2.0), epsilon = 0.0)

        assertThat(weights.getValue("A")).isWithin(1e-9).of(0.8)
        assertThat(weights.getValue("B")).isWithin(1e-9).of(0.2)
    }
}
