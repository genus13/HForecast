package dev.weather.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.weather.astronomy.AstronomySnapshot
import dev.weather.astronomy.SkyObject
import dev.weather.astronomy.SkyObjectType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AstronomyScreen(state: WeatherUiState.Content) {
    val sky = state.astronomy
    if (sky == null) {
        SupplementalPlaceholder("astronomy", state.supplementalLoading, state.supplementalErrors["Astronomy"])
        return
    }
    val weather = state.forecast.current?.consensus
    val zone = ZoneId.of(state.forecast.zoneId)
    val observable = sky.sunAltitudeDegrees <= -6.0
    var manualHeading by rememberSaveable { mutableFloatStateOf(0f) }
    var selectedChartObject by remember { mutableStateOf<SkyObject?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sky at your location", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Drag the circular chart itself to rotate it, then use the phone's compass to align its top edge. " +
                        "Tap a named object for details. HForecast does not rotate this view automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SkyMap(
                sky = sky,
                heading = manualHeading,
                onHeadingChange = { manualHeading = normalizeHeading(it) },
                selectedObject = selectedChartObject,
                onObjectSelected = { selectedChartObject = if (selectedChartObject == it) null else it },
            )
        }
        selectedChartObject?.let { selected ->
            item(key = "selected-${selected.name}") {
                SelectedSkyObjectCard(
                    item = selected,
                    darkEnough = observable,
                    onClose = { selectedChartObject = null },
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkyMetric("Sky state", skyState(sky.sunAltitudeDegrees))
                    SkyMetric("Sun altitude", sky.sunAltitudeDegrees.angle())
                    SkyMetric("Cloud cover", weather?.cloudCoverPercent.percent())
                    SkyMetric("Visibility", weather?.visibilityMeters.distance())
                    SkyMetric("Moon", "${sky.moonPhaseName} · ${sky.moonIlluminationPercent.roundToInt()}% lit")
                    SkyMetric("Moon altitude", sky.moonAltitudeDegrees.angle())
                    HorizontalDivider()
                    SkyMetric("Next sunrise", sky.nextSunrise.localTime(zone))
                    SkyMetric("Next sunset", sky.nextSunset.localTime(zone))
                    SkyMetric("Astronomical dusk", sky.nextAstronomicalDusk.localTime(zone))
                    SkyMetric("Astronomical dawn", sky.nextAstronomicalDawn.localTime(zone))
                    Text(
                        if (observable) {
                            "Objects below are geometrically above the horizon. Clouds, buildings and light " +
                                "pollution can still prevent observation."
                        } else {
                            "Objects are geometrically above the horizon, but the sky is currently too bright " +
                                "for most stars."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Text("Planets above the horizon", style = MaterialTheme.typography.titleLarge) }
        if (sky.planetsAboveHorizon.isEmpty()) {
            item { Text("No supported planet is currently above the horizon.") }
        } else {
            items(sky.planetsAboveHorizon, key = { "planet-${it.name}" }) { planet ->
                ExpandableSkyObjectRow(planet, observable)
            }
        }
        item { Text("Brightest stars above the horizon", style = MaterialTheme.typography.titleLarge) }
        items(sky.brightStarsAboveHorizon.take(16), key = { "star-${it.name}" }) { star ->
            ExpandableSkyObjectRow(star, observable)
        }
        item {
            Text(
                "Positions are calculated offline for your coordinates and current time using Astronomy " +
                    "Engine. The chart labels the brightest targets from a curated star catalogue. It is an " +
                    "orientation aid, not a telescope pointing system.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkyMap(
    sky: AstronomySnapshot,
    heading: Float,
    onHeadingChange: (Float) -> Unit,
    selectedObject: SkyObject?,
    onObjectSelected: (SkyObject) -> Unit,
) {
    val stars = sky.brightStarsAboveHorizon
    val planets = sky.planetsAboveHorizon
    val clickableObjects = remember(stars, planets) { planets + stars }
    val labelledObjects = remember(stars, planets) { planets + stars.take(10) }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val starLabelPaint = remember { labelPaint(Color.White) }
    val planetLabelPaint = remember { labelPaint(Color(0xFFFFC857)) }
    val cardinalPaint = remember {
        labelPaint(Color.White).apply { textAlign = Paint.Align.CENTER }
    }
    val currentHeading by rememberUpdatedState(heading)
    val updateHeading by rememberUpdatedState(onHeadingChange)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(370.dp)
                .background(Color(0xFF050A18), RoundedCornerShape(24.dp))
                .onSizeChanged { chartSize = it }
                .pointerInput(chartSize) {
                    detectDragGestures { change, dragAmount ->
                        if (chartSize == IntSize.Zero) return@detectDragGestures
                        val center = Offset(chartSize.width / 2f, chartSize.height / 2f)
                        val previous = change.previousPosition
                        val current = change.position
                        val previousRadius = hypot(previous.x - center.x, previous.y - center.y)
                        val currentRadius = hypot(current.x - center.x, current.y - center.y)
                        val minimumRadius = 36.dp.toPx()
                        val deltaDegrees = if (previousRadius > minimumRadius && currentRadius > minimumRadius) {
                            val before = Math.toDegrees(
                                atan2(
                                    (previous.y - center.y).toDouble(),
                                    (previous.x - center.x).toDouble(),
                                ),
                            )
                            val after = Math.toDegrees(
                                atan2(
                                    (current.y - center.y).toDouble(),
                                    (current.x - center.x).toDouble(),
                                ),
                            )
                            (((after - before + 540.0) % 360.0) - 180.0).toFloat()
                        } else {
                            dragAmount.x / chartSize.width.coerceAtLeast(1) * 180f
                        }
                        change.consume()
                        updateHeading(normalizeHeading(currentHeading - deltaDegrees))
                    }
                }
                .pointerInput(clickableObjects, chartSize) {
                    detectTapGestures { tap ->
                        if (chartSize == IntSize.Zero) return@detectTapGestures
                        val nearest = clickableObjects.minByOrNull { item ->
                            val point = chartPosition(
                                chartSize.width.toFloat(),
                                chartSize.height.toFloat(),
                                item.azimuthDegrees,
                                item.altitudeDegrees,
                                currentHeading,
                            )
                            hypot(tap.x - point.x, tap.y - point.y)
                        }
                        if (nearest != null) {
                            val point = chartPosition(
                                chartSize.width.toFloat(),
                                chartSize.height.toFloat(),
                                nearest.azimuthDegrees,
                                nearest.altitudeDegrees,
                                currentHeading,
                            )
                            if (hypot(tap.x - point.x, tap.y - point.y) <= 38.dp.toPx()) {
                                onObjectSelected(nearest)
                            }
                        }
                    }
                },
        ) {
            val radius = min(size.width, size.height) * 0.43f
            val center = Offset(size.width / 2f, size.height / 2f)
            val occupiedLabels = mutableListOf<RectF>()
            drawCircle(Color(0xFF33445D), radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(Color(0xFF24364E), radius / 2f, center, style = Stroke(1.dp.toPx()))
            drawLine(Color(0xFF24364E), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
            drawLine(Color(0xFF24364E), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))

            stars.forEach { star ->
                val point = chartPosition(size.width, size.height, star.azimuthDegrees, star.altitudeDegrees, heading)
                val pointRadius = ((2.4 - star.magnitude).coerceIn(0.8, 3.8) * density).toFloat()
                drawCircle(Color.White.copy(alpha = 0.92f), pointRadius, point)
            }
            planets.forEach { planet ->
                val point = chartPosition(size.width, size.height, planet.azimuthDegrees, planet.altitudeDegrees, heading)
                drawCircle(Color(0xFFFFC857), 5.dp.toPx(), point)
                drawCircle(Color.White.copy(alpha = 0.8f), 7.dp.toPx(), point, style = Stroke(1.dp.toPx()))
            }

            starLabelPaint.textSize = 11.sp.toPx()
            planetLabelPaint.textSize = 11.sp.toPx()
            cardinalPaint.textSize = 14.sp.toPx()

            listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (label, azimuth) ->
                val point = chartPosition(size.width, size.height, azimuth, 4.5, heading)
                drawCircle(Color(0xCC14263A), 13.dp.toPx(), point)
                cardinalPaint.color = if (label == "N") Color(0xFFFF6B6B).toArgb() else Color.White.toArgb()
                drawContext.canvas.nativeCanvas.drawText(label, point.x, point.y + 5.dp.toPx(), cardinalPaint)
                occupiedLabels += RectF(
                    point.x - 15.dp.toPx(),
                    point.y - 15.dp.toPx(),
                    point.x + 15.dp.toPx(),
                    point.y + 15.dp.toPx(),
                )
            }

            fun placeLabel(name: String, point: Offset, paint: Paint): Pair<Float, Float>? {
                val width = paint.measureText(name)
                val metrics = paint.fontMetrics
                val height = metrics.bottom - metrics.top
                val gap = 8.dp.toPx()
                val candidates = listOf(
                    Pair(point.x + gap, point.y - gap),
                    Pair(point.x - gap - width, point.y - gap),
                    Pair(point.x + gap, point.y + gap + height),
                    Pair(point.x - gap - width, point.y + gap + height),
                )
                return candidates.firstOrNull { (x, baseline) ->
                    val bounds = RectF(x - 2.dp.toPx(), baseline + metrics.top, x + width + 2.dp.toPx(), baseline + metrics.bottom)
                    val inside = bounds.left >= 4.dp.toPx() && bounds.right <= size.width - 4.dp.toPx() &&
                        bounds.top >= 4.dp.toPx() && bounds.bottom <= size.height - 4.dp.toPx()
                    if (inside && occupiedLabels.none { existing -> RectF.intersects(existing, bounds) }) {
                        occupiedLabels += bounds
                        true
                    } else {
                        false
                    }
                }
            }

            if (sky.moonAltitudeDegrees > 0.0) {
                val moon = chartPosition(
                    size.width,
                    size.height,
                    sky.moonAzimuthDegrees,
                    sky.moonAltitudeDegrees,
                    heading,
                )
                drawCircle(Color(0xFFDDE7FF), 7.dp.toPx(), moon)
                placeLabel("Moon", moon, starLabelPaint)?.let { (x, y) ->
                    drawContext.canvas.nativeCanvas.drawText("Moon", x, y, starLabelPaint)
                }
            }
            labelledObjects.sortedWith(compareBy<SkyObject> { it.type != SkyObjectType.PLANET }.thenBy { it.magnitude })
                .forEach { item ->
                val point = chartPosition(size.width, size.height, item.azimuthDegrees, item.altitudeDegrees, heading)
                val paint = if (item.type == SkyObjectType.PLANET) planetLabelPaint else starLabelPaint
                placeLabel(item.name, point, paint)?.let { (x, y) ->
                    drawContext.canvas.nativeCanvas.drawText(item.name, x, y, paint)
                }
            }
            selectedObject?.let { item ->
                val point = chartPosition(size.width, size.height, item.azimuthDegrees, item.altitudeDegrees, heading)
                drawCircle(Color(0xFF62E6D5), 11.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            }

            drawLine(
                Color(0xFF62E6D5),
                Offset(center.x, center.y - radius - 7.dp.toPx()),
                Offset(center.x, center.y - radius + 6.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }
        Text(
            "Top: ${heading.toDouble().compass()} · ${heading.roundToInt()}° · drag the dial to rotate · " +
                "N/E/S/W mark true directions · tap a named object for details",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun labelPaint(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textAlign = Paint.Align.LEFT
    typeface = android.graphics.Typeface.DEFAULT_BOLD
}

@Composable
private fun SelectedSkyObjectCard(item: SkyObject, darkEnough: Boolean, onClose: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClose) { Text("Close") }
            }
            SkyObjectDetails(item, darkEnough)
        }
    }
}

@Composable
private fun ExpandableSkyObjectRow(item: SkyObject, darkEnough: Boolean) {
    var expanded by rememberSaveable(item.name) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(item.type.name.lowercase().replaceFirstChar { it.titlecase() })
                            item.constellation?.let { append(" · $it") }
                            append(if (expanded) " · tap to close" else " · tap for details")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    Text("${item.azimuthDegrees.compass()} · ${item.altitudeDegrees.angle()}")
                    Text(
                        String.format(Locale.US, "magnitude %.1f", item.magnitude),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider()
                SkyObjectDetails(item, darkEnough)
            }
        }
    }
}

@Composable
private fun SkyObjectDetails(item: SkyObject, darkEnough: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(item.description(), style = MaterialTheme.typography.bodyMedium)
        SkyMetric("Current direction", "${item.azimuthDegrees.compass()} · ${item.azimuthDegrees.angle()} azimuth")
        SkyMetric("Altitude", item.altitudeDegrees.angle())
        SkyMetric("Apparent magnitude", String.format(Locale.US, "%.1f", item.magnitude))
        item.constellation?.let { SkyMetric("Constellation", it) }
        SkyMetric("Observation", item.observationStatus(darkEnough))
        Text(
            "Lower magnitude means brighter. Position values apply to the calculation time shown by this forecast refresh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SkyMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun chartPosition(
    width: Float,
    height: Float,
    azimuth: Double,
    altitude: Double,
    heading: Float,
): Offset {
    val radius = min(width, height) * 0.43f
    val radialDistance = radius * ((90.0 - altitude.coerceIn(0.0, 90.0)) / 90.0).toFloat()
    val angle = Math.toRadians(azimuth - heading)
    return Offset(
        x = width / 2f + sin(angle).toFloat() * radialDistance,
        y = height / 2f - cos(angle).toFloat() * radialDistance,
    )
}

private fun SkyObject.observationStatus(darkEnough: Boolean): String = when {
    !darkEnough && type == SkyObjectType.STAR -> "Daylight or twilight limited"
    altitudeDegrees < 10.0 -> "Very low; seek a clear horizon"
    magnitude <= 1.5 -> "Bright naked-eye target in a clear sky"
    magnitude <= 5.5 -> "Potentially naked-eye under a dark sky"
    else -> "Binoculars or a telescope are recommended"
}

private fun SkyObject.description(): String = OBJECT_DESCRIPTIONS[name]
    ?: when (type) {
        SkyObjectType.STAR -> "$name is included in HForecast's curated catalogue of bright navigation and observation stars."
        SkyObjectType.PLANET -> "$name is shown when its calculated position is geometrically above your local horizon."
        SkyObjectType.MOON -> "Earth's natural satellite; its phase and illuminated fraction change throughout the lunar month."
    }

private val OBJECT_DESCRIPTIONS = mapOf(
    "Mercury" to "Mercury is the innermost planet. It is usually easiest to find low in twilight near sunrise or sunset.",
    "Venus" to "Venus is normally the brightest planet and can appear as a brilliant morning or evening object.",
    "Mars" to "Mars is a rocky planet whose warm orange-red colour can help distinguish it from nearby stars.",
    "Jupiter" to "Jupiter is the largest planet and is usually a bright naked-eye target; binoculars may reveal its major moons.",
    "Saturn" to "Saturn is a naked-eye planet under suitable conditions, while its rings require optical magnification.",
    "Uranus" to "Uranus is an ice giant near the naked-eye limit and normally requires binoculars and a precise chart.",
    "Neptune" to "Neptune is a distant ice giant that requires optical aid and careful identification.",
    "Sirius" to "Sirius is the brightest star in Earth's night sky and is a prominent cool-season target from northern latitudes.",
    "Canopus" to "Canopus is one of the brightest stars in the sky but remains below the horizon from much of the Nordic region.",
    "Arcturus" to "Arcturus is a bright orange giant and an easy reference star in the northern spring and summer sky.",
    "Vega" to "Vega is a bright blue-white star and one corner of the Summer Triangle.",
    "Capella" to "Capella is a bright northern star system that remains visible for long periods from Nordic latitudes.",
    "Rigel" to "Rigel is the bright blue-white star marking Orion's foot.",
    "Procyon" to "Procyon is a nearby bright star and one vertex of the Winter Triangle.",
    "Betelgeuse" to "Betelgeuse is a reddish supergiant marking Orion's shoulder, with naturally variable brightness.",
    "Achernar" to "Achernar is a bright southern star and is difficult or impossible to see from high northern latitudes.",
    "Hadar" to "Hadar is a bright southern star in Centaurus and is not visible from most of northern Europe.",
    "Altair" to "Altair is a nearby bright star and one corner of the Summer Triangle.",
    "Acrux" to "Acrux is the brightest star in Crux and stays below the horizon from the Nordic region.",
    "Aldebaran" to "Aldebaran is an orange giant that appears near the Hyades cluster in Taurus.",
    "Antares" to "Antares is a reddish supergiant in Scorpius, best sought low in the Nordic summer sky.",
    "Spica" to "Spica is a bright blue-white star and the most prominent star in Virgo.",
    "Pollux" to "Pollux is the brighter of the two principal stars associated with the heads of Gemini's twins.",
    "Fomalhaut" to "Fomalhaut is a bright southern star that appears low from northern Europe during autumn.",
    "Deneb" to "Deneb is a luminous supergiant and one corner of the Summer Triangle.",
    "Regulus" to "Regulus is the brightest star in Leo and lies close to the apparent path of the planets.",
    "Castor" to "Castor appears as one of Gemini's two head stars and resolves into a multiple-star system with instruments.",
    "Polaris" to "Polaris lies close to the north celestial pole, so it is a practical reference for finding true north.",
)

private fun skyState(sunAltitude: Double): String = when {
    sunAltitude > 0.0 -> "Daylight"
    sunAltitude > -6.0 -> "Civil twilight"
    sunAltitude > -12.0 -> "Nautical twilight"
    sunAltitude > -18.0 -> "Astronomical twilight"
    else -> "Astronomical night"
}

private fun normalizeHeading(value: Float): Float = ((value % 360f) + 360f) % 360f
private fun Double.angle() = String.format(Locale.US, "%.1f°", this)
private fun Double?.percent() = this?.let { "${it.roundToInt()}%" } ?: "—"
private fun Double?.distance() = this?.let { String.format(Locale.US, "%.1f km", it / 1_000.0) } ?: "—"
private fun Instant?.localTime(zone: ZoneId) = this?.atZone(zone)?.format(SKY_TIME) ?: "No event in range"
private fun Double.compass(): String {
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return labels[((this / 45.0).roundToInt() % 8 + 8) % 8]
}

private val SKY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)
