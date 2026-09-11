package dev.weather.ui

import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import dev.weather.core.NotificationPreferences
import dev.weather.ensemble.EnsembleResult
import dev.weather.ensemble.EnsembleLearningSummary
import dev.weather.environment.AirQualityForecast
import dev.weather.environment.AuroraForecast
import dev.weather.astronomy.AstronomySnapshot

sealed interface WeatherUiState {
    data object Locating : WeatherUiState
    data object LocationPermissionRequired : WeatherUiState
    data class Loading(val location: GeoLocation) : WeatherUiState
    data class Content(
        val forecast: EnsembleResult,
        val isRefreshing: Boolean,
        val isFromCache: Boolean,
        val providerErrors: Map<ProviderId, String>,
        val learning: EnsembleLearningSummary = EnsembleLearningSummary(),
        val notificationPreferences: NotificationPreferences,
        val aurora: AuroraForecast? = null,
        val airQuality: AirQualityForecast? = null,
        val astronomy: AstronomySnapshot? = null,
        val supplementalLoading: Boolean = false,
        val supplementalErrors: Map<String, String> = emptyMap(),
    ) : WeatherUiState

    data class Error(val message: String) : WeatherUiState
}
