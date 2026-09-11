package dev.weather.ensemble

import com.google.common.truth.Truth.assertThat
import dev.weather.core.ProviderId
import org.junit.jupiter.api.Test

class ModelDiagnosticsCalculatorTest {
    @Test
    fun `calculates distribution and weight concentration diagnostics`() {
        val result = ModelDiagnosticsCalculator.calculate(
            correctedValues = mapOf(
                ProviderId.ECMWF to 10.0,
                ProviderId.ICON to 12.0,
                ProviderId.GFS to 14.0,
            ),
            normalizedWeights = mapOf(
                ProviderId.ECMWF to 0.5,
                ProviderId.ICON to 0.3,
                ProviderId.GFS to 0.2,
            ),
            consensus = 11.4,
        )

        assertThat(result.availableModels).isEqualTo(3)
        assertThat(result.arithmeticMean).isWithin(1e-9).of(12.0)
        assertThat(result.median).isWithin(1e-9).of(12.0)
        assertThat(result.populationStandardDeviation).isWithin(1e-9).of(kotlin.math.sqrt(8.0 / 3.0))
        assertThat(result.range).isWithin(1e-9).of(4.0)
        assertThat(result.consensusMinusMedian).isWithin(1e-9).of(-0.6)
        assertThat(result.effectiveModelCount).isWithin(1e-9).of(1.0 / 0.38)
        assertThat(result.dominantProvider).isEqualTo(ProviderId.ECMWF)
        assertThat(result.dominantWeight).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `renormalizes only weights belonging to finite available values`() {
        val result = ModelDiagnosticsCalculator.calculate(
            correctedValues = mapOf(
                ProviderId.ECMWF to 2.0,
                ProviderId.ICON to Double.NaN,
            ),
            normalizedWeights = mapOf(
                ProviderId.ECMWF to 0.25,
                ProviderId.ICON to 0.75,
            ),
            consensus = 2.0,
        )

        assertThat(result.availableModels).isEqualTo(1)
        assertThat(result.effectiveModelCount).isWithin(1e-9).of(1.0)
        assertThat(result.dominantWeight).isWithin(1e-9).of(1.0)
    }
}
