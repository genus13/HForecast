package dev.weather.location

import dev.weather.core.GeoLocation

interface LocationClient {
    /** Performs a single location lookup. The implementation never starts continuous updates. */
    suspend fun currentLocation(forceRefresh: Boolean = false): GeoLocation?
}

interface LocationStore {
    fun lastKnown(maxAgeMillis: Long = Long.MAX_VALUE): GeoLocation?
    fun save(location: GeoLocation)
}
