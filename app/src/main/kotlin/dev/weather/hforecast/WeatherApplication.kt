package dev.weather.hforecast

import android.app.Application
import android.content.res.Configuration
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.weather.hforecast.work.ForecastRefreshWorker
import dev.weather.hforecast.work.NotificationChannels
import dev.weather.hforecast.work.NotificationScheduler
import dev.weather.settings.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class WeatherApplication : Application() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notificationScheduler: NotificationScheduler

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        notificationScheduler.apply(settingsRepository.currentNotifications())
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<ForecastRefreshWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ForecastRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dev.weather.hforecast.widget.WeatherWidgetUpdater.requestAll(this)
    }
}
