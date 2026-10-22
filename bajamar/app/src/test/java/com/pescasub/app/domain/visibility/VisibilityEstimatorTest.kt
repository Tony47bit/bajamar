package com.pescasub.app.domain.visibility

import com.pescasub.app.domain.HourlyConditions
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityEstimatorTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")

    private fun obs(kd: Double, ageHours: Long = 20) =
        WaterClarityObservation(kd, now.minus(Duration.ofHours(ageHours)), "Satélite de prueba")

    private fun hours(wave: Double?, rain: Double?, from: Long = -48, to: Long = 24) =
        (from..to).map { HourlyConditions(time = now.plus(Duration.ofHours(it)), waveHeightM = wave, precipitationMm = rain) }

    private fun est(r: VisibilityEstimate) = r as VisibilityEstimate.Estimated

    @Test
    fun completeRecentCalmDataGivesMediumConfidenceRange() {
        // Kd = 0,17 1/m -> Secchi = 10 m -> rango 5–15 m (0,5×–1,5×).
        val r = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), hours(0.3, 0.0), now))
        assertEquals(5, r.minMeters)
        assertEquals(15, r.maxMeters)
        assertEquals(VisibilityConfidence.MEDIUM, r.confidence)
        assertTrue(!r.upperCapped)
        assertTrue(VisibilityFactor.COARSE_SATELLITE in r.factors)
    }

    @Test
    fun confidenceHasNoHighLevel() {
        assertEquals(listOf("LOW", "MEDIUM"), VisibilityConfidence.values().map { it.name })
    }

    @Test
    fun roughSeaCapsUpperLimitAndLowersConfidence() {
        val r = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), hours(2.0, 0.0), now))
        assertTrue(r.upperCapped)
        assertEquals(10, r.maxMeters) // 1,0× Secchi en vez de 1,5×
        assertEquals(VisibilityConfidence.LOW, r.confidence)
        assertTrue(VisibilityFactor.ROUGH_SEA in r.factors)
    }

    @Test
    fun heavyRainCapsAndNeverImprovesTheEstimate() {
        val calm = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), hours(0.3, 0.0), now))
        val rainy = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), hours(0.3, 1.0), now)) // 49 mm/48 h
        assertTrue(VisibilityFactor.RECENT_RAIN in rainy.factors)
        assertTrue(rainy.maxMeters <= calm.maxMeters)
        assertEquals(calm.minMeters, rainy.minMeters)
    }

    @Test
    fun missingWaveOrRainDataLowersConfidenceAndIsReported() {
        val noWaves = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), hours(null, 0.0), now))
        assertEquals(VisibilityConfidence.LOW, noWaves.confidence)
        assertTrue(VisibilityFactor.WAVE_DATA_MISSING in noWaves.factors)
        val noRain = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), hours(0.3, null), now))
        assertEquals(VisibilityConfidence.LOW, noRain.confidence)
        assertTrue(VisibilityFactor.RAIN_DATA_MISSING in noRain.factors)
    }

    @Test
    fun noForecastDataAtAllStillEstimatesButWithLowConfidence() {
        val r = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), emptyList(), now))
        assertEquals(VisibilityConfidence.LOW, r.confidence)
        assertTrue(VisibilityFactor.WAVE_DATA_MISSING in r.factors && VisibilityFactor.RAIN_DATA_MISSING in r.factors)
    }

    @Test
    fun agedObservationLowersConfidenceAndIsFlagged() {
        val r = est(VisibilityEstimator.estimate(obs(0.17, ageHours = 24 * 4), emptyList(), hours(0.3, 0.0), now))
        assertEquals(VisibilityConfidence.LOW, r.confidence)
        assertTrue(VisibilityFactor.OBSERVATION_AGED in r.factors)
    }

    @Test
    fun failedRefreshIsMarkedAndNeverPresentedAsCurrent() {
        val r = est(VisibilityEstimator.estimate(obs(0.17), emptyList(), hours(0.3, 0.0), now, refreshFailed = true))
        assertEquals(VisibilityConfidence.LOW, r.confidence)
        assertTrue(VisibilityFactor.REFRESH_FAILED in r.factors)
        assertEquals(now.minus(Duration.ofHours(20)), r.observedAt)
    }

    @Test
    fun noObservationIsNotEstimable() {
        val r = VisibilityEstimator.estimate(null, emptyList(), hours(0.3, 0.0), now) as VisibilityEstimate.NotEstimable
        assertEquals(NotEstimableReason.NO_OBSERVATION, r.reason)
    }

    @Test
    fun tooOldObservationIsNotReusedSilently() {
        val r = VisibilityEstimator.estimate(obs(0.17, ageHours = 24 * 8), emptyList(), hours(0.3, 0.0), now)
        assertEquals(NotEstimableReason.OBSERVATION_TOO_OLD, (r as VisibilityEstimate.NotEstimable).reason)
    }

    @Test
    fun invalidValuesAreRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 0.005, 6.5).forEach { kd ->
            val r = VisibilityEstimator.estimate(obs(kd), emptyList(), hours(0.3, 0.0), now)
            assertEquals("kd=$kd", NotEstimableReason.INVALID_OBSERVATION, (r as VisibilityEstimate.NotEstimable).reason)
        }
        val future = WaterClarityObservation(0.17, now.plus(Duration.ofDays(3)), "x")
        val r = VisibilityEstimator.estimate(future, emptyList(), hours(0.3, 0.0), now)
        assertEquals(NotEstimableReason.INVALID_OBSERVATION, (r as VisibilityEstimate.NotEstimable).reason)
    }

    @Test
    fun turbidWaterGivesShortRangeWithoutFalsePrecision() {
        // Kd = 1,0 -> Secchi 1,7 m -> 0,85–2,55 m: se redondea a 1–3 m, nunca decimales.
        val r = est(VisibilityEstimator.estimate(obs(1.0), emptyList(), hours(0.3, 0.0), now))
        assertEquals(1, r.minMeters)
        assertEquals(3, r.maxMeters)
    }

    @Test
    fun clearWaterRoundsToMultiplesOfFiveAboveTenMeters() {
        // Kd = 0,05 -> Secchi 34 m -> 17–51 m -> 15–50 m.
        val r = est(VisibilityEstimator.estimate(obs(0.05), emptyList(), hours(0.3, 0.0), now))
        assertEquals(0, r.minMeters % 5)
        assertEquals(0, r.maxMeters % 5)
        assertTrue(r.maxMeters > r.minMeters)
    }

    @Test
    fun rangeIsAlwaysAtLeastOneMeterWideAndPositive() {
        listOf(0.01, 0.3, 0.9, 2.0, 5.9).forEach { kd ->
            val r = est(VisibilityEstimator.estimate(obs(kd), emptyList(), hours(0.3, 0.0), now))
            assertTrue("kd=$kd", r.minMeters >= 1 && r.maxMeters > r.minMeters)
        }
    }

    @Test
    fun waveHoursBeforeTheObservationDoNotAffectTheResult() {
        // Mar agitado hace 40 h, anterior a la observación (hace 20 h): no debe recortar.
        val rough = (-48L..-30L).map { HourlyConditions(time = now.plus(Duration.ofHours(it)), waveHeightM = 3.0, precipitationMm = 0.0) }
        val calm = (-29L..24L).map { HourlyConditions(time = now.plus(Duration.ofHours(it)), waveHeightM = 0.3, precipitationMm = 0.0) }
        val r = est(VisibilityEstimator.estimate(obs(0.17), rough, calm, now))
        assertTrue(!r.upperCapped)
    }
}
