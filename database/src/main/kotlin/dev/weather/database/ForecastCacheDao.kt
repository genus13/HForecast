package dev.weather.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ForecastCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: ForecastRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<ForecastPointEntity>)

    /** Append-only insertion: this preserves the forecast that was actually available at issue time. */
    @Transaction
    suspend fun archive(run: ForecastRunEntity, points: List<ForecastPointEntity>) {
        insertRun(run)
        insertPoints(points)
    }

    @Transaction
    @Query(
        """
        SELECT * FROM forecast_runs
        WHERE locationKey = :locationKey
          AND providerId NOT LIKE 'HFORECAST_%'
        ORDER BY issuedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun forecastsForLocation(
        locationKey: String,
        limit: Int = 100,
    ): List<ForecastRunWithPoints>

    /**
     * The issue-before-target predicate is also the hard boundary that keeps recently downloaded retrospective
     * model output out of adaptive verification.
     */
    @Query(
        """
        SELECT r.runId AS forecastRunId,
               r.providerId AS providerId,
               r.issuedAtEpochMillis AS issuedAtEpochMillis,
               p.targetEpochMillis AS targetEpochMillis,
               p.leadTimeSeconds AS leadTimeSeconds,
               p.temperatureC AS temperatureC,
               p.relativeHumidityPercent AS relativeHumidityPercent,
               p.dewPointC AS dewPointC,
               p.pressureHpa AS pressureHpa,
               p.precipitationProbabilityPercent AS precipitationProbabilityPercent,
               p.precipitationMm AS precipitationMm,
               p.windSpeedMs AS windSpeedMs,
               p.windDirectionDegrees AS windDirectionDegrees,
               p.windGustMs AS windGustMs
        FROM forecast_runs r
        INNER JOIN forecast_points p ON p.runId = r.runId
        WHERE r.locationKey = :locationKey
          AND p.targetEpochMillis BETWEEN :minimumTargetEpochMillis AND :maximumTargetEpochMillis
          AND r.issuedAtEpochMillis < p.targetEpochMillis
        ORDER BY r.issuedAtEpochMillis ASC
        """,
    )
    suspend fun forecastsForVerification(
        locationKey: String,
        minimumTargetEpochMillis: Long,
        maximumTargetEpochMillis: Long,
    ): List<ForecastVerificationRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservation(observation: WeatherObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVerificationSample(sample: VerificationSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(skill: ModelSkillEntity)

    @Query(
        """
        SELECT * FROM model_skills
        WHERE providerId = :providerId
          AND variable = :variable
          AND horizonBucket = :horizonBucket
          AND regionKey = :regionKey
          AND season = :season
        LIMIT 1
        """,
    )
    suspend fun skill(
        providerId: String,
        variable: String,
        horizonBucket: String,
        regionKey: String,
        season: String,
    ): ModelSkillEntity?

    @Query("SELECT * FROM model_skills")
    suspend fun allSkills(): List<ModelSkillEntity>

    @Query("SELECT COUNT(*) FROM verification_samples WHERE regionKey = :regionKey")
    suspend fun verificationCount(regionKey: String): Long

    @Query(
        """
        SELECT * FROM weather_observations
        WHERE locationKey = :locationKey
        ORDER BY targetEpochMillis DESC, stationDistanceMeters ASC
        LIMIT 1
        """,
    )
    suspend fun latestObservation(locationKey: String): WeatherObservationEntity?

    @Query("DELETE FROM forecast_runs WHERE issuedAtEpochMillis < :cutoffEpochMillis")
    suspend fun deleteArchiveBefore(cutoffEpochMillis: Long)

    @Query("DELETE FROM weather_observations WHERE targetEpochMillis < :cutoffEpochMillis")
    suspend fun deleteObservationsBefore(cutoffEpochMillis: Long)

    @Query("DELETE FROM verification_samples WHERE targetEpochMillis < :cutoffEpochMillis")
    suspend fun deleteSamplesBefore(cutoffEpochMillis: Long)

    @Transaction
    suspend fun recordVerification(sample: VerificationSampleEntity, alpha: Double = 0.05): Boolean {
        if (insertVerificationSample(sample) == -1L) return false
        val previous = skill(
            sample.providerId,
            sample.variable,
            sample.horizonBucket,
            sample.regionKey,
            sample.season,
        )
        val blend = if (previous == null) 1.0 else alpha.coerceIn(0.001, 1.0)
        fun update(old: Double?, value: Double): Double = old?.let { (1.0 - blend) * it + blend * value } ?: value
        upsertSkill(
            ModelSkillEntity(
                providerId = sample.providerId,
                variable = sample.variable,
                horizonBucket = sample.horizonBucket,
                regionKey = sample.regionKey,
                season = sample.season,
                sampleCount = (previous?.sampleCount ?: 0) + 1,
                meanBias = update(previous?.meanBias, sample.error),
                meanAbsoluteError = update(previous?.meanAbsoluteError, sample.absoluteError),
                meanSquaredError = update(previous?.meanSquaredError, sample.squaredError),
                updatedAtEpochMillis = sample.verifiedAtEpochMillis,
            ),
        )
        return true
    }
}
