package dev.weather.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.weather.core.ForecastVariable
import dev.weather.core.AppTheme
import dev.weather.core.GeoLocation
import dev.weather.core.NotificationPreferences
import dev.weather.core.ProviderId
import dev.weather.core.WeatherCondition
import dev.weather.core.WeatherPoint
import dev.weather.ensemble.ConfidenceBand
import dev.weather.ensemble.ConsensusPoint
import dev.weather.ensemble.EnsembleResult
import dev.weather.ensemble.EnsembleLearningSummary
import dev.weather.ensemble.ModelDiagnosticsCalculator
import dev.weather.ensemble.horizonBucket
import dev.weather.ensemble.season
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ScreenTab(val title: String) {
    OVERVIEW("Now"),
    HOURLY("Hourly"),
    MODELS("Models"),
    MAP("Map"),
    AURORA("Aurora"),
    SKY("Sky"),
    AIR("Air"),
    ABOUT("About"),
    SETTINGS("Settings"),
}

@Composable
fun HForecastApp(
    state: WeatherUiState,
    appTheme: AppTheme,
    onRefresh: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onSelectLocation: (GeoLocation) -> Unit,
    onUseCurrentLocation: () -> Unit,
    notificationPermissionGranted: Boolean,
    onNotificationSettingsChange: (NotificationPreferences) -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    HForecastTheme(appTheme) {
        when (state) {
            WeatherUiState.Locating -> CenterMessage("Finding your location…", showProgress = true, modifier)
            WeatherUiState.LocationPermissionRequired -> PermissionScreen(onRequestLocationPermission, modifier)
            is WeatherUiState.Loading -> CenterMessage(
                "Loading forecasts for ${state.location.displayLabel()}…",
                showProgress = true,
                modifier,
            )
            is WeatherUiState.Error -> ErrorScreen(state.message, onRefresh, modifier)
            is WeatherUiState.Content -> ForecastScaffold(
                state = state,
                onRefresh = onRefresh,
                onSelectLocation = onSelectLocation,
                onUseCurrentLocation = onUseCurrentLocation,
                notificationPermissionGranted = notificationPermissionGranted,
                onNotificationSettingsChange = onNotificationSettingsChange,
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ForecastScaffold(
    state: WeatherUiState.Content,
    onRefresh: () -> Unit,
    onSelectLocation: (GeoLocation) -> Unit,
    onUseCurrentLocation: () -> Unit,
    notificationPermissionGranted: Boolean,
    onNotificationSettingsChange: (NotificationPreferences) -> Unit,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { ScreenTab.entries.size })
    val navigationScope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.hforecast_brand_mark),
                        contentDescription = "HForecast logo",
                        modifier = Modifier.size(46.dp).padding(4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "HForecast",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${state.forecast.location.displayLabel()} · ${state.forecast.providerIssuedAt.size} models",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.forecast.location.coordinateLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Text(if (state.isRefreshing) "Updating…" else "Refresh")
                    }
                }
                ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 8.dp) {
                    ScreenTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { navigationScope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(tab.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ForecastStatusBanner(state)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                key = { ScreenTab.entries[it].name },
                beyondViewportPageCount = 0,
            ) { page ->
                when (ScreenTab.entries[page]) {
                    ScreenTab.OVERVIEW -> OverviewScreen(state.forecast)
                    ScreenTab.HOURLY -> HourlyScreen(state.forecast)
                    ScreenTab.MODELS -> ModelComparisonScreen(state.forecast, state.learning)
                    ScreenTab.MAP -> ForecastMapScreen(state.forecast, onSelectLocation, onUseCurrentLocation)
                    ScreenTab.AURORA -> AuroraScreen(state)
                    ScreenTab.SKY -> AstronomyScreen(state)
                    ScreenTab.AIR -> AirQualityScreen(state)
                    ScreenTab.ABOUT -> AboutScreen()
                    ScreenTab.SETTINGS -> SettingsScreen(
                        preferences = state.notificationPreferences,
                        notificationPermissionGranted = notificationPermissionGranted,
                        onChange = onNotificationSettingsChange,
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastStatusBanner(state: WeatherUiState.Content) {
    val newest = state.forecast.providerIssuedAt.values.maxOrNull()
    val age = newest?.let { Duration.between(it, Instant.now()).coerceAtLeast(Duration.ZERO) }
    if (state.isFromCache || (age != null && age > Duration.ofHours(1))) {
        Text(
            text = "Cached forecast · updated ${age?.humanAge() ?: "at an unknown time"}",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    if (state.providerErrors.isNotEmpty()) {
        Text(
            text = "Refresh failed; cached values are used when available: " +
                state.providerErrors.keys.joinToString { it.displayName },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OverviewScreen(result: EnsembleResult) {
    val current = result.current
    if (current == null) {
        CenterMessage("No aligned forecast points are available.", false, Modifier)
        return
    }
    val point = current.consensus
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CurrentConditionsCard(current, result) }
        item {
            Text("Confidence by variable", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfidencePill("Temperature", current.confidence[ForecastVariable.TEMPERATURE]?.band)
                ConfidencePill("Rain", current.confidence[ForecastVariable.PRECIPITATION_AMOUNT]?.band)
                ConfidencePill("Wind", current.confidence[ForecastVariable.WIND_SPEED]?.band)
            }
        }
        item { Text("16-day outlook", style = MaterialTheme.typography.titleMedium) }
        items(result.daily.take(16)) { day ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(day.condition.displayName(), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${day.minimumTemperatureC.degrees()} / ${day.maximumTemperatureC.degrees()}")
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${day.precipitationProbabilityPercent.percent()} · ${day.precipitationMm.mm()}")
                        Text(day.confidence.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Text(
                "Weather model data delivered by Open-Meteo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrentConditionsCard(current: ConsensusPoint, result: EnsembleResult) {
    val point = current.consensus
    val zone = ZoneId.of(result.zoneId)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(point.condition.displayName(), style = MaterialTheme.typography.titleMedium)
            Text(point.temperatureC.oneDecimal("°C"), style = MaterialTheme.typography.displayMedium)
            Text("Feels like ${point.apparentTemperatureC.oneDecimal("°C")}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MetricRow("Rain probability", point.precipitationProbabilityPercent.percent())
            MetricRow("Expected precipitation", point.precipitationMm.mm())
            MetricRow("Humidity", point.relativeHumidityPercent.percent())
            MetricRow("Pressure", point.pressureHpa.zeroDecimal("hPa"))
            MetricRow(
                "Wind",
                "${point.windDirectionDegrees.compass()} ${point.windSpeedMs.oneDecimal("m/s")}",
            )
            MetricRow("Gusts", point.windGustMs.oneDecimal("m/s"))
            MetricRow("Forecast confidence", current.overallConfidence.name)
            MetricRow("Models available", current.members.size.toString())
            MetricRow("Last update", result.providerIssuedAt.values.maxOrNull()?.atZone(zone)?.format(TIME) ?: "—")
            MetricRow("Sunrise", point.sunrise?.atZone(zone)?.format(TIME) ?: "—")
            MetricRow("Sunset", point.sunset?.atZone(zone)?.format(TIME) ?: "—")
        }
    }
}

@Composable
private fun HourlyScreen(result: EnsembleResult) {
    val zone = ZoneId.of(result.zoneId)
    val upcoming = result.points.filter { it.timestamp >= result.generatedAt.minusSeconds(1_800) }
    val chartTimeline = result.centeredTechnicalTimeline()
    var showChart by rememberSaveable { mutableStateOf(true) }
    var selectedGroupIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedGroup = HOURLY_CHART_GROUPS[selectedGroupIndex]
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = showChart, onClick = { showChart = true }, label = { Text("Technical chart") })
                    FilterChip(selected = !showChart, onClick = { showChart = false }, label = { Text("Hourly table") })
                }
                Text(
                    "Charts open on 24 hours centred on now and can zoom out to 72 hours past and 72 hours ahead. " +
                        "The table continues through ${result.daily.lastOrNull()?.date ?: "the available horizon"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showChart) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(HOURLY_CHART_GROUPS.indices.toList()) { index ->
                        FilterChip(
                            selected = selectedGroupIndex == index,
                            onClick = { selectedGroupIndex = index },
                            label = { Text(HOURLY_CHART_GROUPS[index].title) },
                        )
                    }
                }
            }
            item {
                val chartSeries = selectedGroup.variables.map { variable ->
                    TechnicalSeries(
                        id = variable.id,
                        label = variable.label,
                        unit = selectedGroup.unit,
                        color = variable.color,
                        values = chartTimeline.map { variable.value(it.consensus) },
                    )
                }
                TechnicalTimeSeriesChart(
                    timestamps = chartTimeline.map { it.timestamp },
                    series = chartSeries,
                    zone = zone,
                    chartKey = "hourly-${selectedGroup.title}",
                    referenceTime = result.generatedAt,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Text(
                    "Curves sharing a chart use the same physical unit. This avoids visually comparing incompatible " +
                        "scales such as millimetres and percent. The past segment is recent archived model output, " +
                        "not a station observation or a forecast-verification shortcut. Missing values create real gaps.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Column {
                    HourlyRow("TIME", "TEMP", "RAIN", "MM", "WIND", header = true)
                    HorizontalDivider()
                }
            }
            items(upcoming) { item ->
                val point = item.consensus
                Column {
                    HourlyRow(
                        point.timestamp.atZone(zone).format(TIME),
                        point.temperatureC.degrees(),
                        point.precipitationProbabilityPercent.percent(),
                        point.precipitationMm.oneDecimal(""),
                        point.windSpeedMs.oneDecimal("m/s"),
                    )
                    Text(
                        "Feels ${point.apparentTemperatureC.degrees()} · gusts ${point.windGustMs.oneDecimal("m/s")} · " +
                            "humidity ${point.relativeHumidityPercent.percent()} · ${point.condition.displayName()}",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HourlyRow(
    time: String,
    temperature: String,
    probability: String,
    amount: String,
    wind: String,
    header: Boolean = false,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(time, modifier = Modifier.weight(1.15f), style = style)
        Text(temperature, modifier = Modifier.weight(0.9f), style = style)
        Text(probability, modifier = Modifier.weight(0.9f), style = style)
        Text(amount, modifier = Modifier.weight(0.7f), style = style)
        Text(wind, modifier = Modifier.weight(1.25f), style = style)
    }
}

private data class ComparisonVariable(
    val title: String,
    val variable: ForecastVariable,
    val value: (WeatherPoint) -> Double?,
    val suffix: String,
)

private val comparisonVariables = listOf(
    ComparisonVariable("Temperature", ForecastVariable.TEMPERATURE, { it.temperatureC }, "°C"),
    ComparisonVariable("Precipitation", ForecastVariable.PRECIPITATION_AMOUNT, { it.precipitationMm }, "mm"),
    ComparisonVariable("Rain probability", ForecastVariable.PRECIPITATION_PROBABILITY, { it.precipitationProbabilityPercent }, "%"),
    ComparisonVariable("Wind", ForecastVariable.WIND_SPEED, { it.windSpeedMs }, "m/s"),
    ComparisonVariable("Gusts", ForecastVariable.WIND_GUST, { it.windGustMs }, "m/s"),
    ComparisonVariable("Humidity", ForecastVariable.HUMIDITY, { it.relativeHumidityPercent }, "%"),
)

@Composable
private fun ModelComparisonScreen(result: EnsembleResult, learning: EnsembleLearningSummary) {
    val timeline = result.centeredTechnicalTimeline()
    if (timeline.isEmpty()) {
        CenterMessage("No comparison points are available.", false, Modifier)
        return
    }
    val initialTimeIndex = timeline.indices.minByOrNull { index ->
        kotlin.math.abs(timeline[index].timestamp.epochSecond - result.generatedAt.epochSecond)
    } ?: 0
    val timelineKey = "${timeline.first().timestamp.epochSecond}:${timeline.last().timestamp.epochSecond}"
    var selectedVariableIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTimeIndex by rememberSaveable(timelineKey) { mutableIntStateOf(initialTimeIndex) }
    val safeSelectedTimeIndex = selectedTimeIndex.coerceIn(0, timeline.lastIndex)
    val selectedVariable = comparisonVariables[selectedVariableIndex]
    val selected = timeline[safeSelectedTimeIndex]
    val zone = ZoneId.of(result.zoneId)
    val isRecentHistory = selected.timestamp < result.generatedAt.minusSeconds(1_800)
    val confidence = selected.confidence[selectedVariable.variable]
    val correctedValues = selected.members.mapNotNull { (provider, point) ->
        val value = selected.correctedMemberValues[selectedVariable.variable]?.get(provider)
            ?: selectedVariable.value(point)
        value?.let { provider to it }
    }.toMap()
    val effectiveWeights = selected.effectiveWeights[selectedVariable.variable].orEmpty()
    val consensusValue = selectedVariable.value(selected.consensus)
    val diagnostics = ModelDiagnosticsCalculator.calculate(correctedValues, effectiveWeights, consensusValue)
    val leadBucket = if (isRecentHistory) {
        null
    } else {
        Duration.between(result.generatedAt, selected.timestamp)
            .coerceAtLeast(Duration.ZERO)
            .horizonBucket()
    }
    val selectedSkills = leadBucket?.let { bucket ->
        learning.skills.filter {
            it.variable == selectedVariable.variable &&
                it.horizonBucket == bucket &&
                it.season == selected.timestamp.season()
        }
    }.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LearningStatusCard(learning)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(comparisonVariables.indices.toList()) { index ->
                    val variable = comparisonVariables[index]
                    FilterChip(
                        selected = selectedVariable == variable,
                        onClick = { selectedVariableIndex = index },
                        label = { Text(variable.title) },
                    )
                }
            }
        }
        item {
            val providerSeries = ProviderId.entries.mapNotNull { provider ->
                if (timeline.none { provider in it.members }) return@mapNotNull null
                TechnicalSeries(
                    id = provider.name,
                    label = provider.displayName,
                    unit = selectedVariable.suffix,
                    color = provider.technicalChartColor(),
                    values = timeline.map { point ->
                        point.correctedMemberValues[selectedVariable.variable]?.get(provider)
                            ?: point.members[provider]?.let(selectedVariable.value)
                    },
                )
            }
            TechnicalTimeSeriesChart(
                timestamps = timeline.map { it.timestamp },
                series = listOf(
                    TechnicalSeries(
                        id = "CONSENSUS",
                        label = "HForecast consensus",
                        unit = selectedVariable.suffix,
                        color = MaterialTheme.colorScheme.primary,
                        values = timeline.map { selectedVariable.value(it.consensus) },
                    ),
                ) + providerSeries,
                zone = zone,
                chartKey = "models-${selectedVariable.variable.name}",
                referenceTime = result.generatedAt,
                selectedPointIndex = safeSelectedTimeIndex,
                onPointSelected = { index -> selectedTimeIndex = index },
            )
        }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                timeline.indices.filter { it % 3 == 0 }.forEach { index ->
                    val point = timeline[index]
                    FilterChip(
                        selected = safeSelectedTimeIndex == index,
                        onClick = { selectedTimeIndex = index },
                        label = { Text(point.timestamp.atZone(zone).format(DAY_TIME)) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    selected.timestamp.atZone(zone).format(FULL_TIME),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (isRecentHistory) {
                        "Recent archived model output · not an observation and not used as a newly issued forecast"
                    } else {
                        "Forecast lead-time bucket $leadBucket"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ComparisonValueRow(
                if (isRecentHistory) {
                    "Weighted retrospective blend"
                } else if (learning.isActive) {
                    "Adaptive consensus"
                } else {
                    "Weighted consensus"
                },
                selectedVariable.value(selected.consensus),
                selectedVariable.suffix,
                emphasized = true,
            )
        }
        items(selected.members.entries.sortedBy { it.key.ordinal }) { (provider, point) ->
            val effectiveWeight = selected.effectiveWeights[selectedVariable.variable]?.get(provider)
            val rawValue = selectedVariable.value(point)
            val correctedValue = selected.correctedMemberValues[selectedVariable.variable]?.get(provider)
            val details = buildList {
                result.providerIssuedAt[provider]?.let {
                    add("Updated ${Duration.between(it, Instant.now()).coerceAtLeast(Duration.ZERO).humanAge()}")
                }
                effectiveWeight?.let { add("Weight ${(it * 100.0).roundToInt()}%") }
                if (rawValue != null && correctedValue != null && kotlin.math.abs(rawValue - correctedValue) >= 0.05) {
                    add("Raw ${rawValue.oneDecimal(selectedVariable.suffix)} after bias correction")
                }
                selectedSkills.firstOrNull { it.providerId == provider.name }?.let { skill ->
                    add(
                        "n=${skill.sampleCount} · bias ${skill.meanBias.signed(selectedVariable.suffix)} · " +
                            "MAE ${skill.meanAbsoluteError.oneDecimal(selectedVariable.suffix)} · " +
                            "RMSE ${skill.rootMeanSquaredError.oneDecimal(selectedVariable.suffix)}",
                    )
                }
            }
            ComparisonValueRow(
                provider.displayName,
                correctedValue ?: rawValue,
                selectedVariable.suffix,
                detail = details.joinToString(" · ").ifEmpty { null },
            )
        }
        item {
            ModelDiagnosticsCard(
                diagnostics = diagnostics,
                suffix = selectedVariable.suffix,
                contextLabel = if (isRecentHistory) {
                    "Corrected recent model output · excluded from forecast skill scoring"
                } else {
                    "Corrected deterministic values · lead-time bucket $leadBucket"
                },
            )
        }
        item {
            HorizontalDivider()
            MetricRow(
                "Model range",
                selected.modelSpread[selectedVariable.variable].oneDecimal(selectedVariable.suffix),
            )
            MetricRow("Confidence", confidence?.band?.name ?: ConfidenceBand.LOW.name)
            Text(
                confidence?.explanation ?: "No comparable model values",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedVariable.variable == ForecastVariable.PRECIPITATION_PROBABILITY &&
                selected.consensus.precipitationProbabilityPercent == null
            ) {
                Text(
                    "No upstream probability product is available; deterministic disagreement is not shown as probability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ModelDiagnosticsCard(
    diagnostics: dev.weather.ensemble.ModelDiagnostics,
    suffix: String,
    contextLabel: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("Distribution diagnostics", style = MaterialTheme.typography.titleMedium)
            Text(
                contextLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            MetricRow("Available values", diagnostics.availableModels.toString())
            MetricRow("Unweighted mean", diagnostics.arithmeticMean.oneDecimal(suffix))
            MetricRow("Median", diagnostics.median.oneDecimal(suffix))
            MetricRow("Population σ", diagnostics.populationStandardDeviation.oneDecimal(suffix))
            MetricRow("Range", diagnostics.range.oneDecimal(suffix))
            MetricRow("Consensus − median", diagnostics.consensusMinusMedian.signed(suffix))
            MetricRow("Effective model count", diagnostics.effectiveModelCount.precise(2))
            LearningDetail(
                "Dominant effective weight",
                diagnostics.dominantProvider?.let { provider ->
                    "${provider.displayName} · ${diagnostics.dominantWeight?.times(100.0)?.roundToInt()}%"
                } ?: "—",
            )
            Text(
                "The mean and median describe the model distribution; σ and range quantify disagreement. The " +
                    "effective count falls toward 1 as weight concentrates in one model and approaches the number " +
                    "of models when weights are balanced.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LearningStatusCard(summary: EnsembleLearningSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Adaptive model learning", style = MaterialTheme.typography.titleMedium)
            Text(
                if (summary.isActive) {
                    "Learning active"
                } else {
                    "Building verification history"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (summary.isActive) {
                    "Verified model skill is influencing at least one forecast context."
                } else {
                    "The transparent starter weights remain in control until enough comparable observations exist."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            LearningDetail("Observation reference", summary.referenceSource)
            summary.latestObservationTime?.let {
                LearningDetail("Latest observation", it.atZone(ZoneId.systemDefault()).format(FULL_TIME))
            }
            summary.observationStation?.let { station ->
                LearningDetail(
                    "Nearest station",
                    station + summary.observationDistanceKm?.let { " · ${"%.1f".format(Locale.US, it)} km" }.orEmpty(),
                )
            }
            HorizontalDivider()
            LearningDetail("Verified forecast–observation pairs", summary.verifiedComparisons.toString())
            LearningDetail("Provider skill records for this region and season", summary.learnedBuckets.toString())
            LearningDetail("Records currently influencing weights", summary.activeBuckets.toString())
            HorizontalDivider()
            Text("How learning is controlled", style = MaterialTheme.typography.labelLarge)
            Text(
                "Skill is separated by provider, variable, lead-time bucket, half-degree region and season. " +
                    "A context needs at least 24 verified samples from every participating model. Learned inverse-RMSE " +
                    "weights then blend in gradually and can contribute at most 75%. If the adaptive consensus becomes " +
                    "more than 2% worse than the fixed-weight baseline, that context returns to the starter weights.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LearningDetail(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ComparisonValueRow(
    name: String,
    value: Double?,
    suffix: String,
    emphasized: Boolean = false,
    detail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(name, fontWeight = if (emphasized) FontWeight.Bold else null)
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            value.oneDecimal(suffix),
            modifier = Modifier.widthIn(min = 68.dp),
            fontWeight = if (emphasized) FontWeight.Bold else null,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AboutScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What HForecast does", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "HForecast means Honest Forecast. It combines weather, model comparison, local forecast " +
                        "verification, maps, aurora and solar activity, an astronomy guide, air quality, alerts and " +
                        "a home-screen widget. Its purpose is to show both the answer and the evidence behind it.",
                )
            }
        }
        item {
            ExplanationCard(
                "Weather models and range",
                "The app independently requests MET Norway Nordic, ECMWF IFS, DWD ICON and NOAA GFS through " +
                    "Open-Meteo. MET Nordic provides high-resolution short-range coverage in the Nordic region; " +
                    "the global models continue farther ahead, with GFS providing the longest horizon. The Now " +
                    "value is the nearest forecast hour, not a live station measurement. Hourly data and the daily " +
                    "outlook continue only as far as upstream data is genuinely available, up to 16 days.",
            )
        }
        item {
            ExplanationCard(
                "Normalization and time alignment",
                "Every provider response is mapped into the same internal format: UTC timestamps and lead times, " +
                    "with the local IANA time zone retained for display. Temperatures use °C, wind uses m/s, " +
                    "precipitation uses mm and pressure uses hPa. HForecast aligns source points onto a common hourly " +
                    "timeline using bounded interpolation, without extending a model beyond its available horizon. " +
                    "Missing values remain missing instead of being silently replaced.",
            )
        }
        item {
            ExplanationCard(
                "Weighted consensus",
                "The consensus is not an arithmetic average. Each value has a configurable weight for its provider, " +
                    "weather variable and forecast lead time. Only models that actually provide that value participate, " +
                    "and their weights are normalized again for that hour. Wind direction is combined circularly, so " +
                    "north is treated correctly across 359° and 1°. The dominant condition is selected using the " +
                    "precipitation weights. Starter weights are explicit rules, not a permanent claim that one model " +
                    "is always best.",
            )
        }
        item {
            ExplanationCard(
                "Adaptive model learning",
                "Each downloaded run is archived as the forecast that really existed at that time. In Sweden, later " +
                    "refreshes match it to quality-controlled SMHI station observations. The app maintains " +
                    "exponentially weighted bias, MAE and RMSE by provider, variable, five lead-time ranges, 0.5° " +
                    "region and season. Once every participating model has at least 24 samples, inverse-squared-RMSE " +
                    "skill weights blend gradually with the starter rules. The learned share is capped at 75%, and " +
                    "bias correction is introduced gradually for suitable continuous variables.",
            )
        }
        item {
            ExplanationCard(
                "Verification safeguards",
                "HForecast never treats downloaded historical model output or reanalysis as the forecast that was " +
                    "available earlier. It scores only runs saved on this device before their target time. The adaptive consensus " +
                    "and a fixed-weight baseline are archived and checked side by side; if adaptive RMSE becomes more " +
                    "than 2% worse, that context falls back to the baseline. Detailed forecast runs are retained for " +
                    "60 days and detailed verification records for 180 days. Outside SMHI observation coverage, the " +
                    "transparent starter weights remain active.",
            )
        }
        item {
            ExplanationCard(
                "Models screen, spread and confidence",
                "Choose a variable and time to compare every available model with the consensus, or touch and zoom " +
                    "the multi-model timeline. Each row shows the effective weight, source age, visible bias correction " +
                    "and any matching historical n, bias, MAE and RMSE. The diagnostics panel adds unweighted mean, " +
                    "median, population standard deviation, range, consensus-minus-median and effective model count. " +
                    "HIGH, MEDIUM and LOW confidence remains a transparent agreement and availability indicator, not " +
                    "an unexplained AI certainty score or a guarantee.",
            )
        }
        item {
            ExplanationCard(
                "Navigation and technical charts",
                "Swipe horizontally between top-level sections or tap their names. Hourly can switch between the " +
                    "compact table and a technical time-series view. Chart families only combine curves with the same " +
                    "physical unit. Every time chart opens on a 24-hour window centred on now, showing 12 hours on " +
                    "each side. Pinch out to see as much as 72 hours past and 72 hours ahead, or zoom in and drag " +
                    "horizontally; the Y range rescales automatically. Tapping a curve or its legend isolates it, " +
                    "and the NOW marker separates retrospective model output from the forecast.",
            )
        }
        item {
            ExplanationCard(
                "Recent model history",
                "Open-Meteo requests include up to 72 recent archived model hours so technical charts have context " +
                    "before now. These values are model output, not station observations and not necessarily the " +
                    "forecast that HForecast had stored before that target time. They are therefore labelled as " +
                    "retrospective and cannot enter adaptive skill scoring. Verification still accepts only a run " +
                    "that was saved on this device before its target time.",
            )
        }
        item {
            ExplanationCard(
                "Precipitation honesty",
                "Probability, expected amount, rain and snow are separate variables. A rain probability is displayed " +
                    "only when an upstream probability product exists; deterministic model disagreement is never " +
                    "converted into a fake probability. Amount values are normalized to millimetres. Current " +
                    "deterministic-model spread describes disagreement, but a calibrated likely range requires real " +
                    "ensemble members and is not claimed by this version.",
            )
        }
        item {
            ExplanationCard(
                "Location, map and spatial resolution",
                "Current location uses a one-shot Android fused-location request and a recent fix may be reused for " +
                    "30 minutes; GPS does not run continuously. On the interactive map you can zoom to roads and place " +
                    "labels, then tap an exact point for a more local request. Weather models still calculate on fixed " +
                    "grids, so exact GPS coordinates do not create street-level weather. HForecast displays the " +
                    "actual grid coordinate returned by each provider so you can see that spatial limitation.",
            )
        }
        item {
            ExplanationCard(
                "Caching, offline use and failures",
                "Room stores the latest successful result for each model and precise 0.01° location bucket. If the " +
                    "network is unavailable or a provider fails, other providers continue independently and compatible " +
                    "cached values may still be shown. Cached data is explicitly labelled with its age. HForecast " +
                    "does not silently present old data as current and does not reuse a forecast across a broad " +
                    "county-sized area.",
            )
        }
        item {
            ExplanationCard(
                "Background refresh and notifications",
                "A battery-conscious WorkManager job refreshes the saved location with network and battery constraints; " +
                    "background work never wakes GPS. Forecast notifications are optional and can run every 1, 3, 6 or " +
                    "12 hours inside a selected local-time window. Aurora alerts are separate and require the selected " +
                    "OVATION threshold, darkness and cloud cover below 75%. Android controls exact delivery timing and " +
                    "requires notification permission on recent versions.",
            )
        }
        item {
            ExplanationCard(
                "Aurora and solar activity",
                "NOAA SWPC provides the current OVATION aurora oval, the nearest grid-cell probability, planetary Kp " +
                    "and its outlook, solar-wind speed, IMF Bt and Bz, 10.7 cm solar flux, and NOAA G/R/S activity " +
                    "scales. The polar map includes land, latitude circles and longitude meridians. HForecast evaluates " +
                    "model probability, local darkness and forecast cloud separately. Terrain, obstructions and light " +
                    "pollution are not modelled, and no single solar value guarantees a visible aurora.",
            )
        }
        item {
            ExplanationCard(
                "Sky guide",
                "Sun and twilight state, Moon phase and illumination, rise/set times, planet positions and a curated " +
                    "catalogue of bright stars are calculated locally for the selected place and current time. Drag " +
                    "the circular sky chart directly like a dial and align its N/E/S/W rim with a separate phone " +
                    "compass. Labels are placed with collision avoidance. Named bright " +
                    "objects can be tapped for observing details. This is a naked-eye guide, not a complete star " +
                    "catalogue, telescope controller or automatic compass view.",
            )
        }
        item {
            ExplanationCard(
                "Air quality and atmosphere",
                "Open-Meteo supplies CAMS-backed European AQI, PM2.5, PM10, nitrogen dioxide, ozone, sulphur dioxide, " +
                    "carbon monoxide, dust, aerosol optical depth, UV and European pollen forecasts, including a " +
                    "seven-day outlook. Values refer to the returned model grid cell and are estimates, not a nearby " +
                    "regulatory sensor, medical advice or an emergency warning.",
            )
        }
        item {
            ExplanationCard(
                "Widget, themes and smartwatch",
                "The detailed home-screen widget reads the latest local cache, shows its age and offers a top-left " +
                    "refresh action that uses the saved location without starting GPS. The widget follows Android's " +
                    "system light/dark mode, while HForecast itself supports system, light and dark themes. Android " +
                    "notifications can be mirrored to an Amazfit through Zepp app notification " +
                    "forwarding, but this Android app does not directly read watch sensors or maintain an always-on " +
                    "Bluetooth connection.",
            )
        }
        item {
            ExplanationCard(
                "Privacy, network use and limitations",
                "Selected coordinates are sent only to the forecast and environmental services needed for a request. " +
                    "Forecast history, observations and learned skill stay in this app's local database. Weather uses " +
                    "Open-Meteo-delivered model data, aurora uses NOAA SWPC and verification in Sweden uses SMHI. " +
                    "Environmental products can be delayed, unavailable or wrong; always use official warnings and " +
                    "specialist guidance for safety-critical decisions.",
            )
        }
    }
}

@Composable
private fun ExplanationCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsScreen(
    preferences: NotificationPreferences,
    notificationPermissionGranted: Boolean,
    onChange: (NotificationPreferences) -> Unit,
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
) {
    val scheduleEnabled = preferences.enabled || preferences.auroraAlertsEnabled
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Appearance", style = MaterialTheme.typography.headlineSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppTheme.entries) { theme ->
                    FilterChip(
                        selected = appTheme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(theme.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    )
                }
            }
        }
        item { HorizontalDivider() }
        item {
            Text("Forecast notifications", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Receive a compact consensus update at a battery-friendly interval and only within your " +
                    "chosen local-time window.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Periodic notifications", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (preferences.enabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = preferences.enabled,
                    onCheckedChange = { onChange(preferences.copy(enabled = it)) },
                )
            }
        }
        if (scheduleEnabled && !notificationPermissionGranted) {
            item {
                Text(
                    "Android notification permission is required. Enabling this setting will request it.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aurora opportunity alerts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Only alerts when OVATION exceeds the threshold, it is dark, and cloud cover is below 75%.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = preferences.auroraAlertsEnabled,
                    onCheckedChange = { onChange(preferences.copy(auroraAlertsEnabled = it)) },
                )
            }
        }
        item {
            Text("Aurora threshold", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(5, 10, 20, 30)) { threshold ->
                    FilterChip(
                        selected = preferences.auroraProbabilityThreshold == threshold,
                        onClick = { onChange(preferences.copy(auroraProbabilityThreshold = threshold)) },
                        enabled = preferences.auroraAlertsEnabled,
                        label = { Text("$threshold%") },
                    )
                }
            }
        }
        item {
            Text("Interval", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(1, 3, 6, 12)) { hours ->
                    FilterChip(
                        selected = preferences.intervalHours == hours,
                        onClick = { onChange(preferences.copy(intervalHours = hours)) },
                        enabled = scheduleEnabled,
                        label = { Text(if (hours == 1) "Every hour" else "Every $hours hours") },
                    )
                }
            }
        }
        item {
            HourPicker(
                title = "Send from",
                selectedHour = preferences.startHour,
                enabled = scheduleEnabled,
                onSelected = { onChange(preferences.copy(startHour = it)) },
            )
        }
        item {
            HourPicker(
                title = "Send until",
                selectedHour = preferences.endHour,
                enabled = scheduleEnabled,
                onSelected = { onChange(preferences.copy(endHour = it)) },
            )
        }
        item {
            Text(
                if (preferences.startHour == preferences.endHour) {
                    "Notifications may be sent all day."
                } else {
                    "Active window: ${preferences.startHour.hourLabel()}–${preferences.endHour.hourLabel()} " +
                        "local time. The end time is exclusive. Overnight windows are supported."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Android schedules background work inexactly to conserve battery, so delivery can be delayed. " +
                    "No notification is shown outside the selected window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { HorizontalDivider() }
        item {
            Text("Home-screen widgets", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Long-press an empty area of your home screen, open Widgets, and choose HForecast Detailed. " +
                    "It shows the latest forecast stored on this device and labels its age. Tap the refresh " +
                    "symbol to request fresh data using the saved location, or tap elsewhere to open HForecast. " +
                    "Widget refresh never starts GPS.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { HorizontalDivider() }
        item {
            Text("Amazfit / Zepp", style = MaterialTheme.typography.headlineSmall)
            Text(
                "To mirror HForecast alerts, keep the watch connected, then open Zepp: Profile > your Amazfit > " +
                    "Notifications and Reminders > App Notifications. Grant Zepp notification access and enable " +
                    "HForecast under Manage Apps. If HForecast is not listed yet, let it produce one phone " +
                    "notification first. Allow Zepp to run in the background so Android does not break the " +
                    "watch connection. The sky map remains manually aligned with a compass app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HourPicker(
    title: String,
    selectedHour: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Text("$title ${selectedHour.hourLabel()}", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items((0..23).toList()) { hour ->
            FilterChip(
                selected = selectedHour == hour,
                onClick = { onSelected(hour) },
                enabled = enabled,
                label = { Text(hour.hourLabel()) },
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConfidencePill(label: String, band: ConfidenceBand?) {
    Card {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text((band ?: ConfidenceBand.LOW).name, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CenterMessage(text: String, showProgress: Boolean, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(text, modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Location is needed to select the forecast grid for your current position.")
            Button(onClick = onRequest) { Text("Allow location") }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE HH", Locale.ENGLISH)
private val FULL_TIME = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.ENGLISH)

private fun EnsembleResult.centeredTechnicalTimeline(hoursEachSide: Long = 72): List<ConsensusPoint> {
    val centerTime = current?.timestamp ?: generatedAt
    val firstIncluded = centerTime.minus(Duration.ofHours(hoursEachSide))
    val lastIncluded = centerTime.plus(Duration.ofHours(hoursEachSide))
    return points.filter { point ->
        !point.timestamp.isBefore(firstIncluded) && !point.timestamp.isAfter(lastIncluded)
    }
}

private fun dev.weather.core.GeoLocation.displayLabel(): String =
    displayName ?: String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude)

private fun GeoLocation.coordinateLabel(): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

private fun Int.hourLabel(): String = String.format(Locale.US, "%02d:00", this)

private fun Double?.oneDecimal(suffix: String): String =
    this?.let { String.format(Locale.getDefault(), "%.1f %s", it, suffix).trim() } ?: "—"

private fun Double?.signed(suffix: String): String =
    this?.let { String.format(Locale.getDefault(), "%+.1f %s", it, suffix).trim() } ?: "—"

private fun Double?.precise(decimals: Int): String =
    this?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—"

private fun Double?.zeroDecimal(suffix: String): String =
    this?.let { "${it.roundToInt()} $suffix" } ?: "—"

private fun Double?.degrees(): String = this?.let { "${it.roundToInt()}°" } ?: "—"
private fun Double?.percent(): String = this?.let { "${it.roundToInt()}%" } ?: "—"
private fun Double?.mm(): String = this?.let { String.format(Locale.getDefault(), "%.1f mm", it) } ?: "—"

private fun Double?.compass(): String {
    if (this == null) return "—"
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return directions[((this / 45.0).roundToInt() % 8 + 8) % 8]
}

private fun WeatherCondition.displayName(): String = name.lowercase().replace('_', ' ')
    .replaceFirstChar { it.titlecase() }

private fun Duration.humanAge(): String = when {
    toMinutes() < 1 -> "just now"
    toHours() < 1 -> "${toMinutes()} minutes ago"
    toDays() < 1 -> "${toHours()} hours ago"
    else -> "${toDays()} days ago"
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
