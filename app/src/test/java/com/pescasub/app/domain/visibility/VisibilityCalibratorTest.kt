package com.pescasub.app.domain.visibility

import com.pescasub.app.domain.HourlyConditions
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityCalibratorTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private fun s(obs: Int, est: Int, daysAgo: Long = 5) = CalibrationSample(obs, est, now.minus(Duration.ofDays(daysAgo)))

    @Test
    fun needsAtLeastThreeSamples() {
        assertNull(VisibilityCalibrator.calibrate(emptyList(), now))
        assertNull(VisibilityCalibrator.calibrate(listOf(s(4, 8), s(4, 8)), now))
        assertNotNull(VisibilityCalibrator.calibrate(listOf(s(4, 8), s(4, 8), s(4, 8)), now))
    }

    @Test
    fun factorIsTheMedianRatioObservedOverEstimated() {
        val c = VisibilityCalibrator.calibrate(listOf(s(4, 8), s(5, 10), s(3, 6)), now)!!
        assertEquals(0.5, c.factor, 1e-9)
        assertEquals(3, c.samples)
    }

    @Test
    fun oneStrangeDayDoesNotDistortTheMedian() {
        val c = VisibilityCalibrator.calibrate(listOf(s(4, 8), s(4, 8), s(4, 8), s(40, 8)), now)!!
        assertEquals(0.5, c.factor, 1e-9)
    }

    @Test
    fun factorIsClampedToASaneRange() {
        val low = VisibilityCalibrator.calibrate(listOf(s(1, 20), s(1, 20), s(1, 20)), now)!!
        assertEquals(VisibilityCalibrator.MIN_FACTOR, low.factor, 1e-9)
        val high = VisibilityCalibrator.calibrate(listOf(s(30, 4), s(30, 4), s(30, 4)), now)!!
        assertEquals(VisibilityCalibrator.MAX_FACTOR, high.factor, 1e-9)
    }

    @Test
    fun oldFutureAndInvalidSamplesAreIgnored() {
        val samples = listOf(
            s(4, 8, daysAgo = 400), s(4, 8, daysAgo = -10), // demasiado antigua / del futuro
            s(0, 8), s(61, 8), s(4, 0),                       // inválidas
            s(4, 8), s(4, 8)
        )
        assertNull(VisibilityCalibrator.calibrate(samples, now)) // solo 2 válidas
    }

    @Test
    fun calibrationScalesTheSatelliteEstimateUpOrDown() {
        val obs = WaterClarityObservation(0.2, now.minus(Duration.ofHours(10)), "Satélite de prueba") // Secchi 8,5 m
        val hours = (-48L..24L).map { HourlyConditions(time = now.plus(Duration.ofHours(it)), waveHeightM = 0.3, precipitationMm = 0.0) }
        val plain = VisibilityEstimator.estimate(obs, emptyList(), hours, now) as VisibilityEstimate.Estimated
        val lower = VisibilityEstimator.estimate(obs, emptyList(), hours, now, calibration = VisibilityCalibration(0.5, 4))
            as VisibilityEstimate.Estimated
        val higher = VisibilityEstimator.estimate(obs, emptyList(), hours, now, calibration = VisibilityCalibration(1.5, 4))
            as VisibilityEstimate.Estimated
        assertTrue(lower.maxMeters < plain.maxMeters && plain.maxMeters < higher.maxMeters)
        assertEquals(0, plain.calibratedSamples)
        assertEquals(4, lower.calibratedSamples)
        assertTrue("sigue siendo una banda estrecha", higher.maxMeters - higher.minMeters <= 5)
    }

    @Test
    fun calibrationAlsoScalesTheTypicalValueOfTheZone() = kotlinx.coroutines.runBlocking {
        val hours = (-48L..24L).map { HourlyConditions(time = now.plus(Duration.ofHours(it)), waveHeightM = 0.3, precipitationMm = 0.0) }
        val clim = ClimatologyObservation(0.2, samples = 3, sourceName = "Típica")
        val plain = VisibilityResolver.resolve(ClarityFetch(null), { clim }, emptyList(), hours, now) as VisibilityEstimate.Climatological
        val cal = VisibilityResolver.resolve(
            ClarityFetch(null), { clim }, emptyList(), hours, now, calibration = VisibilityCalibration(0.5, 3)
        ) as VisibilityEstimate.Climatological
        assertTrue(cal.maxMeters < plain.maxMeters)
        assertEquals(3, cal.calibratedSamples)
    }

    @Test
    fun withoutAnyWaterReferenceCalibrationInventsNothing() = kotlinx.coroutines.runBlocking {
        val r = VisibilityResolver.resolve(
            ClarityFetch(null), { null }, emptyList(), emptyList(), now, calibration = VisibilityCalibration(2.0, 9)
        )
        assertTrue(r is VisibilityEstimate.NotEstimable)
    }
}
