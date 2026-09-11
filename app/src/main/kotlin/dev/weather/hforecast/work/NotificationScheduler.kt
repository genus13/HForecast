package dev.weather.hforecast.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.weather.core.NotificationPreferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun apply(preferences: NotificationPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (!preferences.enabled && !preferences.auroraAlertsEnabled) {
            workManager.cancelUniqueWork(WeatherNotificationWorker.UNIQUE_WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (preferences.auroraAlertsEnabled) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED,
            )
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(
            preferences.intervalHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WeatherNotificationWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

object NotificationChannels {
    const val FORECAST_CHANNEL_ID = "forecast_updates"
    const val AURORA_CHANNEL_ID = "aurora_alerts"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    FORECAST_CHANNEL_ID,
                    "Forecast updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Periodic HForecast consensus summaries"
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    AURORA_CHANNEL_ID,
                    "Aurora alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Local aurora opportunities based on NOAA OVATION, darkness and clouds"
                },
            )
        }
    }
}
