package com.pescasub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibilityTextTest {
    @Test
    fun gaugeScaleContainsTheEstimatedMaximum() {
        assertEquals(10, visibilityScaleMax(3))
        assertEquals(10, visibilityScaleMax(10))
        assertEquals(20, visibilityScaleMax(11))
        assertEquals(20, visibilityScaleMax(15))
        assertEquals(30, visibilityScaleMax(25))
        assertEquals(50, visibilityScaleMax(50))
        assertEquals(100, visibilityScaleMax(75))
        assertEquals(120, visibilityScaleMax(120)) // más allá de la escala: nunca se recorta el rango
    }

    @Test
    fun rangeIsShownInWholeMetersWithAnEnDash() {
        assertEquals("5–15 m", visibilityRangeShort(5, 15))
    }

    @Test
    fun seaOutlookNeverTalksInMeters() {
        com.pescasub.app.domain.visibility.SeaOutlook.values().forEach {
            assertEquals(false, seaOutlookText(it).contains(" m"))
        }
    }
}
