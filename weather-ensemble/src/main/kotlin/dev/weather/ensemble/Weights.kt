package dev.weather.ensemble

import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import java.time.Duration
import java.time.Instant
import java.time.Month
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

fun interface EnsembleWeightProvider {
    fun weight(
        provider: ProviderId,
        variable: ForecastVariable,
        leadTime: Duration,
        location: GeoLocation,
        targetTime: Instant,
    ): Double
}

data class WeightRule(
    val provider: ProviderId,
    val variable: ForecastVariable,
    val minimumLeadHours: Long = 0,
    val maximumLeadHoursExclusive: Long = Long.MAX_VALUE,
    val weight: Double,
) {
    init {
        require(weight >= 0.0)
        require(maximumLeadHoursExclusive > minimumLeadHours)
    }
}

class ConfigurableWeightProvider(
    private val rules: List<WeightRule>,
    private val fallbackWeight: Double = 1.0,
) : EnsembleWeightProvider {
    override fun weight(
        provider: ProviderId,
        variable: ForecastVariable,
        leadTime: Duration,
        location: GeoLocation,
        targetTime: Instant,
    ): Double {
        val hour = leadTime.toHours().coerceAtLeast(0)
        return rules
            .filter {
                it.provider == provider &&
                    it.variable == variable &&
                    hour >= it.minimumLeadHours &&
                    hour < it.maximumLeadHoursExclusive
            }
            .lastOrNull()
            ?.weight
            ?: fallbackWeight
    }

    companion object {
        /** Starter values only; this object is designed to be replaced by learned skill weights. */
        fun nordicStarter(): ConfigurableWeightProvider {
            val rules = ForecastVariable.entries.flatMap { variable ->
                listOf(
                    WeightRule(ProviderId.MET_NORWAY, variable, 0, 60, 0.40),
                    WeightRule(ProviderId.ECMWF, variable, 0, 60, 0.25),
                    WeightRule(ProviderId.ICON, variable, 0, 60, 0.22),
                    WeightRule(ProviderId.GFS, variable, 0, 60, 0.13),
                    WeightRule(ProviderId.ECMWF, variable, 60, 72, 0.40),
                    WeightRule(ProviderId.ICON, variable, 60, 72, 0.35),
                    WeightRule(ProviderId.GFS, variable, 60, 72, 0.25),
                    WeightRule(ProviderId.ECMWF, variable, 72, Long.MAX_VALUE, 0.45),
                    WeightRule(ProviderId.ICON, variable, 72, Long.MAX_VALUE, 0.30),
                    WeightRule(ProviderId.GFS, variable, 72, Long.MAX_VALUE, 0.25),
                )
            }.toMutableList()

            listOf(
                ForecastVariable.PRECIPITATION_AMOUNT,
                ForecastVariable.PRECIPITATION_PROBABILITY,
                ForecastVariable.RAIN,
                ForecastVariable.SNOWFALL,
                ForecastVariable.WIND_SPEED,
                ForecastVariable.WIND_GUST,
            ).forEach { variable ->
                rules += WeightRule(ProviderId.MET_NORWAY, variable, 0, 60, 0.42)
                rules += WeightRule(ProviderId.ECMWF, variable, 0, 60, 0.20)
                rules += WeightRule(ProviderId.ICON, variable, 0, 60, 0.25)
                rules += WeightRule(ProviderId.GFS, variable, 0, 60, 0.13)
            }
            return ConfigurableWeightProvider(rules, fallbackWeight = 0.0)
        }
    }
}

fun interface BiasCorrector {
    fun correct(
        provider: ProviderId,
        variable: ForecastVariable,
        leadTime: Duration,
        rawValue: Double,
        location: GeoLocation,
        targetTime: Instant,
    ): Double
}

object NoBiasCorrection : BiasCorrector {
    override fun correct(
        provider: ProviderId,
        variable: ForecastVariable,
        leadTime: Duration,
        rawValue: Double,
        location: GeoLocation,
        targetTime: Instant,
    ): Double = rawValue
}

class FixedBiasCorrector(
    private val biases: Map<Pair<ProviderId, ForecastVariable>, Double>,
) : BiasCorrector {
    override fun correct(
        provider: ProviderId,
        variable: ForecastVariable,
        leadTime: Duration,
        rawValue: Double,
        location: GeoLocation,
        targetTime: Instant,
    ): Double = rawValue - (biases[provider to variable] ?: 0.0)
}

