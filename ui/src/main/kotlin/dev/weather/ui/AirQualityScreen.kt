package dev.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.weather.environment.AirQualityForecast
import dev.weather.environment.AirQualityLevel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AirQualityScreen(state: WeatherUiState.Content) {
    val forecast = state.airQuality
    if (forecast == null) {
        SupplementalPlaceholder("air quality", state.supplementalLoading, state.supplementalErrors["Air quality"])
        return
    }
    val current = forecast.current
    val weather = state.forecast.current?.consensus
    val zone = ZoneId.of(forecast.zoneId)
    val daily = forecast.hourly
        .filter { it.timestamp >= forecast.current.timestamp }
        .groupBy { it.timestamp.atZone(zone).toLocalDate() }
        .map { (date, points) ->
            AirDay(
                date = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                maximumAqi = points.mapNotNull { it.europeanAqi }.maxOrNull(),
                maximumPm25 = points.mapNotNull { it.pm25 }.maxOrNull(),
                maximumPollen = points.flatMap { listOfNotNull(it.birchPollen, it.grassPollen) }.maxOrNull(),
            )
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Air and atmosphere", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pollution, pollen and observation-related weather variables for the selected point.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(current.level.displayName(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        current.europeanAqi?.roundToInt()?.toString() ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("European Air Quality Index")
                    HorizontalDivider()
                    AirMetric("PM2.5", current.pm25.concentration())
                    AirMetric("PM10", current.pm10.concentration())
                    AirMetric("Nitrogen dioxide", current.nitrogenDioxide.concentration())
                    AirMetric("Ozone", current.ozone.concentration())
                    AirMetric("Sulphur dioxide", current.sulphurDioxide.concentration())
                    AirMetric("Carbon monoxide", current.carbonMonoxide.concentration())
                }
            }
        }
        item {
            Text("Pollen and aerosols", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AirMetric("Birch pollen", current.birchPollen.pollen())
                    AirMetric("Grass pollen", current.grassPollen.pollen())
                    AirMetric("Alder pollen", current.alderPollen.pollen())
                    AirMetric("Mugwort pollen", current.mugwortPollen.pollen())
                    AirMetric("Dust", current.dust.concentration())
                    AirMetric("Aerosol optical depth", current.aerosolOpticalDepth.decimal())
                    Text(
                        "Pollen fields are seasonal and may be unavailable outside Europe or outside the pollen season.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text("Useful atmospheric variables", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AirMetric("Relative humidity", weather?.relativeHumidityPercent.percent())
                    AirMetric("Dew point", weather?.dewPointC.temperature())
                    AirMetric("Pressure", weather?.pressureHpa.pressure())
                    AirMetric("Cloud cover", weather?.cloudCoverPercent.percent())
                    AirMetric("Visibility", weather?.visibilityMeters.distance())
                    AirMetric("UV index", (weather?.uvIndex ?: current.uvIndex).decimal())
                    AirMetric("Wind gusts", weather?.windGustMs.speed())
                }
            }
        }
        item { Text("7-day air-quality outlook", style = MaterialTheme.typography.titleMedium) }
        items(daily.take(7)) { day ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(day.date, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Peak PM2.5 ${day.maximumPm25.concentration()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column {
                        Text("AQI ${day.maximumAqi?.roundToInt() ?: "—"}")
                        Text("Pollen ${day.maximumPollen.pollen()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text(
                "CAMS/Open-Meteo grid point %.3f, %.3f · approximately 11 km resolution · updated %s"
                    .format(
                        Locale.US,
                        forecast.gridLocation.latitude,
                        forecast.gridLocation.longitude,
                        forecast.fetchedAt.atZone(zone).format(AIR_TIME),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Air-quality forecasts are model estimates, not a replacement for local public-health alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class AirDay(
    val date: String,
    val maximumAqi: Double?,
    val maximumPm25: Double?,
    val maximumPollen: Double?,
)

@Composable
private fun AirMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun AirQualityLevel.displayName() = name.lowercase().replace('_', ' ')
    .replaceFirstChar { it.titlecase() }
private fun Double?.concentration() = this?.let { String.format(Locale.US, "%.1f µg/m³", it) } ?: "—"
private fun Double?.pollen() = this?.let { String.format(Locale.US, "%.1f grains/m³", it) } ?: "—"
private fun Double?.decimal() = this?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
private fun Double?.percent() = this?.let { "${it.roundToInt()}%" } ?: "—"
private fun Double?.temperature() = this?.let { String.format(Locale.US, "%.1f °C", it) } ?: "—"
private fun Double?.pressure() = this?.let { "${it.roundToInt()} hPa" } ?: "—"
private fun Double?.distance() = this?.let { String.format(Locale.US, "%.1f km", it / 1_000.0) } ?: "—"
private fun Double?.speed() = this?.let { String.format(Locale.US, "%.1f m/s", it) } ?: "—"
private val AIR_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
