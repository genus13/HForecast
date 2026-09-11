package dev.weather.providers.openmeteo

import com.google.gson.annotations.SerializedName

data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timezone: String,
    @SerializedName("utc_offset_seconds") val utcOffsetSeconds: Int,
    val hourly: HourlyDto,
    val daily: DailyDto? = null,
)

data class HourlyDto(
    val time: List<String> = emptyList(),
    @SerializedName("temperature_2m") val temperature: List<Double?>? = null,
    @SerializedName("apparent_temperature") val apparentTemperature: List<Double?>? = null,
    @SerializedName("relative_humidity_2m") val relativeHumidity: List<Double?>? = null,
    @SerializedName("dew_point_2m") val dewPoint: List<Double?>? = null,
    @SerializedName("pressure_msl") val meanSeaLevelPressure: List<Double?>? = null,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Double?>? = null,
    val precipitation: List<Double?>? = null,
    val rain: List<Double?>? = null,
    /** Open-Meteo returns snowfall depth in centimetres. */
    val snowfall: List<Double?>? = null,
    @SerializedName("wind_speed_10m") val windSpeed: List<Double?>? = null,
    @SerializedName("wind_direction_10m") val windDirection: List<Double?>? = null,
    @SerializedName("wind_gusts_10m") val windGusts: List<Double?>? = null,
    @SerializedName("cloud_cover") val cloudCover: List<Double?>? = null,
    val visibility: List<Double?>? = null,
    @SerializedName("uv_index") val uvIndex: List<Double?>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int?>? = null,
)

data class DailyDto(
    val time: List<String> = emptyList(),
    @SerializedName("temperature_2m_min") val minimumTemperature: List<Double?>? = null,
    @SerializedName("temperature_2m_max") val maximumTemperature: List<Double?>? = null,
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int?>? = null,
)
