package dev.weather.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ForecastRunEntity::class,
        ForecastPointEntity::class,
        WeatherObservationEntity::class,
        VerificationSampleEntity::class,
        ModelSkillEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
}
