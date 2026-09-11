package dev.weather.environment

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

internal interface AirQualityApi {
    @GET("v1/air-quality")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_VARIABLES,
        @Query("hourly") hourly: String = HOURLY_VARIABLES,
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto",
        @Query("cell_selection") cellSelection: String = "nearest",
    ): AirQualityResponseDto

    companion object {
        const val CURRENT_VARIABLES =
            "european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone," +
                "aerosol_optical_depth,dust,uv_index,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen"
        const val HOURLY_VARIABLES =
            "european_aqi,pm10,pm2_5,nitrogen_dioxide,ozone,uv_index,birch_pollen,grass_pollen"
    }
}

internal data class AirQualityResponseDto(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timezone: String,
    @SerializedName("utc_offset_seconds") val utcOffsetSeconds: Int,
    val current: AirQualityValuesDto,
    val hourly: AirQualityHourlyDto,
)

internal data class AirQualityValuesDto(
    val time: String,
    @SerializedName("european_aqi") val europeanAqi: Double? = null,
    val pm10: Double? = null,
    @SerializedName("pm2_5") val pm25: Double? = null,
    @SerializedName("carbon_monoxide") val carbonMonoxide: Double? = null,
    @SerializedName("nitrogen_dioxide") val nitrogenDioxide: Double? = null,
    @SerializedName("sulphur_dioxide") val sulphurDioxide: Double? = null,
    val ozone: Double? = null,
    @SerializedName("aerosol_optical_depth") val aerosolOpticalDepth: Double? = null,
    val dust: Double? = null,
    @SerializedName("uv_index") val uvIndex: Double? = null,
    @SerializedName("alder_pollen") val alderPollen: Double? = null,
    @SerializedName("birch_pollen") val birchPollen: Double? = null,
    @SerializedName("grass_pollen") val grassPollen: Double? = null,
    @SerializedName("mugwort_pollen") val mugwortPollen: Double? = null,
)

internal data class AirQualityHourlyDto(
    val time: List<String> = emptyList(),
    @SerializedName("european_aqi") val europeanAqi: List<Double?>? = null,
    val pm10: List<Double?>? = null,
    @SerializedName("pm2_5") val pm25: List<Double?>? = null,
    @SerializedName("nitrogen_dioxide") val nitrogenDioxide: List<Double?>? = null,
    val ozone: List<Double?>? = null,
    @SerializedName("uv_index") val uvIndex: List<Double?>? = null,
    @SerializedName("birch_pollen") val birchPollen: List<Double?>? = null,
    @SerializedName("grass_pollen") val grassPollen: List<Double?>? = null,
)

internal interface SpaceWeatherApi {
    @GET("json/ovation_aurora_latest.json")
    suspend fun aurora(): AuroraResponseDto

    @GET("products/noaa-planetary-k-index.json")
    suspend fun currentKp(): List<CurrentKpDto>

    @GET("products/noaa-planetary-k-index-forecast.json")
    suspend fun kpForecast(): List<KpForecastDto>

    @GET("products/summary/solar-wind-speed.json")
    suspend fun solarWindSpeed(): List<SolarWindSpeedDto>

    @GET("products/summary/solar-wind-mag-field.json")
    suspend fun magneticField(): List<MagneticFieldDto>

    @GET("products/summary/10cm-flux.json")
    suspend fun radioFlux(): List<RadioFluxDto>

    @GET("products/noaa-scales.json")
    suspend fun noaaScales(): Map<String, NoaaScalesDto>
}

internal data class AuroraResponseDto(
    @SerializedName("Observation Time") val observationTime: String,
    @SerializedName("Forecast Time") val forecastTime: String,
    val coordinates: List<List<Double>>,
)

internal data class CurrentKpDto(
    @SerializedName("time_tag") val time: String,
    @SerializedName("Kp") val kp: Double?,
)

internal data class KpForecastDto(
    @SerializedName("time_tag") val time: String,
    val kp: Double?,
    val observed: String? = null,
    @SerializedName("noaa_scale") val noaaScale: String? = null,
)

internal data class SolarWindSpeedDto(@SerializedName("proton_speed") val speed: Double?)
internal data class MagneticFieldDto(val bt: Double?, @SerializedName("bz_gsm") val bz: Double?)
internal data class RadioFluxDto(val flux: Double?)
internal data class ScaleValueDto(@SerializedName("Scale") val scale: String? = null)
internal data class NoaaScalesDto(
    @SerializedName("G") val geomagnetic: ScaleValueDto? = null,
    @SerializedName("R") val radioBlackout: ScaleValueDto? = null,
    @SerializedName("S") val solarRadiation: ScaleValueDto? = null,
)
