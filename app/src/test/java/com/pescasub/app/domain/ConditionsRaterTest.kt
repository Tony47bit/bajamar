package com.pescasub.app.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionsRaterTest {
    private fun hour(wave: Double?, wind: Double?, gusts: Double? = null) = HourlyConditions(
        time = Instant.EPOCH, waveHeightM = wave, windSpeedKmh = wind, windGustsKmh = gusts
    )

    @Test
    fun calmSeaAndLightWindIsGood() {
        val a = ConditionsRater.assess(hour(wave = 0.3, wind = 10.0))
        assertEquals(Rating.GOOD, a.rating)
        assertTrue(RatingFactor.WAVES_CALM in a.factors && RatingFactor.WIND_CALM in a.factors)
    }

    @Test
    fun moderateWavesAreFair() {
        assertEquals(Rating.FAIR, ConditionsRater.assess(hour(wave = 0.8, wind = 10.0)).rating)
    }

    @Test
    fun highWavesArePoor() {
        assertEquals(Rating.POOR, ConditionsRater.assess(hour(wave = 1.6, wind = 5.0)).rating)
    }

    @Test
    fun strongWindIsPoor() {
        assertEquals(Rating.POOR, ConditionsRater.assess(hour(wave = 0.2, wind = 30.0)).rating)
    }

    @Test
    fun strongGustsDowngradeToPoorEvenWithLowAverageWind() {
        val a = ConditionsRater.assess(hour(wave = 0.2, wind = 10.0, gusts = 45.0))
        assertEquals(Rating.POOR, a.rating)
        assertTrue(RatingFactor.GUSTS_STRONG in a.factors)
    }

    @Test
    fun thresholdsAreInclusive() {
        assertEquals(Rating.GOOD, ConditionsRater.assess(hour(wave = 0.5, wind = 15.0)).rating)
        assertEquals(Rating.FAIR, ConditionsRater.assess(hour(wave = 1.0, wind = 25.0)).rating)
    }

    @Test
    fun noDataAtAllIsUnknown() {
        val a = ConditionsRater.assess(hour(wave = null, wind = null))
        assertEquals(Rating.UNKNOWN, a.rating)
        assertTrue(RatingFactor.WAVES_MISSING in a.factors && RatingFactor.WIND_MISSING in a.factors)
    }

    @Test
    fun missingWaveDataNeverRatesGood() {
        val a = ConditionsRater.assess(hour(wave = null, wind = 5.0))
        assertEquals(Rating.FAIR, a.rating)
        assertTrue(RatingFactor.WAVES_MISSING in a.factors)
    }

    @Test
    fun missingWindDataNeverRatesGoodButStillReportsPoorWaves() {
        assertEquals(Rating.FAIR, ConditionsRater.assess(hour(wave = 0.2, wind = null)).rating)
        assertEquals(Rating.POOR, ConditionsRater.assess(hour(wave = 2.0, wind = null)).rating)
    }
}
