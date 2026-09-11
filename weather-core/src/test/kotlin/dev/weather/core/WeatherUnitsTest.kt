package dev.weather.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WeatherUnitsTest {
    @Test
    fun `converts common units to internal metric units`() {
        assertThat(WeatherUnits.temperatureToCelsius(68.0, TemperatureUnit.FAHRENHEIT))
            .isWithin(0.0001).of(20.0)
        assertThat(WeatherUnits.windToMetersPerSecond(36.0, WindUnit.KILOMETERS_PER_HOUR))
            .isWithin(0.0001).of(10.0)
        assertThat(WeatherUnits.pressureToHpa(101_325.0, PressureUnit.PASCAL))
            .isWithin(0.0001).of(1013.25)
        assertThat(WeatherUnits.precipitationToMillimetres(1.0, PrecipitationUnit.INCHES))
            .isWithin(0.0001).of(25.4)
    }
}
