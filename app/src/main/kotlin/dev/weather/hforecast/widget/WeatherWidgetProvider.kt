package dev.weather.hforecast.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.weather.core.WeatherCondition
import dev.weather.hforecast.MainActivity
import dev.weather.hforecast.R
import dev.weather.hforecast.data.ForecastRepository
import dev.weather.hforecast.work.ForecastRefreshWorker
import dev.weather.location.LocationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

class DetailedWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAsynchronously(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAsynchronously(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val pendingResult = goAsync()
            WIDGET_SCOPE.launch {
                try {
                    WeatherWidgetUpdater.updateAll(
                        context.applicationContext,
                        context.getString(R.string.widget_refreshing),
                    )
                    val request = OneTimeWorkRequestBuilder<ForecastRefreshWorker>()
                        .setInputData(workDataOf(ForecastRefreshWorker.KEY_WIDGET_REQUEST to true))
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        ForecastRefreshWorker.WIDGET_UNIQUE_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    private fun updateAsynchronously(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val pendingResult = goAsync()
        WIDGET_SCOPE.launch {
            try {
                WeatherWidgetUpdater.update(context.applicationContext, manager, ids)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        internal const val ACTION_REFRESH = "dev.weather.hforecast.widget.REFRESH"
    }
}

object WeatherWidgetUpdater {
    fun requestAll(context: Context, statusOverride: String? = null) {
        WIDGET_SCOPE.launch { updateAll(context.applicationContext, statusOverride) }
    }

    suspend fun updateAll(context: Context, statusOverride: String? = null) {
        val manager = AppWidgetManager.getInstance(context)
        update(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, DetailedWeatherWidgetProvider::class.java)),
            statusOverride,
        )
    }

    internal suspend fun update(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        statusOverride: String? = null,
    ) {
        if (ids.isEmpty()) return
        val snapshot = loadSnapshot(context)
        ids.forEach { id ->
            val views = detailedViews(context, snapshot, statusOverride)
            views.setOnClickPendingIntent(R.id.widget_root, launchPendingIntent(context, id))
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context, id))
            manager.updateAppWidget(id, views)
        }
    }

    private suspend fun loadSnapshot(context: Context): WidgetSnapshot? = runCatching {
        val dependencies = EntryPointAccessors.fromApplication(
            context,
            WidgetDependencies::class.java,
        )
        val location = dependencies.locationStore().lastKnown() ?: return@runCatching null
        val result = dependencies.repository().cached(location) ?: return@runCatching null
        val current = result.forecast.current ?: return@runCatching null
        val point = current.consensus
        val newestIssue = result.forecast.providerIssuedAt.values.maxOrNull()
        WidgetSnapshot(
            location = location.displayName?.take(36)
                ?: String.format(Locale.US, "%.3f, %.3f", location.latitude, location.longitude),
            temperature = point.temperatureC.temperature(),
            feelsLike = point.apparentTemperatureC.temperature(),
            condition = point.condition.displayName(),
            rainProbability = point.precipitationProbabilityPercent.percent(),
            precipitation = point.precipitationMm.millimetres(),
            wind = point.windSpeedMs.speed(),
            gusts = point.windGustMs.speed(),
            humidity = point.relativeHumidityPercent.percent(),
            pressure = point.pressureHpa.pressure(),
            confidence = current.overallConfidence.name,
            models = current.members.size,
            age = newestIssue.ageLabel(),
        )
    }.getOrNull()

    private fun detailedViews(
        context: Context,
        snapshot: WidgetSnapshot?,
        statusOverride: String?,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_weather_detailed).apply {
            setTextViewText(R.id.widget_location, snapshot?.location ?: "HForecast")
            setTextViewText(R.id.widget_updated, statusOverride ?: snapshot?.age ?: "No cached forecast")
            setTextViewText(R.id.widget_confidence, snapshot?.confidence ?: "LOW")
            setTextViewText(R.id.widget_temperature, snapshot?.temperature ?: "— °C")
            setTextViewText(R.id.widget_condition, snapshot?.condition ?: "Forecast unavailable")
            setTextViewText(R.id.widget_feels_like, "Feels like ${snapshot?.feelsLike ?: "— °C"}")
            setTextViewText(
                R.id.widget_rain,
                snapshot?.let { "Rain ${it.rainProbability} · ${it.precipitation}" } ?: "Rain — · — mm",
            )
            setTextViewText(
                R.id.widget_wind,
                snapshot?.let { "Wind ${it.wind} · gusts ${it.gusts}" } ?: "Wind — · gusts —",
            )
            setTextViewText(
                R.id.widget_atmosphere,
                snapshot?.let { "Humidity ${it.humidity} · ${it.pressure}" } ?: "Humidity — · pressure —",
            )
            setTextViewText(
                R.id.widget_models,
                snapshot?.let { "${it.models} models available" } ?: "— models available",
            )
        }

    private fun launchPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = "hforecast://widget/$widgetId".toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, DetailedWeatherWidgetProvider::class.java).apply {
            action = DetailedWeatherWidgetProvider.ACTION_REFRESH
            data = "hforecast://widget/$widgetId/refresh".toUri()
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface WidgetDependencies {
        fun repository(): ForecastRepository
        fun locationStore(): LocationStore
    }
}

private data class WidgetSnapshot(
    val location: String,
    val temperature: String,
    val feelsLike: String,
    val condition: String,
    val rainProbability: String,
    val precipitation: String,
    val wind: String,
    val gusts: String,
    val humidity: String,
    val pressure: String,
    val confidence: String,
    val models: Int,
    val age: String,
)

private val WIDGET_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun Double?.temperature(): String =
    this?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "— °C"

private fun Double?.percent(): String = this?.let { "${it.roundToInt()}%" } ?: "—"
private fun Double?.millimetres(): String =
    this?.let { String.format(Locale.getDefault(), "%.1f mm", it) } ?: "— mm"

private fun Double?.speed(): String =
    this?.let { String.format(Locale.getDefault(), "%.1f m/s", it) } ?: "—"

private fun Double?.pressure(): String = this?.let { "${it.roundToInt()} hPa" } ?: "—"
private fun WeatherCondition.displayName(): String = name.lowercase().replace('_', ' ')
    .replaceFirstChar { it.titlecase() }

private fun Instant?.ageLabel(): String {
    if (this == null) return "update time unavailable"
    val duration = Duration.between(this, Instant.now()).let { if (it.isNegative) Duration.ZERO else it }
    return when {
        duration.toMinutes() < 1 -> "updated just now"
        duration.toHours() < 1 -> "updated ${duration.toMinutes()} min ago"
        duration.toDays() < 1 -> "updated ${duration.toHours()} h ago"
        else -> "updated ${duration.toDays()} d ago"
    }
}
