package dev.weather.verification

import com.google.gson.annotations.SerializedName
import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SmhiObservationProvider(
    private val api: SmhiObservationApi = SmhiObservationClientFactory.create(),
    private val clock: () -> Instant = Instant::now,
    private val maximumStationDistanceMeters: Double = 100_000.0,
) : ObservationProvider {
    override val sourceId: String = "SMHI_METOBS"

    override fun supports(location: GeoLocation): Boolean =
        location.latitude in 55.0..70.0 && location.longitude in 10.0..25.0

    override suspend fun latest(location: GeoLocation): List<ReferenceObservation> {
        if (!supports(location)) return emptyList()
        val now = clock()
        return coroutineScope {
            PARAMETERS.map { (parameter, variable) ->
                async {
                    runCatching { api.latestHour(parameter) }.getOrNull()
                        ?.nearestObservation(location, sourceId, variable, now, maximumStationDistanceMeters)
                }
            }.awaitAll().filterNotNull().flatMap { observation ->
                if (observation.variable == ForecastVariable.PRECIPITATION_AMOUNT) {
                    listOf(
                        observation,
                        observation.copy(
                            variable = ForecastVariable.PRECIPITATION_PROBABILITY,
                            value = if (observation.value >= RAIN_EVENT_THRESHOLD_MM) 100.0 else 0.0,
                        ),
                    )
                } else {
                    listOf(observation)
                }
            }
        }
    }

    companion object {
        private const val RAIN_EVENT_THRESHOLD_MM = 0.1
        private val PARAMETERS = listOf(
            "1" to ForecastVariable.TEMPERATURE,
            "6" to ForecastVariable.HUMIDITY,
            "39" to ForecastVariable.DEW_POINT,
            "9" to ForecastVariable.PRESSURE,
            "7" to ForecastVariable.PRECIPITATION_AMOUNT,
            "4" to ForecastVariable.WIND_SPEED,
            "3" to ForecastVariable.WIND_DIRECTION,
            "21" to ForecastVariable.WIND_GUST,
        )
    }
}

interface SmhiObservationApi {
    @GET("api/version/1.0/parameter/{parameter}/station-set/all/period/latest-hour/data.json")
    suspend fun latestHour(@Path("parameter") parameter: String): SmhiObservationResponse
}

object SmhiObservationClientFactory {
    fun create(): SmhiObservationApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "HForecast/0.4 (personal weather verification)")
                        .build(),
                )
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        return Retrofit.Builder()
            .baseUrl("https://opendata-download-metobs.smhi.se/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmhiObservationApi::class.java)
    }
}

data class SmhiObservationResponse(
    val station: List<SmhiStationDto> = emptyList(),
)

data class SmhiStationDto(
    val key: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("value") val values: List<SmhiValueDto> = emptyList(),
)

data class SmhiValueDto(
    val date: Long,
    val value: String,
    val quality: String = "",
)

internal fun SmhiObservationResponse.nearestObservation(
    location: GeoLocation,
    sourceId: String,
    variable: ForecastVariable,
    now: Instant,
    maximumDistanceMeters: Double,
): ReferenceObservation? = station.mapNotNull { station ->
    val latest = station.values
        .filter { it.quality.uppercase() in setOf("G", "Y") }
        .maxByOrNull { it.date }
        ?: return@mapNotNull null
    val value = latest.value.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@mapNotNull null
    val target = Instant.ofEpochMilli(latest.date)
    if (target > now.plus(Duration.ofMinutes(5)) || Duration.between(target, now) > Duration.ofHours(3)) {
        return@mapNotNull null
    }
    val distance = haversineMeters(location.latitude, location.longitude, station.latitude, station.longitude)
    ReferenceObservation(
        sourceId = sourceId,
        stationId = station.key,
        stationName = station.name,
        stationLatitude = station.latitude,
        stationLongitude = station.longitude,
        distanceMeters = distance,
        targetTime = target,
        variable = variable,
        value = value,
        quality = latest.quality,
    )
}.filter { it.distanceMeters <= maximumDistanceMeters }
    .minWithOrNull(compareBy<ReferenceObservation> { if (it.quality.uppercase() == "G") 0 else 1 }
        .thenBy { it.distanceMeters })

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = lat1 * PI / 180.0
    val phi2 = lat2 * PI / 180.0
    val deltaPhi = (lat2 - lat1) * PI / 180.0
    val deltaLambda = (lon2 - lon1) * PI / 180.0
    val a = sin(deltaPhi / 2.0) * sin(deltaPhi / 2.0) +
        cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0) * sin(deltaLambda / 2.0)
    return 6_371_000.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
}
