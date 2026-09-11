package dev.weather.hforecast.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.weather.hforecast.data.ForecastRepository
import dev.weather.hforecast.widget.WeatherWidgetUpdater
import dev.weather.location.LocationStore

class ForecastRefreshWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val isWidgetRequest = inputData.getBoolean(KEY_WIDGET_REQUEST, false)
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerDependencies::class.java,
        )
        val location = dependencies.locationStore().lastKnown()
        if (location == null) {
            if (isWidgetRequest) {
                WeatherWidgetUpdater.updateAll(applicationContext, "Open HForecast to set a location")
            }
            return Result.success()
        }
        val refreshed = runCatching { dependencies.repository().refresh(location) }.getOrNull()
        if (refreshed == null || !refreshed.hadFreshData) {
            if (isWidgetRequest) {
                WeatherWidgetUpdater.updateAll(applicationContext, "Refresh failed · cached data shown")
            }
            return Result.retry()
        }
        WeatherWidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerDependencies {
        fun repository(): ForecastRepository
        fun locationStore(): LocationStore
    }

    companion object {
        const val UNIQUE_WORK_NAME = "periodic-forecast-refresh"
        const val WIDGET_UNIQUE_WORK_NAME = "widget-forecast-refresh"
        const val KEY_WIDGET_REQUEST = "widget_request"
    }
}
