package com.pescasub.app.domain.visibility

import com.pescasub.app.domain.HourlyConditions
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Capas: observación → valor típico de la época → solo estado del mar → no estimable. */
class VisibilityResolverTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")

    private fun obs(kd: Double, ageHours: Long = 20) =
        WaterClarityObservation(kd, now.minus(Duration.ofHours(ageHours)), "Satélite")

    private fun hours(wave: Double?, rain: Double?) =
        (-48L..24L).map { HourlyConditions(time = now.plus(Duration.ofHours(it)), waveHeightM = wave, precipitationMm = rain) }

    private val clim = ClimatologyObservation(0.17, samples = 3, sourceName = "Climatología de prueba")

    private fun resolve(
        fetch: ClarityFetch,
        climatology: ClimatologyObservation? = clim,
        h: List<HourlyConditions> = hours(0.3, 0.0),
        onClimatology: () -> Unit = {}
    ) = runBlocking {
        VisibilityResolver.resolve(fetch, { onClimatology(); climatology }, emptyList(), h, now)
    }

    @Test
    fun aRecentObservationWinsAndClimatologyIsNeverRequested() {
        var asked = false
        val r = resolve(ClarityFetch(obs(0.17)), onClimatology = { asked = true })
        assertTrue(r is VisibilityEstimate.Estimated)
        assertFalse("no hace falta pedir la climatología", asked)
    }

    @Test
    fun withoutObservationTheTypicalValueIsUsedAndLabelledAsSuch() {
        val r = resolve(ClarityFetch(null)) as VisibilityEstimate.Climatological
        assertEquals(5, r.minMeters)
        assertEquals(15, r.maxMeters)
        assertEquals(3, r.samples)
        assertEquals(VisibilityConfidence.LOW, r.confidence)
        assertFalse(r.upperCapped)
    }

    @Test
    fun anOldObservationFallsBackToTheTypicalValueNotToTheOldOne() {
        val r = resolve(ClarityFetch(obs(1.0, ageHours = 24 * 9)))
        assertTrue(r is VisibilityEstimate.Climatological)
    }

    @Test
    fun anInvalidObservationFallsBackToo() {
        assertTrue(resolve(ClarityFetch(obs(Double.NaN))) is VisibilityEstimate.Climatological)
    }

    @Test
    fun roughSeaCapsTheTypicalValueToo() {
        val r = resolve(ClarityFetch(null), h = hours(2.0, 0.0)) as VisibilityEstimate.Climatological
        assertTrue(r.upperCapped)
        assertEquals(10, r.maxMeters)
        assertTrue(VisibilityFactor.ROUGH_SEA in r.factors)
    }

    @Test
    fun invalidClimatologyIsIgnored() {
        listOf(
            ClimatologyObservation(Double.NaN, 3, "x"),
            ClimatologyObservation(0.0, 3, "x"),
            ClimatologyObservation(9.0, 3, "x"),
            ClimatologyObservation(0.17, 0, "x")
        ).forEach { bad ->
            val r = resolve(ClarityFetch(null), climatology = bad)
            assertTrue("$bad -> $r", r is VisibilityEstimate.SeaStateOnly)
        }
    }

    @Test
    fun withoutAnyWaterDataCalmDrySeaGivesOnlyAQualitativeSignalWithoutMeters() {
        val r = resolve(ClarityFetch(null), climatology = null) as VisibilityEstimate.SeaStateOnly
        assertEquals(SeaOutlook.NO_WARNING_SIGNS, r.outlook)
    }

    @Test
    fun withoutAnyWaterDataRoughOrRainyIsFlaggedAsPossiblyWorse() {
        val rough = resolve(ClarityFetch(null), climatology = null, h = hours(2.0, 0.0))
        assertEquals(SeaOutlook.WORSE_THAN_USUAL, (rough as VisibilityEstimate.SeaStateOnly).outlook)
        val rainy = resolve(ClarityFetch(null), climatology = null, h = hours(0.3, 1.0))
        assertEquals(SeaOutlook.WORSE_THAN_USUAL, (rainy as VisibilityEstimate.SeaStateOnly).outlook)
    }

    @Test
    fun aSingleMissingDatasetIsReportedInTheFactors() {
        val r = resolve(ClarityFetch(null), climatology = null, h = hours(0.3, null)) as VisibilityEstimate.SeaStateOnly
        assertTrue(VisibilityFactor.RAIN_DATA_MISSING in r.factors)
    }

    @Test
    fun noDataAtAllIsNotEstimableAndNothingIsInvented() {
        val r = resolve(ClarityFetch(null), climatology = null, h = emptyList())
        assertEquals(NotEstimableReason.NO_OBSERVATION, (r as VisibilityEstimate.NotEstimable).reason)
        val noSea = resolve(ClarityFetch(null), climatology = null, h = hours(null, null))
        assertTrue(noSea is VisibilityEstimate.NotEstimable)
    }

    @Test
    fun failedRefreshWithAStoredObservationStaysOnTheObservationLayerMarkedAsStale() {
        val r = resolve(ClarityFetch(obs(0.17), refreshFailed = true)) as VisibilityEstimate.Estimated
        assertTrue(VisibilityFactor.REFRESH_FAILED in r.factors)
    }
}
