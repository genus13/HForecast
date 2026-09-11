package dev.weather.settings

import android.content.Context
import androidx.core.content.edit
import dev.weather.core.NotificationPreferences
import dev.weather.core.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SettingsRepository {
    val notifications: StateFlow<NotificationPreferences>
    val theme: StateFlow<AppTheme>
    fun currentNotifications(): NotificationPreferences
    fun setNotifications(preferences: NotificationPreferences)
    fun setTheme(theme: AppTheme)
}

class PreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _notifications = MutableStateFlow(readNotifications())
    private val _theme = MutableStateFlow(readTheme())

    override val notifications: StateFlow<NotificationPreferences> = _notifications.asStateFlow()
    override val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    override fun currentNotifications(): NotificationPreferences = _notifications.value

    override fun setNotifications(preferences: NotificationPreferences) {
        this.preferences.edit {
            putBoolean(KEY_ENABLED, preferences.enabled)
            putInt(KEY_INTERVAL, preferences.intervalHours)
            putInt(KEY_START_HOUR, preferences.startHour)
            putInt(KEY_END_HOUR, preferences.endHour)
            putBoolean(KEY_AURORA_ALERTS, preferences.auroraAlertsEnabled)
            putInt(KEY_AURORA_THRESHOLD, preferences.auroraProbabilityThreshold)
        }
        _notifications.value = preferences
    }

    override fun setTheme(theme: AppTheme) {
        preferences.edit { putString(KEY_THEME, theme.name) }
        _theme.value = theme
    }

    private fun readNotifications() = NotificationPreferences(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        intervalHours = preferences.getInt(KEY_INTERVAL, 3),
        startHour = preferences.getInt(KEY_START_HOUR, 7),
        endHour = preferences.getInt(KEY_END_HOUR, 22),
        auroraAlertsEnabled = preferences.getBoolean(KEY_AURORA_ALERTS, false),
        auroraProbabilityThreshold = preferences.getInt(KEY_AURORA_THRESHOLD, 10),
    )

    private fun readTheme(): AppTheme = runCatching {
        AppTheme.valueOf(preferences.getString(KEY_THEME, null) ?: AppTheme.SYSTEM.name)
    }.getOrDefault(AppTheme.SYSTEM)

    private companion object {
        const val FILE_NAME = "hforecast_settings"
        const val KEY_ENABLED = "notifications_enabled"
        const val KEY_INTERVAL = "notification_interval_hours"
        const val KEY_START_HOUR = "notification_start_hour"
        const val KEY_END_HOUR = "notification_end_hour"
        const val KEY_THEME = "app_theme"
        const val KEY_AURORA_ALERTS = "aurora_alerts_enabled"
        const val KEY_AURORA_THRESHOLD = "aurora_probability_threshold"
    }
}
