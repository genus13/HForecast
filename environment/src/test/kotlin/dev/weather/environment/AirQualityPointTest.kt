package dev.weather.environment

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AirQualityPointTest {
    @Test
    fun `European AQI bands use documented boundaries`() {
        assertThat(AirQualityPoint(Instant.EPOCH, europeanAqi = 12.0).level).isEqualTo(AirQualityLevel.GOOD)
        assertThat(AirQualityPoint(Instant.EPOCH, europeanAqi = 45.0).level).isEqualTo(AirQualityLevel.MODERATE)
        assertThat(AirQualityPoint(Instant.EPOCH, europeanAqi = 105.0).level)
            .isEqualTo(AirQualityLevel.EXTREMELY_POOR)
    }
}
