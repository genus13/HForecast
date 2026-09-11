package dev.weather.hforecast.data

import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import dev.weather.core.WeatherForecast
import dev.weather.core.WeatherProvider
import dev.weather.database.ForecastCacheDao
import dev.weather.database.cacheLocationKey
import dev.weather.database.toCacheEntities
import dev.weather.database.toDomain
import dev.weather.ensemble.EnsembleLearningSummary
import dev.weather.ensemble.EnsembleResult
import dev.weather.ensemble.LearnedForecastIds
import dev.weather.ensemble.WeightedEnsembleEngine
import dev.weather.hforecast.di.BaselineEnsemble
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ForecastRepositoryResult(
    val forecast: EnsembleResult,
    val isFromCache: Boolean,
    val providerErrors: Map<ProviderId, String>,
    val hadFreshData: Boolean,
    val learning: EnsembleLearningSummary = EnsembleLearningSummary(),
)

@Singleton
class ForecastRepository @Inject constructor(
    providerSet: Set<@JvmSuppressWildcards WeatherProvider>,
    private val cacheDao: ForecastCacheDao,
    private val ensembleEngine: WeightedEnsembleEngine,
    @param:BaselineEnsemble private val baselineEngine: WeightedEnsembleEngine,
    private val learningCoordinator: ForecastLearningCoordinator,
) {
    private val providers = providerSet.sortedBy { it.id.ordinal }
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    suspend fun cached(location: GeoLocation): ForecastRepositoryResult? = withContext(ioDispatcher) {
        val forecasts = cachedForecastsNear(location)
        if (forecasts.isEmpty()) return@withContext null
        val learning = runCatching {
            learningCoordinator.prepare(location, fetchObservations = false)
        }.getOrDefault(EnsembleLearningSummary(lastError = "Could not load learned skill"))
        ForecastRepositoryResult(
            forecast = ensembleEngine.calculate(
                forecasts,
                generatedAt = Instant.now(),
                location = location,
            ),
            isFromCache = true,
            providerErrors = emptyMap(),
            hadFreshData = false,
            learning = learning,
        )
    }

    suspend fun refresh(location: GeoLocation): ForecastRepositoryResult = supervisorScope {
        val attempts = providers.map { provider ->
            async(ioDispatcher) { provider.id to runCatching { provider.getForecast(location.latitude, location.longitude) } }
        }.awaitAll().toMap()

        val fresh = attempts.mapNotNull { (provider, result) ->
            result.getOrNull()?.let { provider to it }
        }.toMap()
        fresh.values.forEach { forecast ->
            // A cache write failure must not hide a valid network forecast.
            runCatching {
                withContext(ioDispatcher) {
                    val (run, points) = forecast.toCacheEntities(location)
                    cacheDao.archive(run, points)
                }
            }
        }

        val learning = runCatching {
            learningCoordinator.prepare(location, fetchObservations = fresh.isNotEmpty())
        }.getOrDefault(EnsembleLearningSummary(lastError = "Adaptive verification is temporarily unavailable"))

        val cached = cachedForecastsNear(location).associateBy { it.provider }
        val combined = providers.mapNotNull { provider -> fresh[provider.id] ?: cached[provider.id] }
            .distinctBy { it.provider }
        check(combined.isNotEmpty()) { "No provider returned a forecast and no cached forecast is available" }

        val generatedAt = Instant.now()
        val forecast = ensembleEngine.calculate(combined, generatedAt = generatedAt, location = location)
        if (fresh.isNotEmpty()) {
            runCatching {
                learningCoordinator.archiveConsensus(
                    forecast,
                    LearnedForecastIds.ADAPTIVE_CONSENSUS,
                    "HForecast adaptive consensus",
                )
                learningCoordinator.archiveConsensus(
                    baselineEngine.calculate(combined, generatedAt = generatedAt, location = location),
                    LearnedForecastIds.MANUAL_BASELINE,
                    "HForecast manual-weight baseline",
                )
            }
        }
        ForecastRepositoryResult(
            forecast = forecast,
            isFromCache = fresh.isEmpty(),
            providerErrors = attempts.mapNotNull { (provider, result) ->
                result.exceptionOrNull()?.let { provider to (it.message ?: it::class.java.simpleName) }
            }.toMap(),
            hadFreshData = fresh.isNotEmpty(),
            learning = learning,
        )
    }

    private suspend fun cachedForecastsNear(location: GeoLocation): List<WeatherForecast> =
        cacheDao.forecastsForLocation(location.cacheLocationKey())
            .map { it.toDomain() }
            .distinctBy { it.provider }
}
