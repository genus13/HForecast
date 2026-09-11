package dev.weather.location

import android.content.Context
import com.google.android.gms.location.LocationServices

object AndroidLocationFactory {
    fun create(context: Context, store: LocationStore): LocationClient = AndroidLocationClient(
        context,
        LocationServices.getFusedLocationProviderClient(context),
        store,
    )
}
