package dev.weather.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.weather.core.ProviderId
import dev.weather.core.WeatherPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class TechnicalSeries(
    val id: String,
    val label: String,
    val unit: String,
    val color: Color,
    val values: List<Double?>,
)

internal data class WeatherChartVariable(
    val id: String,
    val label: String,
    val color: Color,
    val value: (WeatherPoint) -> Double?,
)

internal data class WeatherChartGroup(
    val title: String,
    val unit: String,
    val variables: List<WeatherChartVariable>,
)

internal data class ChartViewportSpec(
    val initialSpan: Float,
    val maximumSpan: Float,
    val minimumZoom: Float,
    val initialStart: Float,
)

/** Point indices represent hourly intervals, so 145 points provide a 144-hour viewport. */
internal fun chartViewportSpec(
    pointCount: Int,
    centerIndex: Int,
    initialVisibleHours: Float,
    maximumVisibleHours: Float,
): ChartViewportSpec {
    val lastIndex = (pointCount - 1).coerceAtLeast(0)
    val availableSpan = max(1, lastIndex).toFloat()
    val initialSpan = min(initialVisibleHours.coerceAtLeast(1f), availableSpan)
    val maximumSpan = min(maximumVisibleHours.coerceAtLeast(initialSpan), availableSpan)
    val maximumStart = (lastIndex - initialSpan).coerceAtLeast(0f)
    return ChartViewportSpec(
        initialSpan = initialSpan,
        maximumSpan = maximumSpan,
        minimumZoom = (initialSpan / maximumSpan).coerceIn(0.01f, 1f),
        initialStart = (centerIndex.coerceIn(0, lastIndex) - initialSpan / 2f)
            .coerceIn(0f, maximumStart),
    )
}

internal val HOURLY_CHART_GROUPS = listOf(
    WeatherChartGroup(
        "Temperature",
        "°C",
        listOf(
            WeatherChartVariable("temperature", "Temperature", Color(0xFFFF6B57)) { it.temperatureC },
            WeatherChartVariable("apparent", "Feels like", Color(0xFFFFB347)) { it.apparentTemperatureC },
            WeatherChartVariable("dew_point", "Dew point", Color(0xFF37B6D9)) { it.dewPointC },
        ),
    ),
    WeatherChartGroup(
        "Precipitation",
        "mm",
        listOf(
            WeatherChartVariable("precipitation", "Total", Color(0xFF2379E8)) { it.precipitationMm },
            WeatherChartVariable("rain", "Rain", Color(0xFF42A5F5)) { it.rainMm },
            WeatherChartVariable("snow", "Snow", Color(0xFF9AD8FF)) { it.snowfallMm },
        ),
    ),
    WeatherChartGroup(
        "Percentages",
        "%",
        listOf(
            WeatherChartVariable("rain_probability", "Rain probability", Color(0xFF1565C0)) {
                it.precipitationProbabilityPercent
            },
            WeatherChartVariable("humidity", "Humidity", Color(0xFF00A88F)) { it.relativeHumidityPercent },
            WeatherChartVariable("cloud", "Cloud cover", Color(0xFF7E8B99)) { it.cloudCoverPercent },
        ),
    ),
    WeatherChartGroup(
        "Wind",
        "m/s",
        listOf(
            WeatherChartVariable("wind", "Sustained wind", Color(0xFF00897B)) { it.windSpeedMs },
            WeatherChartVariable("gust", "Gusts", Color(0xFF8E24AA)) { it.windGustMs },
        ),
    ),
    WeatherChartGroup(
        "Pressure",
        "hPa",
        listOf(WeatherChartVariable("pressure", "Mean sea-level pressure", Color(0xFF6D4C41)) { it.pressureHpa }),
    ),
    WeatherChartGroup(
        "Visibility",
        "km",
        listOf(WeatherChartVariable("visibility", "Visibility", Color(0xFF546E7A)) {
            it.visibilityMeters?.div(1_000.0)
        }),
    ),
    WeatherChartGroup(
        "UV index",
        "index",
        listOf(WeatherChartVariable("uv", "UV index", Color(0xFFF9A825)) { it.uvIndex }),
    ),
)

