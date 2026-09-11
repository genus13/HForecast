package dev.weather.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WeatherDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS weather_observations (
                    observationId TEXT NOT NULL PRIMARY KEY,
                    sourceId TEXT NOT NULL,
                    stationId TEXT NOT NULL,
                    stationName TEXT NOT NULL,
                    stationLatitude REAL NOT NULL,
                    stationLongitude REAL NOT NULL,
                    stationDistanceMeters REAL NOT NULL,
                    quality TEXT NOT NULL,
                    locationKey TEXT NOT NULL,
                    targetEpochMillis INTEGER NOT NULL,
                    variable TEXT NOT NULL,
                    value REAL NOT NULL,
                    recordedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_weather_observations_locationKey_targetEpochMillis " +
                    "ON weather_observations(locationKey, targetEpochMillis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_weather_observations_variable_targetEpochMillis " +
                    "ON weather_observations(variable, targetEpochMillis)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS verification_samples (
                    sampleId TEXT NOT NULL PRIMARY KEY,
                    forecastRunId TEXT NOT NULL,
                    observationId TEXT NOT NULL,
                    providerId TEXT NOT NULL,
                    variable TEXT NOT NULL,
                    horizonBucket TEXT NOT NULL,
                    locationKey TEXT NOT NULL,
                    regionKey TEXT NOT NULL,
                    season TEXT NOT NULL,
                    issuedAtEpochMillis INTEGER NOT NULL,
                    targetEpochMillis INTEGER NOT NULL,
                    forecastValue REAL NOT NULL,
                    observationValue REAL NOT NULL,
                    error REAL NOT NULL,
                    absoluteError REAL NOT NULL,
                    squaredError REAL NOT NULL,
                    verifiedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_verification_samples_providerId_variable_horizonBucket_regionKey_season " +
                    "ON verification_samples(providerId, variable, horizonBucket, regionKey, season)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_verification_samples_locationKey_targetEpochMillis " +
                    "ON verification_samples(locationKey, targetEpochMillis)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS model_skills (
                    providerId TEXT NOT NULL,
                    variable TEXT NOT NULL,
                    horizonBucket TEXT NOT NULL,
                    regionKey TEXT NOT NULL,
                    season TEXT NOT NULL,
                    sampleCount INTEGER NOT NULL,
                    meanBias REAL NOT NULL,
                    meanAbsoluteError REAL NOT NULL,
                    meanSquaredError REAL NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    PRIMARY KEY(providerId, variable, horizonBucket, regionKey, season)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_model_skills_regionKey_season " +
                    "ON model_skills(regionKey, season)",
            )
        }
    }
}
