package dev.weather.location

import android.content.Context
import androidx.core.content.edit
import dev.weather.core.GeoLocation

class PreferencesLocationStore(context: Context) : LocationStore {
    private val preferences = context.getSharedPreferences("last_location", Context.MODE_PRIVATE)

    override fun lastKnown(maxAgeMillis: Long): GeoLocation? {
        if (!preferences.contains(KEY_LATITUDE)) return null
        val savedAt = preferences.getLong(KEY_SAVED_AT, 0L)
        if (System.currentTimeMillis() - savedAt > maxAgeMillis) return null
        return GeoLocation(
            latitude = Double.fromBits(preferences.getLong(KEY_LATITUDE, 0L)),
            longitude = Double.fromBits(preferences.getLong(KEY_LONGITUDE, 0L)),
            displayName = preferences.getString(KEY_NAME, null),
            altitudeMeters = preferences.takeIf { it.contains(KEY_ALTITUDE) }
                ?.getLong(KEY_ALTITUDE, 0L)?.let(Double::fromBits),
        )
    }

    override fun save(location: GeoLocation) {
        val altitude = location.altitudeMeters
        preferences.edit {
            putLong(KEY_LATITUDE, location.latitude.toBits())
            putLong(KEY_LONGITUDE, location.longitude.toBits())
            putString(KEY_NAME, location.displayName)
            putLong(KEY_SAVED_AT, System.currentTimeMillis())
            if (altitude == null) remove(KEY_ALTITUDE)
            else putLong(KEY_ALTITUDE, altitude.toBits())
        }
    }

    private companion object {
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ALTITUDE = "altitude"
        const val KEY_NAME = "name"
        const val KEY_SAVED_AT = "saved_at"
    }
}
