package dev.weather.core

object WeatherUnits {
    fun temperatureToCelsius(value: Double, unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.CELSIUS -> value
        TemperatureUnit.FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
        TemperatureUnit.KELVIN -> value - 273.15
    }

    fun windToMetersPerSecond(value: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.METERS_PER_SECOND -> value
        WindUnit.KILOMETERS_PER_HOUR -> value / 3.6
        WindUnit.MILES_PER_HOUR -> value * 0.44704
        WindUnit.KNOTS -> value * 0.514444
    }

    fun pressureToHpa(value: Double, unit: PressureUnit): Double = when (unit) {
        PressureUnit.HECTOPASCAL -> value
        PressureUnit.PASCAL -> value / 100.0
        PressureUnit.INCHES_OF_MERCURY -> value * 33.8638866667
    }

    fun precipitationToMillimetres(value: Double, unit: PrecipitationUnit): Double = when (unit) {
        PrecipitationUnit.MILLIMETRES -> value
        PrecipitationUnit.INCHES -> value * 25.4
    }
}

enum class TemperatureUnit { CELSIUS, FAHRENHEIT, KELVIN }
enum class WindUnit { METERS_PER_SECOND, KILOMETERS_PER_HOUR, MILES_PER_HOUR, KNOTS }
enum class PressureUnit { HECTOPASCAL, PASCAL, INCHES_OF_MERCURY }
enum class PrecipitationUnit { MILLIMETRES, INCHES }