data class LearnedSkill(
    val providerId: String,
    val variable: ForecastVariable,
    val horizonBucket: String,
    val regionKey: String,
    val season: String,
    val sampleCount: Long,
    val meanBias: Double,
    val meanAbsoluteError: Double,
    val rootMeanSquaredError: Double,
)

data class EnsembleLearningSummary(
    val referenceSource: String = "SMHI station observations",
    val latestObservationTime: Instant? = null,
    val verifiedComparisons: Long = 0,
    val learnedBuckets: Int = 0,
    val activeBuckets: Int = 0,
    val observationStation: String? = null,
    val observationDistanceKm: Double? = null,
    val lastError: String? = null,
    /** Local verified model skills exposed for transparent technical inspection. */
    val skills: List<LearnedSkill> = emptyList(),
) {
    val isActive: Boolean get() = activeBuckets > 0
}

interface AdaptiveEnsembleController : EnsembleWeightProvider, BiasCorrector {
    fun replaceSkills(skills: Collection<LearnedSkill>)
    fun skills(): Collection<LearnedSkill>
}

object LearnedForecastIds {
    const val ADAPTIVE_CONSENSUS = "HFORECAST_ADAPTIVE"
    const val MANUAL_BASELINE = "HFORECAST_BASELINE"
}

/**
 * Thread-safe adaptive model. Learned weights remain strongly shrunk towards the explicit
 * starter configuration until enough independent verification samples exist.
 */
