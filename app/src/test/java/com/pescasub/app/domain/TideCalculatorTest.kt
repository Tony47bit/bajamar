package com.pescasub.app.domain

import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TideCalculatorTest {
    private val start = Instant.parse("2026-10-20T00:00:00Z")

    /** Marea semidiurna sintética: periodo de 12,42 h, amplitud 0,5 m. */
    private fun synthetic(hours: Int, phaseHours: Double = 0.0, amplitude: Double = 0.5): List<Pair<Instant, Double>> {
        val period = 12.42
        return (0 until hours).map { h ->
            start.plusSeconds(h * 3600L) to amplitude * sin(2 * PI * (h - phaseHours) / period)
        }
    }

    @Test
    fun findsAlternatingHighAndLowTides() {
        val extremes = TideCalculator.extremes(synthetic(48))
        assertTrue("Se esperaban varias mareas en 48 h", extremes.size >= 6)
        extremes.zipWithNext().forEach { (a, b) -> assertTrue(a.type != b.type) }
    }

    @Test
    fun interpolationRecoversSubHourTimeAndHeight() {
        // El primer máximo teórico está a 12,42/4 = 3,105 h del origen y vale 0,5 m.
        val first = TideCalculator.extremes(synthetic(48)).first { it.type == TideType.HIGH }
        val expectedSeconds = (12.42 / 4 * 3600).toLong()
        val actualSeconds = first.time.epochSecond - start.epochSecond
        assertTrue("Error de tiempo < 10 min", Math.abs(actualSeconds - expectedSeconds) < 600)
        assertEquals(0.5, first.heightM, 0.01)
    }

    @Test
    fun highIsAboveLow() {
        val extremes = TideCalculator.extremes(synthetic(48))
        val highs = extremes.filter { it.type == TideType.HIGH }.map { it.heightM }
        val lows = extremes.filter { it.type == TideType.LOW }.map { it.heightM }
        assertTrue(highs.min() > lows.max())
    }

    @Test
    fun flatSeriesHasNoTides() {
        val flat = (0 until 24).map { start.plusSeconds(it * 3600L) to 0.2 }
        assertTrue(TideCalculator.extremes(flat).isEmpty())
    }

    @Test
    fun tinyWigglesBelowNoiseThresholdAreIgnored() {
        val noisy = (0 until 24).map { start.plusSeconds(it * 3600L) to if (it % 2 == 0) 0.200 else 0.204 }
        assertTrue(TideCalculator.extremes(noisy).isEmpty())
    }

    @Test
    fun shortOrIrregularSeriesDoesNotCrash() {
        assertTrue(TideCalculator.extremes(emptyList()).isEmpty())
        assertTrue(TideCalculator.extremes(listOf(start to 0.1, start.plusSeconds(3600) to 0.3)).isEmpty())
        val irregular = listOf(start to 0.0, start.plusSeconds(3600) to 0.5, start.plusSeconds(3600 + 5400) to 0.0)
        assertTrue(TideCalculator.extremes(irregular).isEmpty())
    }
}
