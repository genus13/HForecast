package dev.weather.ui

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.weather.environment.AuroraForecast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AuroraScreen(state: WeatherUiState.Content) {
    val aurora = state.aurora
    if (aurora == null) {
        SupplementalPlaceholder("Aurora forecast", state.supplementalLoading, state.supplementalErrors["Aurora"])
        return
    }
    val weather = state.forecast.current?.consensus
    val cloud = weather?.cloudCoverPercent
    val sunAltitude = state.astronomy?.sunAltitudeDegrees
    val assessment = auroraAssessment(aurora.localProbabilityPercent, cloud, sunAltitude)
    val zone = ZoneId.of(state.forecast.zoneId)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Aurora forecast", style = MaterialTheme.typography.headlineSmall)
            Text(
                "A local NOAA OVATION signal combined with darkness and the weather consensus.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(assessment.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${aurora.localProbabilityPercent.roundToInt()}%",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("OVATION aurora probability at the nearest forecast grid point")
                    HorizontalDivider()
                    AuroraMetric("Cloud cover", cloud.percentLabel())
                    AuroraMetric("Sun altitude", sunAltitude.degreeLabel())
                    AuroraMetric("Viewing conditions", assessment.conditions)
                    Text(
                        assessment.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            AuroraOvalChart(aurora, state.forecast.location.latitude, state.forecast.location.longitude)
        }
        item {
            Text("Solar and geomagnetic activity", style = MaterialTheme.typography.titleLarge)
        }
        item {
            val solar = aurora.solarActivity
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AuroraMetric("Planetary Kp", solar.currentKp.decimalLabel())
                    AuroraMetric("Geomagnetic storm", scaleLabel("G", solar.geomagneticStormScale))
                    AuroraMetric("Radio blackout", scaleLabel("R", solar.radioBlackoutScale))
                    AuroraMetric("Solar radiation storm", scaleLabel("S", solar.solarRadiationScale))
                    AuroraMetric("Solar wind", solar.solarWindSpeedKmS.unitLabel("km/s"))
                    AuroraMetric("IMF total field Bt", solar.interplanetaryFieldBtNt.unitLabel("nT"))
                    AuroraMetric("IMF Bz", solar.interplanetaryFieldBzNt.unitLabel("nT"))
                    AuroraMetric("10.7 cm solar flux", solar.solarRadioFluxSfu.unitLabel("sfu"))
                    Text(
                        "A negative Bz can favour coupling with Earth's magnetic field, but no single metric " +
                            "guarantees an aurora at your location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Text("Kp outlook", style = MaterialTheme.typography.titleMedium) }
        items(
            aurora.solarActivity.kpForecast
                .filter { it.timestamp >= Instant.now().minusSeconds(10_800) }
                .take(16),
        ) { point ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(point.timestamp.atZone(zone).format(AURORA_TIME), modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "Kp %.1f", point.kp), fontWeight = FontWeight.SemiBold)
                Text(" · ${point.status}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text(
                "Forecast valid ${aurora.forecastTime.atZone(zone).format(AURORA_FULL_TIME)} · observed " +
                    aurora.observationTime.atZone(zone).format(AURORA_TIME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "OVATION is a 30–90 minute model forecast. Local horizons, light pollution and obstructions " +
                    "are not modelled. Data: NOAA Space Weather Prediction Center.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AuroraOvalChart(aurora: AuroraForecast, userLatitude: Double, userLongitude: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Northern aurora oval · polar map", style = MaterialTheme.typography.titleMedium)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color(0xFF06131F), RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            val radius = min(size.width, size.height) * 0.39f
            val center = Offset(size.width / 2f, size.height / 2f)
            val gridColor = Color(0xFF628093)
            val landColor = Color(0xFF244454)
            val coastColor = Color(0xFF91A8B4)
            val gridLabelPaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = Color(0xFFB6CAD4).toArgb()
                textSize = 10.dp.toPx()
                textAlign = NativePaint.Align.CENTER
            }
            val landLabelPaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = Color(0xFFD2DEE4).toArgb()
                textSize = 9.dp.toPx()
                textAlign = NativePaint.Align.CENTER
                setShadowLayer(3.dp.toPx(), 0f, 0f, Color(0xFF06131F).toArgb())
            }

            drawCircle(Color(0xFF0A2433), radius, center)
            NORTHERN_LAND_MASSES.forEach { feature ->
                val path = Path()
                feature.outline.forEachIndexed { index, coordinate ->
                    val projected = polarPoint(coordinate.latitude, coordinate.longitude, center, radius)
                    if (index == 0) path.moveTo(projected.x, projected.y)
                    else path.lineTo(projected.x, projected.y)
                }
                path.close()
                drawPath(path, landColor)
                drawPath(path, coastColor, style = Stroke(width = 0.8.dp.toPx()))
            }
            aurora.northernGrid.forEach { cell ->
                val point = polarPoint(cell.latitude, cell.longitude, center, radius)
                val fraction = (cell.probabilityPercent / 100.0).coerceIn(0.08, 1.0).toFloat()
                val color = if (cell.probabilityPercent >= 30) Color(0xFFFFC857) else Color(0xFF55E6A5)
                drawCircle(color.copy(alpha = fraction), 1.8.dp.toPx(), point)
            }

            (-150..180 step 30).forEach { longitude ->
                val edge = polarPoint(40.0, longitude.toDouble(), center, radius)
                drawLine(gridColor.copy(alpha = 0.55f), center, edge, 0.7.dp.toPx())
                val labelRadius = radius * 1.10f
                val angle = Math.toRadians(longitude.toDouble())
                val label = Offset(
                    center.x + sin(angle).toFloat() * labelRadius,
                    center.y - cos(angle).toFloat() * labelRadius,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    longitudeLabel(longitude),
                    label.x,
                    label.y + gridLabelPaint.textSize * 0.35f,
                    gridLabelPaint,
                )
            }
            listOf(40, 50, 60, 70, 80).forEach { latitude ->
                val r = radius * ((90 - latitude) / 50f)
                drawCircle(gridColor, r, center, style = Stroke(width = 0.9.dp.toPx()))
                val labelPoint = polarPoint(latitude.toDouble(), -43.0, center, radius)
                drawContext.canvas.nativeCanvas.drawText(
                    "$latitude°N",
                    labelPoint.x + 3.dp.toPx(),
                    labelPoint.y - 3.dp.toPx(),
                    gridLabelPaint,
                )
            }
            drawCircle(coastColor, radius, center, style = Stroke(width = 1.2.dp.toPx()))

            POLAR_PLACE_LABELS.forEach { label ->
                val projected = polarPoint(label.latitude, label.longitude, center, radius)
                drawContext.canvas.nativeCanvas.drawText(
                    label.name,
                    projected.x,
                    projected.y,
                    landLabelPaint,
                )
            }
            val user = polarPoint(userLatitude.coerceAtLeast(40.0), userLongitude, center, radius)
            drawCircle(Color.White, 5.dp.toPx(), user, style = Stroke(width = 2.dp.toPx()))
            drawLine(Color.White, user - Offset(7.dp.toPx(), 0f), user + Offset(7.dp.toPx(), 0f), 1.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, user - Offset(0f, 7.dp.toPx()), user + Offset(0f, 7.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
            drawContext.canvas.nativeCanvas.drawText(
                "NORTH POLE",
                center.x,
                center.y + landLabelPaint.textSize + 3.dp.toPx(),
                landLabelPaint,
            )
        }
        Text(
            "North Pole at centre · latitude circles every 10° · longitude meridians every 30° · " +
                "simplified coastlines · white cross is your location · aurora brightness represents " +
                "OVATION probability" +
                if (userLatitude < 40.0) " · locations south of 40°N appear at the outer rim" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun polarPoint(latitude: Double, longitude: Double, center: Offset, radius: Float): Offset {
    val r = radius * ((90.0 - latitude.coerceIn(40.0, 90.0)) / 50.0).toFloat()
    val angle = Math.toRadians(longitude)
    return Offset(
        center.x + sin(angle).toFloat() * r,
        center.y - cos(angle).toFloat() * r,
    )
}

private fun longitudeLabel(longitude: Int): String = when {
    longitude == 0 -> "0°"
    kotlin.math.abs(longitude) == 180 -> "180°"
    longitude > 0 -> "${longitude}°E"
    else -> "${-longitude}°W"
}

private data class PolarCoordinate(val latitude: Double, val longitude: Double)

private data class PolarLandMass(val outline: List<PolarCoordinate>)

private data class PolarPlaceLabel(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

private val NORTHERN_LAND_MASSES = listOf(
    PolarLandMass(
        listOf(
            PolarCoordinate(40.0, -124.0), PolarCoordinate(50.0, -124.0),
            PolarCoordinate(56.0, -132.0), PolarCoordinate(60.0, -142.0),
            PolarCoordinate(58.0, -154.0), PolarCoordinate(65.0, -168.0),
            PolarCoordinate(72.0, -163.0), PolarCoordinate(71.0, -145.0),
            PolarCoordinate(74.0, -125.0), PolarCoordinate(77.0, -108.0),
            PolarCoordinate(82.0, -92.0), PolarCoordinate(80.0, -76.0),
            PolarCoordinate(72.0, -64.0), PolarCoordinate(60.0, -58.0),
            PolarCoordinate(50.0, -66.0), PolarCoordinate(44.0, -75.0),
            PolarCoordinate(42.0, -90.0), PolarCoordinate(40.0, -108.0),
        ),
    ),
    PolarLandMass(
        listOf(
            PolarCoordinate(60.0, -73.0), PolarCoordinate(59.5, -48.0),
            PolarCoordinate(64.0, -41.0), PolarCoordinate(72.0, -29.0),
            PolarCoordinate(80.0, -18.0), PolarCoordinate(83.5, -28.0),
            PolarCoordinate(83.0, -48.0), PolarCoordinate(78.0, -62.0),
            PolarCoordinate(69.0, -68.0),
        ),
    ),
    PolarLandMass(
        listOf(
            PolarCoordinate(40.0, -10.0), PolarCoordinate(43.0, 0.0),
            PolarCoordinate(44.0, 10.0), PolarCoordinate(40.0, 22.0),
            PolarCoordinate(41.0, 36.0), PolarCoordinate(42.0, 52.0),
            PolarCoordinate(45.0, 68.0), PolarCoordinate(45.0, 84.0),
            PolarCoordinate(48.0, 100.0), PolarCoordinate(47.0, 116.0),
            PolarCoordinate(44.0, 132.0), PolarCoordinate(48.0, 143.0),
            PolarCoordinate(55.0, 150.0), PolarCoordinate(60.0, 161.0),
            PolarCoordinate(64.0, 176.0), PolarCoordinate(69.0, 178.0),
            PolarCoordinate(72.0, 160.0), PolarCoordinate(70.0, 145.0),
            PolarCoordinate(72.0, 130.0), PolarCoordinate(74.0, 114.0),
            PolarCoordinate(77.0, 100.0), PolarCoordinate(76.0, 82.0),
            PolarCoordinate(73.0, 66.0), PolarCoordinate(71.0, 54.0),
            PolarCoordinate(68.0, 44.0), PolarCoordinate(66.0, 38.0),
            PolarCoordinate(70.0, 32.0), PolarCoordinate(71.0, 27.0),
            PolarCoordinate(68.0, 23.0), PolarCoordinate(64.0, 19.0),
            PolarCoordinate(60.0, 13.0), PolarCoordinate(58.0, 7.0),
            PolarCoordinate(54.0, 3.0), PolarCoordinate(51.0, -5.0),
            PolarCoordinate(46.0, -9.0),
        ),
    ),
    PolarLandMass(
        listOf(
            PolarCoordinate(63.0, -24.0), PolarCoordinate(64.5, -14.0),
            PolarCoordinate(67.0, -14.0), PolarCoordinate(66.5, -24.0),
        ),
    ),
    PolarLandMass(
        listOf(
            PolarCoordinate(50.0, -6.0), PolarCoordinate(51.0, 1.0),
            PolarCoordinate(58.5, -3.0), PolarCoordinate(57.0, -7.0),
        ),
    ),
    PolarLandMass(
        listOf(
            PolarCoordinate(68.0, 10.0), PolarCoordinate(74.0, 18.0),
            PolarCoordinate(81.0, 20.0), PolarCoordinate(80.0, 31.0),
            PolarCoordinate(74.0, 28.0),
        ),
    ),
    PolarLandMass(
        listOf(
            PolarCoordinate(42.0, 130.0), PolarCoordinate(46.0, 142.0),
            PolarCoordinate(51.0, 143.0), PolarCoordinate(45.0, 135.0),
        ),
    ),
)

private val POLAR_PLACE_LABELS = listOf(
    PolarPlaceLabel("Alaska", 65.0, -150.0),
    PolarPlaceLabel("Canada", 60.0, -103.0),
    PolarPlaceLabel("Greenland", 72.0, -43.0),
    PolarPlaceLabel("Iceland", 65.0, -19.0),
    PolarPlaceLabel("Nordics", 64.0, 17.0),
    PolarPlaceLabel("Russia", 62.0, 90.0),
)

@Composable
private fun AuroraMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SupplementalPlaceholder(title: String, loading: Boolean, error: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) CircularProgressIndicator()
        Text(
            error?.let { "$title unavailable: $it" } ?: "Loading $title…",
            modifier = Modifier.padding(16.dp),
            color = if (error == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
}

private data class AuroraAssessment(val title: String, val conditions: String, val explanation: String)

private fun auroraAssessment(probability: Double, cloud: Double?, sunAltitude: Double?): AuroraAssessment = when {
    probability < 1.0 -> AuroraAssessment(
        "No local aurora signal",
        "No opportunity detected",
        "The nearest OVATION grid cell currently has less than 1% aurora probability.",
    )
    sunAltitude == null -> AuroraAssessment(
        "Aurora activity detected",
        "Darkness unknown",
        "The space-weather signal exists, but local daylight could not be assessed.",
    )
    sunAltitude > -6.0 -> AuroraAssessment(
        "Activity, but too bright",
        "Daylight or bright twilight",
        "The Sun is too high for useful naked-eye aurora viewing at this time.",
    )
    cloud != null && cloud >= 75.0 -> AuroraAssessment(
        "Activity behind clouds",
        "Poor",
        "The aurora model has a local signal, but the weather consensus predicts heavy cloud cover.",
    )
    probability >= 20.0 && (cloud ?: 100.0) <= 40.0 && sunAltitude <= -12.0 -> AuroraAssessment(
        "Good aurora opportunity",
        "Good",
        "Aurora activity, sufficient darkness and limited cloud cover currently align.",
    )
    else -> AuroraAssessment(
        "Possible aurora activity",
        "Limited",
        "A local signal exists, but probability, cloud or twilight limits confidence in actual viewing.",
    )
}

private fun Double?.percentLabel() = this?.let { "${it.roundToInt()}%" } ?: "—"
private fun Double?.degreeLabel() = this?.let { String.format(Locale.US, "%.1f°", it) } ?: "—"
private fun Double?.decimalLabel() = this?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
private fun Double?.unitLabel(unit: String) = this?.let { String.format(Locale.US, "%.1f %s", it, unit) } ?: "—"
private fun scaleLabel(prefix: String, value: String?) = if (value.isNullOrBlank() || value == "0") "None" else "$prefix$value"
private val AURORA_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)
private val AURORA_FULL_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)
