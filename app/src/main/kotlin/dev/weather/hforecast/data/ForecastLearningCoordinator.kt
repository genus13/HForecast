package dev.weather.hforecast.data

import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import dev.weather.database.ForecastCacheDao
import dev.weather.database.ForecastVerificationRow
import dev.weather.database.VerificationSampleEntity
import dev.weather.database.WeatherObservationEntity
import dev.weather.database.cacheLocationKey
import dev.weather.database.toArchiveEntities
import dev.weather.ensemble.AdaptiveEnsembleController
import dev.weather.ensemble.EnsembleLearningSummary
import dev.weather.ensemble.EnsembleResult
import dev.weather.ensemble.LearnedSkill
import dev.weather.ensemble.horizonBucket
import dev.weather.ensemble.season
import dev.weather.ensemble.skillRegionKey
import dev.weather.verification.ObservationProvider
import dev.weather.verification.ReferenceObservation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForecastLearningCoordinator @Inject constructor(
    private val dao: ForecastCacheDao,
    private val observations: ObservationProvider,
    private val adaptiveModel: AdaptiveEnsembleController,
) {
    private val mutex = Mutex()
    private val lastObservationFetch = mutableMapOf<String, Instant>()

    suspend fun prepare(
        location: GeoLocation,
        fetchObservations: Boolean,
        now: Instant = Instant.now(),
    ): EnsembleLearningSummary = mutex.withLock {
        val locationKey = location.cacheLocationKey()
        var latest: List<ReferenceObservation> = emptyList()
        var error: String? = null
        val shouldFetch = fetchObservations && observations.supports(location) &&
            lastObservationFetch[locationKey]?.let { Duration.between(it, now) >= OBSERVATION_REFRESH } != false
        if (shouldFetch) {
            runCatching { observations.latest(location) }
                .onSuccess { received ->
                    latest = received
                    lastObservationFetch[locationKey] = now
                    received.forEach { observation -> record(location, observation, now) }
                    prune(now)
                }
                .onFailure { failure -> error = failure.message ?: "Observation update failed" }
        }

        val regionKey = location.skillRegionKey()
        val skills = dao.allSkills().mapNotNull { entity ->
            val variable = runCatching { ForecastVariable.valueOf(entity.variable) }.getOrNull()
                ?: return@mapNotNull null
            LearnedSkill(
                providerId = entity.providerId,
                variable = variable,
                horizonBucket = entity.horizonBucket,
                regionKey = entity.regionKey,
                season = entity.season,
                sampleCount = entity.sampleCount,
                meanBias = entity.meanBias,
                meanAbsoluteError = entity.meanAbsoluteError,
                rootMeanSquaredError = sqrt(entity.meanSquaredError.coerceAtLeast(0.0)),
            )
        }
        adaptiveModel.replaceSkills(skills)
        val regionalPhysicalSkills = skills.filter { skill ->
            skill.regionKey == regionKey &&
                runCatching { dev.weather.core.ProviderId.valueOf(skill.providerId) }.isSuccess
        }
        val physicalSkills = regionalPhysicalSkills.filter { it.season == now.season() }
        val nearest = latest.minByOrNull { it.distanceMeters }
        val storedObservation = dao.latestObservation(locationKey)
        EnsembleLearningSummary(
            referenceSource = if (observations.supports(location)) {
                "SMHI quality-controlled station observations"
            } else {
                "No verified observation source for this location"
            },
            latestObservationTime = latest.maxOfOrNull { it.targetTime }
                ?: storedObservation?.targetEpochMillis?.let(Instant::ofEpochMilli),
            verifiedComparisons = dao.verificationCount(regionKey),
            learnedBuckets = physicalSkills.size,
            activeBuckets = physicalSkills.count { it.sampleCount >= MINIMUM_ADAPTIVE_SAMPLES },
            observationStation = nearest?.stationName ?: storedObservation?.stationName,
            observationDistanceKm = nearest?.distanceMeters?.div(1_000.0)
                ?: storedObservation?.stationDistanceMeters?.div(1_000.0),
            lastError = error,
            skills = regionalPhysicalSkills,
        )
    }

    suspend fun archiveConsensus(
        result: EnsembleResult,
        providerId: String,
        modelName: String,
    ) {
        val (run, points) = toArchiveEntities(
            providerId = providerId,
            modelName = modelName,
            modelInitializationTime = null,
            issuedAt = result.generatedAt,
            forecastLocation = result.location,
            requestedLocation = result.location,
            zoneId = result.zoneId,
            points = result.points.map { it.consensus },
        )
        dao.archive(run, points)
    }

    private suspend fun record(location: GeoLocation, observation: ReferenceObservation, now: Instant) {
        val locationKey = location.cacheLocationKey()
        val observationId = listOf(
            observation.sourceId,
            observation.stationId,
            observation.variable.name,
            observation.targetTime.toEpochMilli(),
            locationKey,
        ).joinToString("|")
        dao.insertObservation(
            WeatherObservationEntity(
                observationId = observationId,
                sourceId = observation.sourceId,
                stationId = observation.stationId,
                stationName = observation.stationName,
                stationLatitude = observation.stationLatitude,
                stationLongitude = observation.stationLongitude,
                stationDistanceMeters = observation.distanceMeters,
                quality = observation.quality,
                locationKey = locationKey,
                targetEpochMillis = observation.targetTime.toEpochMilli(),
                variable = observation.variable.name,
                value = observation.value,
                recordedAtEpochMillis = now.toEpochMilli(),
            ),
        )
        val tolerance = Duration.ofMinutes(5).toMillis()
        dao.forecastsForVerification(
            locationKey,
            observation.targetTime.toEpochMilli() - tolerance,
            observation.targetTime.toEpochMilli() + tolerance,
        ).forEach { row ->
            val forecastValue = row.valueFor(observation.variable) ?: return@forEach
            val error = verificationError(observation.variable, forecastValue, observation.value)
            val sample = VerificationSampleEntity(
                sampleId = "${row.forecastRunId}|$observationId",
                forecastRunId = row.forecastRunId,
                observationId = observationId,
                providerId = row.providerId,
                variable = observation.variable.name,
                horizonBucket = Duration.ofSeconds(row.leadTimeSeconds).horizonBucket(),
                locationKey = locationKey,
                regionKey = location.skillRegionKey(),
                season = observation.targetTime.season(),
                issuedAtEpochMillis = row.issuedAtEpochMillis,
                targetEpochMillis = row.targetEpochMillis,
                forecastValue = forecastValue,
                observationValue = observation.value,
                error = error,
                absoluteError = abs(error),
                squaredError = error * error,
                verifiedAtEpochMillis = now.toEpochMilli(),
            )
            dao.recordVerification(sample)
        }
    }

    private suspend fun prune(now: Instant) {
        dao.deleteArchiveBefore(now.minus(FORECAST_ARCHIVE_RETENTION).toEpochMilli())
        val verificationCutoff = now.minus(VERIFICATION_DETAIL_RETENTION).toEpochMilli()
        dao.deleteObservationsBefore(verificationCutoff)
        dao.deleteSamplesBefore(verificationCutoff)
    }

    companion object {
        private val OBSERVATION_REFRESH = Duration.ofMinutes(45)
        private val FORECAST_ARCHIVE_RETENTION = Duration.ofDays(60)
        private val VERIFICATION_DETAIL_RETENTION = Duration.ofDays(180)
        private const val MINIMUM_ADAPTIVE_SAMPLES = 24L
    }
}

private fun ForecastVerificationRow.valueFor(variable: ForecastVariable): Double? = when (variable) {
    ForecastVariable.TEMPERATURE -> temperatureC
    ForecastVariable.HUMIDITY -> relativeHumidityPercent
    ForecastVariable.DEW_POINT -> dewPointC
    ForecastVariable.PRESSURE -> pressureHpa
    ForecastVariable.PRECIPITATION_PROBABILITY -> precipitationProbabilityPercent
    ForecastVariable.PRECIPITATION_AMOUNT -> precipitationMm
    ForecastVariable.WIND_SPEED -> windSpeedMs
    ForecastVariable.WIND_DIRECTION -> windDirectionDegrees
    ForecastVariable.WIND_GUST -> windGustMs
    else -> null
}

private fun verificationError(variable: ForecastVariable, forecast: Double, observation: Double): Double =
    if (variable == ForecastVariable.WIND_DIRECTION) {
        (forecast - observation + 540.0) % 360.0 - 180.0
    } else {
        forecast - observation
    }