internal fun ProviderId.technicalChartColor(): Color = when (this) {
    ProviderId.MET_NORWAY -> Color(0xFF00A88F)
    ProviderId.ECMWF -> Color(0xFF7E57C2)
    ProviderId.ICON -> Color(0xFFFF8F00)
    ProviderId.GFS -> Color(0xFF1E88E5)
    ProviderId.SMHI -> Color(0xFFE53935)
}

@Composable
internal fun TechnicalTimeSeriesChart(
    timestamps: List<Instant>,
    series: List<TechnicalSeries>,
    zone: ZoneId,
    chartKey: String,
    modifier: Modifier = Modifier,
    initialVisibleHours: Float = 24f,
    maximumVisibleHours: Float = 144f,
    referenceTime: Instant? = null,
    selectedPointIndex: Int? = null,
    onPointSelected: (Int) -> Unit = {},
) {
    if (timestamps.isEmpty() || series.none { item -> item.values.any { it != null && it.isFinite() } }) {
        Card(modifier = modifier.fillMaxWidth()) {
            Text("No chartable values are available for this selection.", modifier = Modifier.padding(16.dp))
        }
        return
    }

    val referenceIndex = referenceTime?.let { target ->
        timestamps.indices.minByOrNull { index ->
            kotlin.math.abs(timestamps[index].epochSecond - target.epochSecond)
        }
    }
    val initialCenterIndex = referenceIndex ?: selectedPointIndex ?: 0
    val viewportSpec = chartViewportSpec(
        pointCount = timestamps.size,
        centerIndex = initialCenterIndex,
        initialVisibleHours = initialVisibleHours,
        maximumVisibleHours = maximumVisibleHours,
    )
    val timelineKey = "${timestamps.first().epochSecond}:${timestamps.last().epochSecond}"
    var zoom by rememberSaveable(chartKey, timelineKey) { mutableFloatStateOf(1f) }
    var viewportStart by rememberSaveable(chartKey, timelineKey) {
        mutableFloatStateOf(viewportSpec.initialStart)
    }
    var highlightedId by rememberSaveable(chartKey, timelineKey) { mutableStateOf(series.first().id) }
    var selectedIndex by rememberSaveable(chartKey, timelineKey) {
        mutableIntStateOf((selectedPointIndex ?: initialCenterIndex).coerceIn(0, timestamps.lastIndex))
    }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val leftInset = with(density) { 54.dp.toPx() }
    val rightInset = with(density) { 14.dp.toPx() }
    val topInset = with(density) { 16.dp.toPx() }
    val bottomInset = with(density) { 42.dp.toPx() }
    val baseSpan = viewportSpec.initialSpan
    val maximumSpan = viewportSpec.maximumSpan

    fun visibleSpan() = (baseSpan / zoom).coerceIn(min(4f, baseSpan), maximumSpan)
    fun maximumStart(span: Float) = (timestamps.lastIndex.toFloat() - span).coerceAtLeast(0f)

    LaunchedEffect(timestamps.size, chartKey) {
        zoom = zoom.coerceIn(viewportSpec.minimumZoom, 10f)
        viewportStart = viewportStart.coerceIn(0f, maximumStart(visibleSpan()))
        selectedIndex = selectedIndex.coerceIn(0, timestamps.lastIndex)
        if (highlightedId !in series.map { it.id }) highlightedId = series.first().id
    }
    LaunchedEffect(selectedPointIndex, timestamps.size, chartKey) {
        val target = selectedPointIndex?.coerceIn(0, timestamps.lastIndex) ?: return@LaunchedEffect
        selectedIndex = target
        val span = visibleSpan()
        if (target < viewportStart || target > viewportStart + span) {
            viewportStart = (target - span / 2f).coerceIn(0f, maximumStart(span))
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val oldSpan = visibleSpan()
        val newZoom = (zoom * zoomChange).coerceIn(viewportSpec.minimumZoom, 10f)
        val newSpan = (baseSpan / newZoom).coerceIn(min(4f, baseSpan), maximumSpan)
        val plotWidth = (chartSize.width - leftInset - rightInset).coerceAtLeast(1f)
        val centeredStart = viewportStart + (oldSpan - newSpan) / 2f
        zoom = newZoom
        viewportStart = (centeredStart - panChange.x / plotWidth * oldSpan)
            .coerceIn(0f, maximumStart(newSpan))
    }

    val surface = MaterialTheme.colorScheme.surfaceContainer
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val nowMarker = MaterialTheme.colorScheme.primary
    val selectedTime = timestamps[selectedIndex.coerceIn(0, timestamps.lastIndex)]

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    selectedTime.atZone(zone).format(CHART_DETAIL_TIME),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Visible Y range recalculates automatically",
                    style = MaterialTheme.typography.labelSmall,
                    color = axis,
                )
            }
            Text(
                "${visibleSpan().roundToInt()} h · ${zoom.oneDecimal()}× X",
                style = MaterialTheme.typography.labelLarge,
                color = axis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            series.forEach { item ->
                FilterChip(
                    selected = highlightedId == item.id,
                    onClick = { highlightedId = item.id },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(8.dp).background(item.color, RoundedCornerShape(50)))
                            Spacer(Modifier.size(6.dp))
                            Text(item.label)
                        }
                    },
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(surface, RoundedCornerShape(20.dp))
                .onSizeChanged { chartSize = it }
                .transformable(transformState)
                .pointerInput(series, timestamps, viewportStart, zoom, chartSize) {
                    detectTapGestures { tap ->
                        val plotWidth = chartSize.width - leftInset - rightInset
                        val plotHeight = chartSize.height - topInset - bottomInset
                        if (plotWidth <= 0f || plotHeight <= 0f || tap.x !in leftInset..(leftInset + plotWidth)) {
                            return@detectTapGestures
                        }
                        val span = visibleSpan()
                        val fractionalIndex = viewportStart + ((tap.x - leftInset) / plotWidth) * span
                        val index = fractionalIndex.roundToInt().coerceIn(0, timestamps.lastIndex)
                        val range = visibleRange(series, viewportStart, viewportStart + span)
                        val nearest = series.mapNotNull { item ->
                            val value = item.values.getOrNull(index)?.takeIf(Double::isFinite) ?: return@mapNotNull null
                            val y = topInset + ((range.second - value) / (range.second - range.first)).toFloat() * plotHeight
                            item to kotlin.math.abs(tap.y - y)
                        }.minByOrNull { it.second }
                        if (nearest != null) highlightedId = nearest.first.id
                        selectedIndex = index
                        onPointSelected(index)
                    }
                },
        ) {
            val plotWidth = size.width - leftInset - rightInset
            val plotHeight = size.height - topInset - bottomInset
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas
            val span = visibleSpan()
            val end = viewportStart + span
            val range = visibleRange(series, viewportStart, end)
            val ySpan = range.second - range.first
            val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = axis.toArgb()
                textSize = 10.sp.toPx()
            }

            repeat(5) { step ->
                val fraction = step / 4f
                val y = topInset + plotHeight * fraction
                drawLine(grid, Offset(leftInset, y), Offset(size.width - rightInset, y), strokeWidth = 1.dp.toPx())
                val value = range.second - ySpan * fraction
                axisPaint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    value.axisLabel(),
                    leftInset - 7.dp.toPx(),
                    y + 4.dp.toPx(),
                    axisPaint,
                )
            }

            val minimumLabelWidth = axisPaint.measureText("00 Sep") + 12.dp.toPx()
            val xDivisions = (plotWidth / minimumLabelWidth).toInt().coerceIn(2, 6)
            repeat(xDivisions + 1) { step ->
                val fraction = step / xDivisions.toFloat()
                val x = leftInset + plotWidth * fraction
                drawLine(grid, Offset(x, topInset), Offset(x, topInset + plotHeight), strokeWidth = 1.dp.toPx())
                val index = (viewportStart + span * fraction).roundToInt().coerceIn(0, timestamps.lastIndex)
                val local = timestamps[index].atZone(zone)
                val label = if (local.hour == 0) local.format(CHART_DAY) else local.format(CHART_HOUR)
                axisPaint.textAlign = Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    topInset + plotHeight + 25.dp.toPx(),
                    axisPaint,
                )
            }

            referenceIndex?.takeIf { it.toFloat() in viewportStart..end }?.let { index ->
                val x = leftInset + ((index - viewportStart) / span) * plotWidth
                drawLine(
                    color = nowMarker.copy(alpha = 0.72f),
                    start = Offset(x, topInset),
                    end = Offset(x, topInset + plotHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
                )
                axisPaint.color = nowMarker.toArgb()
                axisPaint.textAlign = Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(
                    "NOW",
                    x.coerceIn(leftInset + 18.dp.toPx(), size.width - rightInset - 18.dp.toPx()),
                    topInset + 12.dp.toPx(),
                    axisPaint,
                )
                axisPaint.color = axis.toArgb()
            }

            val firstIndex = floor(viewportStart).toInt().coerceAtLeast(0)
            val lastIndex = ceil(end).toInt().coerceAtMost(timestamps.lastIndex)
            series.sortedBy { if (it.id == highlightedId) 1 else 0 }.forEach { item ->
                val path = Path()
                var drawing = false
                for (index in firstIndex..lastIndex) {
                    val value = item.values.getOrNull(index)?.takeIf(Double::isFinite)
                    if (value == null) {
                        drawing = false
                        continue
                    }
                    val x = leftInset + ((index - viewportStart) / span) * plotWidth
                    val y = topInset + ((range.second - value) / ySpan).toFloat() * plotHeight
                    if (!drawing) {
                        path.moveTo(x, y)
                        drawing = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                val highlighted = item.id == highlightedId
                drawPath(
                    path = path,
                    color = item.color.copy(alpha = if (highlighted) 1f else 0.34f),
                    style = Stroke(
                        width = if (highlighted) 3.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }

            if (selectedIndex.toFloat() in viewportStart..end) {
                val x = leftInset + ((selectedIndex - viewportStart) / span) * plotWidth
                drawLine(axis.copy(alpha = 0.6f), Offset(x, topInset), Offset(x, topInset + plotHeight), 1.dp.toPx())
                series.forEach { item ->
                    val value = item.values.getOrNull(selectedIndex)?.takeIf(Double::isFinite) ?: return@forEach
                    val y = topInset + ((range.second - value) / ySpan).toFloat() * plotHeight
                    drawCircle(
                        color = item.color,
                        radius = if (item.id == highlightedId) 5.dp.toPx() else 3.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
        }

        val highlighted = series.firstOrNull { it.id == highlightedId }
        highlighted?.let { item ->
            val value = item.values.getOrNull(selectedIndex)
            Text(
                "${item.label}: ${value.chartValue(item.unit)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = item.color,
            )
        }
        Text(
            "Default 24 h around now · zoom out to 72 h past and 72 h ahead · drag horizontally · tap a curve",
            style = MaterialTheme.typography.labelSmall,
            color = axis,
        )
    }
}

private fun visibleRange(series: List<TechnicalSeries>, start: Float, end: Float): Pair<Double, Double> {
    val first = floor(start).toInt().coerceAtLeast(0)
    val last = ceil(end).toInt()
    val values = series.flatMap { item ->
        (first..last.coerceAtMost(item.values.lastIndex)).mapNotNull { index ->
            item.values.getOrNull(index)?.takeIf(Double::isFinite)
        }
    }
    if (values.isEmpty()) return 0.0 to 1.0
    val minimum = values.min()
    val maximum = values.max()
    val rawSpan = maximum - minimum
    val padding = if (rawSpan > 1e-9) rawSpan * 0.12 else max(kotlin.math.abs(maximum) * 0.08, 1.0)
    return (minimum - padding) to (maximum + padding)
}

private fun Double.axisLabel(): String = when {
    kotlin.math.abs(this) >= 1_000.0 -> String.format(Locale.US, "%.0f", this)
    kotlin.math.abs(this) >= 100.0 -> String.format(Locale.US, "%.0f", this)
    kotlin.math.abs(this) >= 10.0 -> String.format(Locale.US, "%.1f", this)
    else -> String.format(Locale.US, "%.2f", this)
}

private fun Double?.chartValue(unit: String): String =
    this?.let { String.format(Locale.US, "%.2f %s", it, unit) } ?: "—"

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private val CHART_HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val CHART_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val CHART_DETAIL_TIME = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ENGLISH)
