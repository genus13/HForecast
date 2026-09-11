package dev.weather.ui

import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import dev.weather.ensemble.EnsembleResult
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions
import java.util.Locale

@Composable
fun ForecastMapScreen(
    result: EnsembleResult,
    onLoadLocation: (GeoLocation) -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    var selected by remember(result.location) { mutableStateOf(result.location) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    val mapView = rememberMapViewWithLifecycle()
    val mapStyleUri = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        DARK_MAP_STYLE_URI
    } else {
        LIGHT_MAP_STYLE_URI
    }

    LaunchedEffect(map, mapStyleUri) {
        val controller = map ?: return@LaunchedEffect
        styleReady = false
        controller.setStyle(Style.Builder().fromUri(mapStyleUri)) {
            styleReady = true
        }
    }

    LaunchedEffect(map, styleReady, result.providerLocations, selected) {
        if (!styleReady) return@LaunchedEffect
        map?.let { controller ->
            controller.clear()
            controller.addMarker(
                MarkerOptions()
                    .position(LatLng(selected.latitude, selected.longitude))
                    .title("Selected forecast point"),
            )
            result.providerLocations.forEach { (provider, location) ->
                controller.addMarker(
                    MarkerOptions()
                        .position(LatLng(location.latitude, location.longitude))
                        .title("${provider.displayName} grid point"),
                )
            }
        }
    }

    LaunchedEffect(map, styleReady, result.location) {
        if (!styleReady) return@LaunchedEffect
        map?.let { controller ->
            controller.cameraPosition = CameraPosition.Builder()
                .target(LatLng(result.location.latitude, result.location.longitude))
                .zoom(10.5)
                .build()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { controller ->
                        map = controller
                        controller.uiSettings.apply {
                            isZoomGesturesEnabled = true
                            isScrollGesturesEnabled = true
                            isRotateGesturesEnabled = true
                            isTiltGesturesEnabled = true
                            isCompassEnabled = true
                            isAttributionEnabled = true
                            isLogoEnabled = true
                        }
                        controller.addOnMapClickListener { point ->
                            selected = GeoLocation(
                                latitude = point.latitude,
                                longitude = point.longitude,
                                displayName = "Selected map point",
                            )
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().weight(0.58f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose an exact forecast point", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap the map, then load that coordinate. The request and cache use the point rounded to " +
                    "0.01°, roughly one kilometre in latitude; they are not shared across an entire county.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Selected: ${selected.coordinateLabel()}",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onLoadLocation(selected) }) { Text("Load this point") }
                OutlinedButton(onClick = onUseCurrentLocation) { Text("Use GPS") }
            }

            Spacer(Modifier.height(4.dp))
            Text("Model grid points", style = MaterialTheme.typography.titleMedium)
            Text(
                "Markers can differ because each model samples its own nearest grid cell. A finer GPS " +
                    "coordinate cannot create detail below that model's physical grid resolution.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            result.providerLocations.entries.sortedBy { it.key.ordinal }.forEach { (provider, location) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(provider.modelLabel(), style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Returned cell: ${location.coordinateLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(
                "Map data © OpenStreetMap contributors · OpenFreeMap / OpenMapTiles · rendered with MapLibre",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    -> view.parent?.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        view.performClick()
                    }

                    MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun GeoLocation.coordinateLabel(): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

private fun ProviderId.modelLabel(): String = when (this) {
    ProviderId.MET_NORWAY -> "MET Nordic · approximately 1 km · first 2.5 days"
    ProviderId.ECMWF -> "ECMWF IFS HRES · approximately 9 km"
    ProviderId.ICON -> "DWD ICON · approximately 7 km over Europe, 13 km globally"
    ProviderId.GFS -> "NOAA GFS · approximately 13 km"
    ProviderId.SMHI -> "SMHI · resolution depends on product"
}

private const val LIGHT_MAP_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_MAP_STYLE_URI = "https://tiles.openfreemap.org/styles/dark"
