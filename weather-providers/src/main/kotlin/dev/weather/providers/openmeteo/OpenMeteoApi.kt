package dev.weather.providers.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") model: String,
        @Query("hourly") hourly: String = HOURLY_VARIABLES,
        @Query("daily") daily: String = DAILY_VARIABLES,
        @Query("forecast_days") forecastDays: Int = 16,
        @Query("past_hours") pastHours: Int = RECENT_HISTORY_HOURS,
        @Query("cell_selection") cellSelection: String = "nearest",
        @Query("timezone") timezone: String = "auto",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("precipitation_unit") precipitationUnit: String = "mm",
        @Query("timeformat") timeFormat: String = "iso8601",
    ): OpenMeteoResponse

    companion object {
        const val RECENT_HISTORY_HOURS = 72
        const val HOURLY_VARIABLES =
            "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m," +
                "pressure_msl,precipitation_probability,precipitation,rain,snowfall," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility," +
                "uv_index,weather_code"
        const val DAILY_VARIABLES =
            "temperature_2m_min,temperature_2m_max,sunrise,sunset,weather_code"
    }
}
