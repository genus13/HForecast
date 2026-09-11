package dev.weather.ensemble

import com.google.common.truth.Truth.assertThat
import dev.weather.core.ForecastVariable
import dev.weather.core.GeoLocation
import dev.weather.core.ProviderId
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AdaptiveEnsembleModelTest {
    private val location = GeoLocation(59.33, 18.07)
    private val target = Instant.parse("2026-01-15T12:00:00Z")
    private val lead = Duration.ofHours(12)
    private val prior = EnsembleWeightProvider { provider, _, _, _, _ ->
        when (provider) {
            ProviderId.ECMWF -> 0.7
            ProviderId.ICON -> 0.3
            else -> 0.0
        }
    }

    @Test
    fun `keeps manual prior until every participating model has enough observations`() {
        val model = AdaptiveEnsembleModel(prior)
        model.replaceSkills(
            listOf(
                skill(ProviderId.ECMWF.name, samples = 23, rmse = 0.5),
                skill(ProviderId.ICON.name, samples = 23, rmse = 2.0),
            ),
        )

        assertThat(weight(model, ProviderId.ECMWF)).isWithin(0.0001).of(0.7)
        assertThat(weight(model, ProviderId.ICON)).isWithin(0.0001).of(0.3)
    }

    @Test
    fun `gradually gives more weight to the lower error model`() {
        val model = AdaptiveEnsembleModel(prior)
        model.replaceSkills(
            listOf(
                skill(ProviderId.ECMWF.name, samples = 160, rmse = 0.5),
                skill(ProviderId.ICON.name, samples = 160, rmse = 2.0),
            ),
        )

        val ecmwfWeight = weight(model, ProviderId.ECMWF)
        val iconWeight = weight(model, ProviderId.ICON)

        assertThat(ecmwfWeight).isGreaterThan(0.7)
        assertThat(iconWeight).isLessThan(0.3)
        assertThat(ecmwfWeight + iconWeight).isWithin(0.0001).of(1.0)
    }

    @Test
    fun `falls back when adaptive consensus is worse than fixed baseline`() {
        val model = AdaptiveEnsembleModel(prior)
        model.replaceSkills(
            listOf(
                skill(ProviderId.ECMWF.name, samples = 160, rmse = 0.5),
                skill(ProviderId.ICON.name, samples = 160, rmse = 2.0),
                skill(LearnedForecastIds.ADAPTIVE_CONSENSUS, samples = 160, rmse = 1.2),
                skill(LearnedForecastIds.MANUAL_BASELINE, samples = 160, rmse = 1.0),
            ),
        )

        assertThat(weight(model, ProviderId.ECMWF)).isWithin(0.0001).of(0.7)
        assertThat(weight(model, ProviderId.ICON)).isWithin(0.0001).of(0.3)
    }

    @Test
    fun `bias correction is learned conservatively`() {
        val model = AdaptiveEnsembleModel(prior)
        model.replaceSkills(listOf(skill(ProviderId.ECMWF.name, samples = 160, rmse = 1.0, bias = 2.0)))

        val corrected = model.correct(
            ProviderId.ECMWF,
            ForecastVariable.TEMPERATURE,
            lead,
            10.0,
            location,
            target,
        )

        assertThat(corrected).isWithin(0.0001).of(8.4)
    }

    @Test
    fun `uses stable horizon region and seasonal contexts`() {
        assertThat(Duration.ofHours(23).horizonBucket()).isEqualTo("00-24h")
        assertThat(Duration.ofHours(24).horizonBucket()).isEqualTo("24-48h")
        assertThat(location.skillRegionKey()).isEqualTo("59.25,18.25")
        assertThat(target.season()).isEqualTo("WINTER")
    }

    private fun weight(model: AdaptiveEnsembleModel, provider: ProviderId): Double = model.weight(
        provider,
        ForecastVariable.TEMPERATURE,
        lead,
        location,
        target,
    )

    private fun skill(
        provider: String,
        samples: Long,
        rmse: Double,
        bias: Double = 0.0,
    ) = LearnedSkill(
        providerId = provider,
        variable = ForecastVariable.TEMPERATURE,
        horizonBucket = lead.horizonBucket(),
        regionKey = location.skillRegionKey(),
        season = target.season(),
        sampleCount = samples,
        meanBias = bias,
        meanAbsoluteError = rmse,
        rootMeanSquaredError = rmse,
    )
}
