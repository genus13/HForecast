package dev.weather.hforecast.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.weather.hforecast.MainActivity
import dev.weather.hforecast.R
import dev.weather.hforecast.data.ForecastRepository
import dev.weather.location.LocationStore
import dev.weather.settings.SettingsRepository
import dev.weather.environment.SpaceWeatherRepository
import dev.weather.astronomy.AstronomyCalculator
import java.time.LocalTime
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

class WeatherNotificationWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerDependencies::class.java,
        )
        val preferences = dependencies.settingsRepository().currentNotifications()
        if ((!preferences.enabled && !preferences.auroraAlertsEnabled) ||
            !preferences.allowsHour(LocalTime.now().hour)
        ) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val location = dependencies.locationStore().lastKnown() ?: return Result.success()
        val result = runCatching { dependencies.repository().cached(location) }.getOrNull()
            ?: return Result.success()
        val point = result.forecast.current ?: return Result.success()
        val weather = point.consensus
        val temperature = weather.temperatureC?.let { String.format(Locale.US, "%.1f°C", it) } ?: "—"
        val rain = weather.precipitationProbabilityPercent?.roundToInt()?.let { "$it%" } ?: "—"
        val amount = weather.precipitationMm?.let { String.format(Locale.US, "%.1f mm", it) } ?: "—"
        val wind = weather.windSpeedMs?.let { String.format(Locale.US, "%.1f m/s", it) } ?: "—"
        val newestUpdate = result.forecast.providerIssuedAt.values.maxOrNull()
        val age = newestUpdate?.let { Duration.between(it, Instant.now()).coerceAtLeast(Duration.ZERO) }
        val ageText = age?.let {
            when {
                it.toMinutes() < 1 -> "updated just now"
                it.toHours() < 1 -> "updated ${it.toMinutes()} min ago"
                else -> "updated ${it.toHours()} h ago"
            }
        } ?: "update time unavailable"
        val launchIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (preferences.enabled) {
            val notification = NotificationCompat.Builder(
                applicationContext,
                NotificationChannels.FORECAST_CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_stat_hforecast)
                .setContentTitle("${location.displayName ?: "Selected location"} · HForecast")
                .setContentText("Temp: $temperature · Rain: $rain / $amount · Wind: $wind")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "HForecast estimate · Temperature: $temperature · Rain probability: $rain · " +
                            "Expected rain: $amount · Wind: $wind · " +
                            "${point.overallConfidence.name} confidence · $ageText",
                    ),
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            runCatching {
                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            }
        }
        if (preferences.auroraAlertsEnabled) {
            val aurora = runCatching { dependencies.spaceWeatherRepository().aurora(location) }.getOrNull()
            val sunAltitude = runCatching { AstronomyCalculator.calculate(location).sunAltitudeDegrees }.getOrNull()
            val cloud = weather.cloudCoverPercent
            if (aurora != null &&
                aurora.localProbabilityPercent >= preferences.auroraProbabilityThreshold &&
                sunAltitude != null && sunAltitude <= -6.0 &&
                cloud != null && cloud < 75.0
            ) {
                val auroraNotification = NotificationCompat.Builder(
                    applicationContext,
                    NotificationChannels.AURORA_CHANNEL_ID,
                )
                    .setSmallIcon(R.drawable.ic_stat_hforecast)
                    .setContentTitle("Aurora opportunity near you")
                    .setContentText(
                        "OVATION ${aurora.localProbabilityPercent.roundToInt()}% · " +
                            "cloud ${cloud.roundToInt()}% · Kp ${aurora.solarActivity.currentKp ?: "—"}",
                    )
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
                runCatching {
                    NotificationManagerCompat.from(applicationContext)
                        .notify(AURORA_NOTIFICATION_ID, auroraNotification)
                }
            }
        }
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerDependencies {
        fun repository(): ForecastRepository
        fun locationStore(): LocationStore
        fun settingsRepository(): SettingsRepository
        fun spaceWeatherRepository(): SpaceWeatherRepository
    }

    companion object {
        const val UNIQUE_WORK_NAME = "periodic-weather-notification"
        const val NOTIFICATION_ID = 1002
        const val AURORA_NOTIFICATION_ID = 1003
    }
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
