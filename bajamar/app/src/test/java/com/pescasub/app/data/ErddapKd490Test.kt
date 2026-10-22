package com.pescasub.app.data

import com.pescasub.app.data.remote.ErddapClarityRepository
import com.pescasub.app.data.remote.ErddapKd490Parser
import com.pescasub.app.domain.Coordinates
import java.time.Instant
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lector de la respuesta JSON de ERDDAP. Los JSON de ejemplo reproducen el formato documentado de ERDDAP (tabla). */
class ErddapKd490Test {
    private fun body(vararg rows: String) =
        """{"table":{"columnNames":["time","altitude","latitude","longitude","kd_490"],""" +
            """"columnTypes":["String","double","double","double","float"],""" +
            """"columnUnits":["UTC","m","degrees_north","degrees_east","m^-1"],"rows":[${rows.joinToString(",")}]}}"""

    @Test
    fun picksTheMostRecentValidObservationSkippingCloudGaps() {
        val json = body(
            """["2026-10-14T12:00:00Z",0.0,36.7,-4.4,0.21]""",
            """["2026-10-15T12:00:00Z",0.0,36.7,-4.4,0.18]""",
            """["2026-10-16T12:00:00Z",0.0,36.7,-4.4,null]""", // nubes
            """["2026-10-17T12:00:00Z",0.0,36.7,-4.4,null]"""
        )
        val obs = ErddapKd490Parser.latest(json)!!
        assertEquals(0.18, obs.kd490PerM, 1e-9)
        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), obs.observedAt)
    }

    @Test
    fun allGapsMeansNoObservation() {
        val json = body("""["2026-10-14T12:00:00Z",0.0,36.7,-4.4,null]""")
        assertNull(ErddapKd490Parser.latest(json))
    }

    @Test
    fun emptyRowsMeansNoObservation() {
        assertNull(ErddapKd490Parser.latest(body()))
    }

    @Test
    fun malformedResponsesAreRejectedNotGuessed() {
        listOf("no es json", "{}", """{"table":{"columnNames":["a"],"rows":[]}}""", """{"table":{"rows":[]}}""")
            .forEach { bad ->
                try {
                    ErddapKd490Parser.latest(bad)
                    throw AssertionError("debía fallar: $bad")
                } catch (e: IllegalArgumentException) {
                    assertNotNull(e.message)
                }
            }
    }

    @Test
    fun requestAsksOnlyForTheLastSevenDaysOfTheNearestPixelAndUsesNoKey() {
        val repo = ErddapClarityRepository(OkHttpClient())
        val url = repo.buildUrl(Coordinates(36.72, -4.42))
        assertTrue(url.startsWith("https://coastwatch.pfeg.noaa.gov/erddap/griddap/nesdisVHNSQkd490Daily.json?"))
        assertTrue(url.contains("kd_490[last-6:1:last][(0.0)][(36.7200):1:(36.7200)][(-4.4200):1:(-4.4200)]"))
        assertTrue(!url.contains("key", ignoreCase = true))
    }
}
