package dev.weather.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "forecast_runs",
    indices = [Index(value = ["providerId", "locationKey", "issuedAtEpochMillis"])],
)
data class ForecastRunEntity(
    @PrimaryKey val runId: String,
    val providerId: String,
    val modelName: String,
    val modelInitializationEpochMillis: Long?,
    /** The time this exact forecast was available to the app. */
    val issuedAtEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val displayName: String?,
    val locationKey: String,
    val zoneId: String,
)

@Entity(
    tableName = "forecast_points",
    primaryKeys = ["runId", "targetEpochMillis"],
    foreignKeys = [
        ForeignKey(
            entity = ForecastRunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("targetEpochMillis")],
)
data class ForecastPointEntity(
    val runId: String,
    val targetEpochMillis: Long,
    val leadTimeSeconds: Long,
    val temperatureC: Double?,
    val apparentTemperatureC: Double?,
    val minimumTemperatureC: Double?,
    val maximumTemperatureC: Double?,
    val relativeHumidityPercent: Double?,
    val dewPointC: Double?,
    val pressureHpa: Double?,
    val precipitationProbabilityPercent: Double?,
    val probabilitySource: String,
    val precipitationMm: Double?,
    val rainMm: Double?,
    val snowfallMm: Double?,
    val windSpeedMs: Double?,
    val windDirectionDegrees: Double?,
    val windGustMs: Double?,
    val cloudCoverPercent: Double?,
    val visibilityMeters: Double?,
    val uvIndex: Double?,
    val condition: String,
    val sunriseEpochMillis: Long?,
    val sunsetEpochMillis: Long?,
)

data class ForecastRunWithPoints(
    @Embedded val run: ForecastRunEntity,
    @Relation(
        parentColumn = "runId",
        entityColumn = "runId",
    )
    val points: List<ForecastPointEntity>,
)
