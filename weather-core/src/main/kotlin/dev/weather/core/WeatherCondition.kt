package dev.weather.core

enum class WeatherCondition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    FREEZING_DRIZZLE,
    RAIN,
    FREEZING_RAIN,
    SNOW,
    RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    UNKNOWN,
}

enum class ProbabilitySource {
    /** No probability was supplied. A probability must not be invented from model disagreement. */
    NONE,

    /** Supplied or derived by the upstream provider/model product. */
    PROVIDER_DERIVED,

    /** Calculated from genuine ensemble members. */
    ENSEMBLE_MEMBERS,
}