class AdaptiveEnsembleModel(
    private val prior: EnsembleWeightProvider,
    private val minimumSamples: Long = 24,
    private val fullLearningSamples: Long = 160,
    private val maximumLearnedShare: Double = 0.75,
) : AdaptiveEnsembleController {
    private val state = AtomicReference<Map<SkillKey, LearnedSkill>>(emptyMap())

    override fun replaceSkills(skills: Collection<LearnedSkill>) {
        state.set(skills.associateBy(::keyOf))
    }

    override fun skills(): Collection<LearnedSkill> = state.get().values

    override fun weight(
        provider: ProviderId,
        variable: ForecastVariable,
        leadTime: Duration,
        location: GeoLocation,
        targetTime: Instant,
    ): Double {
        val priorWeight = prior.weight(provider, variable, leadTime, location, targetTime)
        val context = SkillContext(variable, leadTime.horizonBucket(), location.skillRegionKey(), targetTime.season())
        val providerSkills = ProviderId.entries.mapNotNull { candidate ->
            val candidatePrior = prior.weight(candidate, variable, leadTime, location, targetTime)
            if (candidatePrior <= 0.0) return@mapNotNull null
            val skill = state.get()[SkillKey(candidate.name, context.variable, context.horizon, context.region, context.season)]
                ?: return@mapNotNull null
            candidate to (candidatePrior to skill)
        }.toMap()
        val expectedProviders = ProviderId.entries.count {
            prior.weight(it, variable, leadTime, location, targetTime) > 0.0
        }
        if (providerSkills.size < expectedProviders || providerSkills.values.any { it.second.sampleCount < minimumSamples }) {
            return priorWeight
        }
        if (!adaptiveConsensusIsSafe(context)) return priorWeight

        val priorTotal = providerSkills.values.sumOf { it.first }
        val adaptiveRaw = providerSkills.mapValues { (_, pair) ->
            1.0 / (pair.second.rootMeanSquaredError * pair.second.rootMeanSquaredError + errorFloorSquared(variable))
        }
        val adaptiveTotal = adaptiveRaw.values.sum()
        val minimumCount = providerSkills.values.minOf { it.second.sampleCount }
        val progress = ((minimumCount - minimumSamples).toDouble() /
            (fullLearningSamples - minimumSamples).coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
        val learnedShare = progress * maximumLearnedShare
        val priorNormalized = if (priorTotal > 0.0) priorWeight / priorTotal else 0.0
        val adaptiveNormalized = adaptiveRaw[provider]?.div(adaptiveTotal)?.takeIf { it.isFinite() } ?: 0.0
        return (1.0 - learnedShare) * priorNormalized + learnedShare * adaptiveNormalized
    }

    override fun correct(
        provider: ProviderId,
        variable: ForecastVariable,
        leadTime: Duration,
        rawValue: Double,
        location: GeoLocation,
        targetTime: Instant,
    ): Double {
        if (variable !in BIAS_CORRECTED_VARIABLES) return rawValue
        val context = SkillContext(variable, leadTime.horizonBucket(), location.skillRegionKey(), targetTime.season())
        if (!adaptiveConsensusIsSafe(context)) return rawValue
        val skill = state.get()[SkillKey(provider.name, variable, context.horizon, context.region, context.season)]
            ?: return rawValue
        if (skill.sampleCount < minimumSamples) return rawValue
        val progress = ((skill.sampleCount - minimumSamples).toDouble() /
            (fullLearningSamples - minimumSamples).coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
        return rawValue - skill.meanBias * progress.coerceAtMost(0.80)
    }

    private fun adaptiveConsensusIsSafe(context: SkillContext): Boolean {
        val skills = state.get()
        val adaptive = skills[SkillKey(LearnedForecastIds.ADAPTIVE_CONSENSUS, context.variable, context.horizon, context.region, context.season)]
        val baseline = skills[SkillKey(LearnedForecastIds.MANUAL_BASELINE, context.variable, context.horizon, context.region, context.season)]
        if (adaptive == null || baseline == null ||
            adaptive.sampleCount < minimumSamples || baseline.sampleCount < minimumSamples
        ) return true
        return adaptive.rootMeanSquaredError <= baseline.rootMeanSquaredError * 1.02
    }

    private data class SkillContext(
        val variable: ForecastVariable,
        val horizon: String,
        val region: String,
        val season: String,
    )

    private data class SkillKey(
        val providerId: String,
        val variable: ForecastVariable,
        val horizon: String,
        val region: String,
        val season: String,
    )

    private fun keyOf(skill: LearnedSkill) = SkillKey(
        skill.providerId,
        skill.variable,
        skill.horizonBucket,
        skill.regionKey,
        skill.season,
    )

    private fun errorFloorSquared(variable: ForecastVariable): Double {
        val floor = when (variable) {
            ForecastVariable.TEMPERATURE,
            ForecastVariable.APPARENT_TEMPERATURE,
            ForecastVariable.MINIMUM_TEMPERATURE,
            ForecastVariable.MAXIMUM_TEMPERATURE,
            ForecastVariable.DEW_POINT -> 0.25
            ForecastVariable.PRECIPITATION_AMOUNT,
            ForecastVariable.RAIN,
            ForecastVariable.SNOWFALL -> 0.20
            ForecastVariable.PRECIPITATION_PROBABILITY -> 5.0
            ForecastVariable.WIND_SPEED,
            ForecastVariable.WIND_GUST -> 0.25
            ForecastVariable.WIND_DIRECTION -> 5.0
            ForecastVariable.HUMIDITY,
            ForecastVariable.CLOUD_COVER -> 2.0
            ForecastVariable.PRESSURE -> 0.5
            ForecastVariable.VISIBILITY -> 250.0
            ForecastVariable.UV_INDEX -> 0.2
        }
        return floor * floor
    }

    companion object {
        private val BIAS_CORRECTED_VARIABLES = setOf(
            ForecastVariable.TEMPERATURE,
            ForecastVariable.HUMIDITY,
            ForecastVariable.DEW_POINT,
            ForecastVariable.PRESSURE,
            ForecastVariable.WIND_SPEED,
            ForecastVariable.WIND_DIRECTION,
            ForecastVariable.WIND_GUST,
        )
    }
}

fun Duration.horizonBucket(): String = when (toHours().coerceAtLeast(0)) {
    in 0..<24 -> "00-24h"
    in 24..<48 -> "24-48h"
    in 48..<72 -> "48-72h"
    in 72..<120 -> "72-120h"
    else -> "120h+"
}

fun GeoLocation.skillRegionKey(): String = String.format(
    Locale.US,
    "%.2f,%.2f",
    floor(latitude * 2.0) / 2.0 + 0.25,
    floor(longitude * 2.0) / 2.0 + 0.25,
)

fun Instant.season(): String = when (atZone(java.time.ZoneOffset.UTC).month) {
    Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> "WINTER"
    Month.MARCH, Month.APRIL, Month.MAY -> "SPRING"
    Month.JUNE, Month.JULY, Month.AUGUST -> "SUMMER"
    Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> "AUTUMN"
}
