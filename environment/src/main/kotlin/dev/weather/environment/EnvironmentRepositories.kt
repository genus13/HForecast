package dev.weather.environment

import dev.weather.core.GeoLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.cos

class AirQualityRepository private constructor(
    private val api: AirQualityApi = EnvironmentClients.airQualityApi(),
    private val clock: () -> Instant = Instant::now,
) {
    constructor() : this(EnvironmentClients.airQualityApi(), Instant::now)

    suspend fun forecast(location: GeoLocation): AirQualityForecast {
        val response = api.forecast(location.latitude, location.longitude)
        val zone = runCatching { ZoneId.of(response.timezone) }
            .getOrElse { ZoneOffset.ofTotalSeconds(response.utcOffsetSeconds) }
        val current = response.current.toPoint(parseInstant(response.current.time, zone))
        val hourly = response.hourly.time.mapIndexedNotNull { index, rawTime ->
            runCatching { parseInstant(rawTime, zone) }.getOrNull()?.let { timestamp ->
                AirQualityPoint(
                    timestamp = timestamp,
                    europeanAqi = response.hourly.europeanAqi.valueAt(index),
                    pm10 = response.hourly.pm10.valueAt(index),
                    pm25 = response.hourly.pm25.valueAt(index),
                    nitrogenDioxide = response.hourly.nitrogenDioxide.valueAt(index),
                    ozone = response.hourly.ozone.valueAt(index),
                    uvIndex = response.hourly.uvIndex.valueAt(index),
                    birchPollen = response.hourly.birchPollen.valueAt(index),
                    grassPollen = response.hourly.grassPollen.valueAt(index),
                )
            }
        }
        return AirQualityForecast(
            requestedLocation = location,
            gridLocation = GeoLocation(response.latitude, response.longitude, altitudeMeters = response.elevation),
            zoneId = zone.id,
            fetchedAt = clock(),
            current = current,
            hourly = hourly,
        )
    }

    private fun AirQualityValuesDto.toPoint(timestamp: Instant) = AirQualityPoint(
        timestamp = timestamp,
        europeanAqi = europeanAqi,
        pm10 = pm10,
        pm25 = pm25,
        carbonMonoxide = carbonMonoxide,
        nitrogenDioxide = nitrogenDioxide,
        sulphurDioxide = sulphurDioxide,
        ozone = ozone,
        aerosolOpticalDepth = aerosolOpticalDepth,
        dust = dust,
        uvIndex = uvIndex,
        alderPollen = alderPollen,
        birchPollen = birchPollen,
        grassPollen = grassPollen,
        mugwortPollen = mugwortPollen,
    )
}

class SpaceWeatherRepository private constructor(
    private val api: SpaceWeatherApi = EnvironmentClients.spaceWeatherApi(),
) {
    constructor() : this(EnvironmentClients.spaceWeatherApi())

    suspend fun aurora(location: GeoLocation): AuroraForecast = supervisorScope {
        val auroraDeferred = async { api.aurora() }
        val currentKp = async { runCatching { api.currentKp() }.getOrDefault(emptyList()) }
        val kpForecast = async { runCatching { api.kpForecast() }.getOrDefault(emptyList()) }
        val wind = async { runCatching { api.solarWindSpeed() }.getOrDefault(emptyList()) }
        val field = async { runCatching { api.magneticField() }.getOrDefault(emptyList()) }
        val flux = async { runCatching { api.radioFlux() }.getOrDefault(emptyList()) }
        val scales = async { runCatching { api.noaaScales() }.getOrDefault(emptyMap()) }

        val response = auroraDeferred.await()
        val cells = response.coordinates.mapNotNull { raw ->
            if (raw.size < 3) null else AuroraGridCell(raw[0], raw[1], raw[2])
        }
        val nearest = cells.minByOrNull { cell ->
            val longitudeDelta = ((cell.longitude - location.longitude + 540.0) % 360.0) - 180.0
            val scaledLongitude = longitudeDelta * cos(Math.toRadians(location.latitude))
            val latitudeDelta = cell.latitude - location.latitude
            scaledLongitude * scaledLongitude + latitudeDelta * latitudeDelta
        } ?: error("NOAA OVATION returned no grid cells")
        val currentScales = scales.await()["0"]
        AuroraForecast(
            observationTime = parseUtc(response.observationTime),
            forecastTime = parseUtc(response.forecastTime),
            localProbabilityPercent = nearest.probabilityPercent,
            nearestGridLocation = GeoLocation(nearest.latitude, normalizeLongitude(nearest.longitude)),
            northernGrid = cells.filter { cell ->
                cell.latitude >= 40.0 && cell.probabilityPercent > 0.0 &&
                    cell.longitude.toInt() % 2 == 0 && cell.latitude.toInt() % 2 == 0
            },
            solarActivity = SolarActivity(
                currentKp = currentKp.await().lastOrNull { it.kp != null }?.kp,
                kpForecast = kpForecast.await().mapNotNull { dto ->
                    val value = dto.kp ?: return@mapNotNull null
                    KpForecastPoint(parseUtc(dto.time), value, dto.observed ?: "unknown", dto.noaaScale)
                },
                solarWindSpeedKmS = wind.await().firstOrNull()?.speed,
                interplanetaryFieldBtNt = field.await().firstOrNull()?.bt,
                interplanetaryFieldBzNt = field.await().firstOrNull()?.bz,
                solarRadioFluxSfu = flux.await().firstOrNull()?.flux,
                geomagneticStormScale = currentScales?.geomagnetic?.scale,
                radioBlackoutScale = currentScales?.radioBlackout?.scale,
                solarRadiationScale = currentScales?.solarRadiation?.scale,
            ),
        )
    }
}

private object EnvironmentClients {
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(45))
        .callTimeout(Duration.ofSeconds(60))
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "HForecast/0.3 personal-use").build())
        }
        .build()

    fun airQualityApi(): AirQualityApi = retrofit("https://air-quality-api.open-meteo.com/")
        .create(AirQualityApi::class.java)

    fun spaceWeatherApi(): SpaceWeatherApi = retrofit("https://services.swpc.noaa.gov/")
        .create(SpaceWeatherApi::class.java)

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

private fun parseInstant(value: String, zone: ZoneId): Instant =
    LocalDateTime.parse(value).atZone(zone).toInstant()

private fun parseUtc(value: String): Instant = runCatching { Instant.parse(value) }
    .getOrElse { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }

private fun normalizeLongitude(value: Double): Double = ((value + 540.0) % 360.0) - 180.0
private fun List<Double?>?.valueAt(index: Int): Double? = this?.getOrNull(index)
