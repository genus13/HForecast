package dev.weather.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.weather.core.GeoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class AndroidLocationClient(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val store: LocationStore,
    private val staleAfterMillis: Long = 30 * 60 * 1_000L,
) : LocationClient {
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(forceRefresh: Boolean): GeoLocation? {
        if (!forceRefresh) store.lastKnown(staleAfterMillis)?.let { return it }

        val cancellation = CancellationTokenSource()
        val androidLocation = runCatching {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token,
            ).await()
        }.getOrNull() ?: runCatching { fusedLocationClient.lastLocation.await() }.getOrNull()
            ?: return store.lastKnown()

        val name = reverseGeocode(androidLocation.latitude, androidLocation.longitude)
        return GeoLocation(
            latitude = androidLocation.latitude,
            longitude = androidLocation.longitude,
            displayName = name,
            altitudeMeters = androidLocation.altitude.takeIf { androidLocation.hasAltitude() },
        ).also(store::save)
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocodeAsync(geocoder, latitude, longitude)
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) { geocoder.getFromLocation(latitude, longitude, 1).orEmpty() }
            }
        }.getOrDefault(emptyList())
        return addresses.firstOrNull()?.displayName()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
            if (continuation.isActive) continuation.resume(addresses)
        }
    }

    private fun Address.displayName(): String? =
        locality ?: subAdminArea ?: adminArea ?: countryName
}
