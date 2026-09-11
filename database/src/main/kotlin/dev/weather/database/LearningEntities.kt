package dev.weather.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "weather_observations",
    primaryKeys = ["observationId"],
    indices = [
        Index(value = ["locationKey", "targetEpochMillis"]),
        Index(value = ["variable", "targetEpochMillis"]),
    ],
)
data class WeatherObservationEntity(
    val observationId: String,
    val sourceId: String,
    val stationId: String,
    val stationName: String,
    val stationLatitude: Double,
    val stationLongitude: Double,
    val stationDistanceMeters: Double,
    val quality: String,
    val locationKey: String,
    val targetEpochMillis: Long,
    val variable: String,
    val value: Double,
    val recordedAtEpochMillis: Long,
)

@Entity(
    tableName = "verification_samples",
    primaryKeys = ["sampleId"],
    indices = [
        Index(value = ["providerId", "variable", "horizonBucket", "regionKey", "season"]),
        Index(value = ["locationKey", "targetEpochMillis"]),
    ],
)
data class VerificationSampleEntity(
    val sampleId: String,
    val forecastRunId: String,
    val observationId: String,
    val providerId: String,
    val variable: String,
    val horizonBucket: String,
    val locationKey: String,
    val regionKey: String,
    val season: String,
    val issuedAtEpochMillis: Long,
    val targetEpochMillis: Long,
    val forecastValue: Double,
    val observationValue: Double,
    val error: Double,
    val absoluteError: Double,
    val squaredError: Double,
    val verifiedAtEpochMillis: Long,
)

@Entity(
    tableName = "model_skills",
    primaryKeys = ["providerId", "variable", "horizonBucket", "regionKey", "season"],
    indices = [Index(value = ["regionKey", "season"])],
)
data class ModelSkillEntity(
    val providerId: String,
    val variable: String,
    val horizonBucket: String,
    val regionKey: String,
    val season: String,
    val sampleCount: Long,
    val meanBias: Double,
    val meanAbsoluteError: Double,
    val meanSquaredError: Double,
    val updatedAtEpochMillis: Long,
)

data class ForecastVerificationRow(
    val forecastRunId: String,
    val providerId: String,
    val issuedAtEpochMillis: Long,
    val targetEpochMillis: Long,
    val leadTimeSeconds: Long,
    val temperatureC: Double?,
    val relativeHumidityPercent: Double?,
    val dewPointC: Double?,
    val pressureHpa: Double?,
    val precipitationProbabilityPercent: Double?,
    val precipitationMm: Double?,
    val windSpeedMs: Double?,
    val windDirectionDegrees: Double?,
    val windGustMs: Double?,
)
