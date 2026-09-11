package dev.weather.database

import android.content.Context
import androidx.room.Room

object WeatherDatabaseFactory {
    fun create(context: Context): WeatherDatabase = Room.databaseBuilder(
        context,
        WeatherDatabase::class.java,
        "hforecast.db",
    ).addMigrations(WeatherDatabaseMigrations.MIGRATION_1_2)
        .build()
}
