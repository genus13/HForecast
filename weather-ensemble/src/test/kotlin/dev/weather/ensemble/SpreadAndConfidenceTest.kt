package dev.weather.ensemble

import com.google.common.truth.Truth.assertThat
import dev.weather.core.ForecastVariable
import org.junit.jupiter.api.Test

class SpreadAndConfidenceTest {
    @Test
    fun `range is max minus min and ignores missing values`() {
        assertThat(SpreadCalculator.range(listOf(16.1, null, 17.0, 16.5)))
            .isWithin(1e-9).of(0.9)
    }

    @Test
    fun `confidence rule is explicit about model count and range`() {
        val policy = TransparentConfidencePolicy(expectedModels = 3)

        assertThat(policy.evaluate(ForecastVariable.TEMPERATURE, 3, 1.4).band)
            .isEqualTo(ConfidenceBand.HIGH)
        assertThat(policy.evaluate(ForecastVariable.TEMPERATURE, 2, 1.4).band)
            .isEqualTo(ConfidenceBand.MEDIUM)
        assertThat(policy.evaluate(ForecastVariable.TEMPERATURE, 1, 0.0).band)
            .isEqualTo(ConfidenceBand.LOW)
    }
}
