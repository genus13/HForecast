package dev.weather.hforecast

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.weather.hforecast.data.ForecastRepository
import dev.weather.hforecast.data.ForecastRepositoryResult
import dev.weather.hforecast.work.NotificationScheduler
import dev.weather.hforecast.widget.WeatherWidgetUpdater
import dev.weather.core.GeoLocation
import dev.weather.core.AppTheme
import dev.weather.core.NotificationPreferences
import dev.weather.location.LocationClient
import dev.weather.location.LocationStore
import dev.weather.settings.SettingsRepository
import dev.weather.environment.AirQualityRepository
import dev.weather.environment.SpaceWeatherRepository
import dev.weather.astronomy.AstronomyCalculator
import dev.weather.ui.WeatherUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val locationClient: LocationClient,
    private val locationStore: LocationStore,
    private val repository: ForecastRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val airQualityRepository: AirQualityRepository,
    private val spaceWeatherRepository: SpaceWeatherRepository,
    @param:ApplicationContext private val applicationContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Locating)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()
    val theme: StateFlow<AppTheme> = settingsRepository.theme
    private var loadJob: Job? = null
    private var supplementalJob: Job? = null
    private var notificationPreferences = settingsRepository.currentNotifications()

    init {
        viewModelScope.launch {
            settingsRepository.notifications.collectLatest { preferences ->
                notificationPreferences = preferences
                val content = _state.value as? WeatherUiState.Content
                if (content != null) _state.value = content.copy(notificationPreferences = preferences)
            }
        }
    }

    fun onLocationPermission(granted: Boolean) {
        if (!granted) {
            if (_state.value !is WeatherUiState.Content) {
                _state.value = WeatherUiState.LocationPermissionRequired
            }
            return
        }
        if (loadJob?.isActive != true && _state.value !is WeatherUiState.Content) {
            loadGps(forceLocation = false)
        }
    }

    fun refresh() {
        val content = _state.value as? WeatherUiState.Content
        if (content != null) loadForecast(content.forecast.location, preserveContent = true)
        else loadGps(forceLocation = false)
    }

    fun useCurrentLocation() = loadGps(forceLocation = true)

    fun selectLocation(location: GeoLocation) {
        locationStore.save(location)
        loadForecast(location, preserveContent = false)
    }

    fun updateNotificationPreferences(preferences: NotificationPreferences) {
        settingsRepository.setNotifications(preferences)
        notificationScheduler.apply(preferences)
    }

    fun updateTheme(theme: AppTheme) = settingsRepository.setTheme(theme)

    private fun loadGps(forceLocation: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = WeatherUiState.Locating
            val location = runCatching { locationClient.currentLocation(forceLocation) }.getOrNull()
            if (location == null) {
                _state.value = WeatherUiState.Error(
                    "Could not obtain a location. Check that location services are enabled and try again.",
                )
                return@launch
            }

            loadForecastContent(location, preserveContent = false)
        }
    }

    private fun loadForecast(location: GeoLocation, preserveContent: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadForecastContent(location, preserveContent) }
    }

    private suspend fun loadForecastContent(location: GeoLocation, preserveContent: Boolean) {
        val previous = _state.value as? WeatherUiState.Content
        if (preserveContent && previous != null) {
            _state.value = previous.copy(isRefreshing = true)
        } else {
            _state.value = WeatherUiState.Loading(location)
        }
        val cached = runCatching { repository.cached(location) }.getOrNull()
        if (cached != null) {
            _state.value = cached.toUiState(isRefreshing = true)
            loadSupplemental(location)
        }

        val refreshed = runCatching { repository.refresh(location) }
        refreshed.onSuccess { result ->
            _state.value = result.toUiState(isRefreshing = false)
            WeatherWidgetUpdater.requestAll(applicationContext)
            loadSupplemental(location)
        }.onFailure { error ->
            _state.value = cached?.toUiState(isRefreshing = false)
                ?: previous?.copy(isRefreshing = false)
                ?: WeatherUiState.Error(error.message ?: "Forecast update failed")
        }
    }

    private fun loadSupplemental(location: GeoLocation) {
        supplementalJob?.cancel()
        supplementalJob = viewModelScope.launch {
            val initial = _state.value as? WeatherUiState.Content ?: return@launch
            _state.value = initial.copy(supplementalLoading = true, supplementalErrors = emptyMap())
            val results = supervisorScope {
                val air = async { runCatching { airQualityRepository.forecast(location) } }
                val aurora = async { runCatching { spaceWeatherRepository.aurora(location) } }
                val astronomy = async { runCatching { AstronomyCalculator.calculate(location) } }
                Triple(air.await(), aurora.await(), astronomy.await())
            }
            val current = _state.value as? WeatherUiState.Content ?: return@launch
            val errors = buildMap {
                results.first.exceptionOrNull()?.let { put("Air quality", it.message ?: "Unavailable") }
                results.second.exceptionOrNull()?.let { put("Aurora", it.message ?: "Unavailable") }
                results.third.exceptionOrNull()?.let { put("Astronomy", it.message ?: "Unavailable") }
            }
            _state.value = current.copy(
                airQuality = results.first.getOrNull() ?: current.airQuality,
                aurora = results.second.getOrNull() ?: current.aurora,
                astronomy = results.third.getOrNull() ?: current.astronomy,
                supplementalLoading = false,
                supplementalErrors = errors,
            )
        }
    }

    private fun ForecastRepositoryResult.toUiState(isRefreshing: Boolean) = WeatherUiState.Content(
        forecast = forecast,
        isRefreshing = isRefreshing,
        isFromCache = isFromCache,
        providerErrors = providerErrors,
        learning = learning,
        notificationPreferences = notificationPreferences,
    )
}
