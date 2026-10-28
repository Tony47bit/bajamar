package com.pescasub.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ventana de previsión: solo hoy y mañana, en el día LOCAL de la ubicación (no UTC ni el del móvil). */
class ForecastWindowTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val lima = ZoneId.of("America/Lima")

    private fun hoursFrom(start: Instant, count: Int) =
        (0 until count).map { HourlyConditions(time = start.plusSeconds(it * 3600L), windSpeedKmh = it.toDouble()) }

    private fun bundle(zone: ZoneId, hours: List<HourlyConditions>, tides: List<TideExtreme> = emptyList()) =
        ForecastBundle(
            coordinates = Coordinates(36.72, -4.42), zone = zone, fetchedAt = Instant.parse("2026-10-20T08:00:00Z"),
            sourceName = "Prueba", hourly = hours, tides = tides, hasMarineData = false, notes = emptyList()
        )

    @Test
    fun keepsOnlyTodayAndTomorrowAndNeverLaterDays() {
        // 7 días de datos desde el 20/10 a las 00:00 locales (22:00Z del día anterior, CEST = UTC+2).
        val start = Instant.parse("2026-10-19T22:00:00Z")
        val now = Instant.parse("2026-10-20T09:00:00Z")
        val limited = bundle(madrid, hoursFrom(start, 24 * 7)).limitedToWindow(now)

        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)), limited.dates())
        assertEquals(48, limited.hourly.size)
        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)), limited.daily().map { it.date })
    }

    @Test
    fun pastDaysAreMovedOutOfTheVisibleDataButKeptAsContext() {
        val start = Instant.parse("2026-10-17T22:00:00Z") // 18/10 00:00 locales
        val now = Instant.parse("2026-10-20T09:00:00Z")
        val limited = bundle(madrid, hoursFrom(start, 24 * 6)).limitedToWindow(now)

        assertTrue(limited.hourly.all { limited.localDate(it.time) >= LocalDate.of(2026, 10, 20) })
        assertTrue(limited.recent.isNotEmpty())
        assertTrue(limited.recent.all { it.time < Instant.parse("2026-10-19T22:00:00Z") })
    }

    @Test
    fun localDayDiffersFromUtcDay() {
        // 20/10 23:30Z ya es 21/10 en Madrid (+2) y en Tokio (+9), pero sigue siendo 20/10 en Lima (-5).
        val now = Instant.parse("2026-10-20T23:30:00Z")
        assertEquals(LocalDate.of(2026, 10, 21), bundle(madrid, emptyList()).windowDates(now).first())
        assertEquals(LocalDate.of(2026, 10, 21), bundle(tokyo, emptyList()).windowDates(now).first())
        assertEquals(LocalDate.of(2026, 10, 20), bundle(lima, emptyList()).windowDates(now).first())
    }

    @Test
    fun dayChangesAtLocalMidnightNotUtcMidnight() {
        val start = Instant.parse("2026-10-19T22:00:00Z")
        val b = bundle(madrid, hoursFrom(start, 24 * 3))
        val before = Instant.parse("2026-10-20T21:59:00Z") // 23:59 locales del 20
        val after = Instant.parse("2026-10-20T22:00:00Z")  // 00:00 locales del 21
        assertEquals(LocalDate.of(2026, 10, 20), b.windowDates(before).first())
        assertEquals(LocalDate.of(2026, 10, 21), b.windowDates(after).first())
        // Pasada la medianoche, "hoy" ya es el 21 y el 20 ha salido de los datos visibles.
        assertFalse(b.limitedToWindow(after).dates().contains(LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun dstEndDayHas25HoursAndTomorrowIsStillComplete() {
        // En Madrid el 25/10/2026 se retrasa el reloj: ese día local tiene 25 horas.
        val start = Instant.parse("2026-10-24T22:00:00Z") // 25/10 00:00 CEST
        val now = Instant.parse("2026-10-25T10:00:00Z")
        val limited = bundle(madrid, hoursFrom(start, 24 * 4)).limitedToWindow(now)
        assertEquals(25, limited.hoursOf(LocalDate.of(2026, 10, 25)).size)
        assertEquals(24, limited.hoursOf(LocalDate.of(2026, 10, 26)).size)
        assertEquals(listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)), limited.dates())
    }

    @Test
    fun dstStartDayHas23Hours() {
        val start = Instant.parse("2026-03-28T23:00:00Z") // 29/03 00:00 CET
        val now = Instant.parse("2026-03-29T08:00:00Z")
        val limited = bundle(madrid, hoursFrom(start, 24 * 4)).limitedToWindow(now)
        assertEquals(23, limited.hoursOf(LocalDate.of(2026, 3, 29)).size)
        assertEquals(24, limited.hoursOf(LocalDate.of(2026, 3, 30)).size)
    }

    @Test
    fun tidesAreAlsoLimitedToTheWindow() {
        val now = Instant.parse("2026-10-20T09:00:00Z")
        val tides = listOf(
            TideExtreme(Instant.parse("2026-10-19T10:00:00Z"), TideType.HIGH, 0.5),
            TideExtreme(Instant.parse("2026-10-20T10:00:00Z"), TideType.LOW, -0.5),
            TideExtreme(Instant.parse("2026-10-21T10:00:00Z"), TideType.HIGH, 0.5),
            TideExtreme(Instant.parse("2026-10-22T10:00:00Z"), TideType.LOW, -0.5)
        )
        val limited = bundle(madrid, hoursFrom(Instant.parse("2026-10-19T22:00:00Z"), 72), tides).limitedToWindow(now)
        assertEquals(2, limited.tides.size)
        assertTrue(limited.tides.all { limited.localDate(it.time) in limited.windowDates(now) })
    }

    @Test
    fun coversTodayDetectsStaleDays() {
        val b = bundle(madrid, hoursFrom(Instant.parse("2026-10-19T22:00:00Z"), 48))
        assertTrue(b.coversToday(Instant.parse("2026-10-20T10:00:00Z")))
        assertTrue(b.coversToday(Instant.parse("2026-10-21T10:00:00Z")))
        assertFalse(b.coversToday(Instant.parse("2026-10-22T10:00:00Z")))
    }

    @Test
    fun missingHoursLeaveTheDayEmptyInsteadOfInventingData() {
        val b = bundle(madrid, hoursFrom(Instant.parse("2026-10-19T22:00:00Z"), 24)) // solo el 20
        val limited = b.limitedToWindow(Instant.parse("2026-10-20T09:00:00Z"))
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), limited.dates())
        assertTrue(limited.hoursOf(LocalDate.of(2026, 10, 21)).isEmpty())
    }
}
