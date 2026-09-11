package dev.weather.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ChartViewportSpecTest {
    @Test
    fun `centers the initial 24 hour viewport on now`() {
        val spec = chartViewportSpec(
            pointCount = 145,
            centerIndex = 72,
            initialVisibleHours = 24f,
            maximumVisibleHours = 144f,
        )

        assertThat(spec.initialSpan).isEqualTo(24f)
        assertThat(spec.initialStart).isEqualTo(60f)
    }

    @Test
    fun `allows zooming out to the complete 72 hour past and future range`() {
        val spec = chartViewportSpec(
            pointCount = 145,
            centerIndex = 72,
            initialVisibleHours = 24f,
            maximumVisibleHours = 144f,
        )

        assertThat(spec.maximumSpan).isEqualTo(144f)
        assertThat(spec.minimumZoom).isWithin(0.0001f).of(1f / 6f)
    }

    @Test
    fun `clamps a centered viewport at a short timeline boundary`() {
        val spec = chartViewportSpec(
            pointCount = 13,
            centerIndex = 1,
            initialVisibleHours = 24f,
            maximumVisibleHours = 144f,
        )

        assertThat(spec.initialSpan).isEqualTo(12f)
        assertThat(spec.maximumSpan).isEqualTo(12f)
        assertThat(spec.initialStart).isEqualTo(0f)
        assertThat(spec.minimumZoom).isEqualTo(1f)
    }
}
