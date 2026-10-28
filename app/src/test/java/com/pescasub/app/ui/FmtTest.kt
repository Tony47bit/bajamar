package com.pescasub.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {
    @Test
    fun compassPointsAndWraparound() {
        assertEquals("N", Fmt.compass(0.0))
        assertEquals("N", Fmt.compass(360.0))
        assertEquals("N", Fmt.compass(355.0))
        assertEquals("NE", Fmt.compass(45.0))
        assertEquals("E", Fmt.compass(90.0))
        assertEquals("S", Fmt.compass(180.0))
        assertEquals("O", Fmt.compass(270.0))
        assertEquals("NNO", Fmt.compass(337.5))
        assertEquals("N", Fmt.compass(-5.0))
    }

    @Test
    fun missingValuesShowDashNeverZero() {
        assertEquals("—", Fmt.compass(null))
        assertEquals("—", Fmt.temp(null))
        assertEquals("—", Fmt.height(null))
        assertEquals("—", Fmt.number(Double.NaN))
        assertEquals("—", Fmt.duration(null))
    }

    @Test
    fun numbersUseSpanishDecimalComma() {
        assertEquals("1,5 m", Fmt.height(1.5))
        assertEquals("21 °C", Fmt.temp(21.4))
        assertEquals("12 km/h", Fmt.speed(12.3))
        assertEquals("2,35 kg", Fmt.kg(2.35))
        assertEquals("0,5", Fmt.num(0.5))
        assertEquals("15", Fmt.num(15.0, 0))
        assertEquals("—", Fmt.num(null))
    }

    @Test
    fun durationsAndSeconds() {
        assertEquals("45 min", Fmt.duration(45))
        assertEquals("2 h", Fmt.duration(120))
        assertEquals("2 h 5 min", Fmt.duration(125))
        assertEquals("1:35 min", Fmt.seconds(95))
        assertEquals("0:05 min", Fmt.seconds(5))
    }

    @Test
    fun timesUseTheGivenZone() {
        val t = Instant.parse("2026-10-20T08:30:00Z")
        assertEquals("10:30", Fmt.time(t, ZoneId.of("Europe/Madrid")))
        assertEquals("08:30", Fmt.time(t, ZoneId.of("UTC")))
        assertEquals("10h", Fmt.hour(t, ZoneId.of("Europe/Madrid")))
    }

    @Test
    fun monthLabelIsShortSpanishWithTwoDigitYear() {
        val label = Fmt.month(YearMonth.of(2026, 10))
        assertEquals(true, label.startsWith("oct") && label.endsWith("26"))
    }

    @Test
    fun daysAndCoordinates() {
        assertEquals("Mar 20", Fmt.dayShort(LocalDate.parse("2026-10-20")))
        assertEquals("Martes 20 de octubre", Fmt.dayLong(LocalDate.parse("2026-10-20")))
        assertEquals("36.72130, -4.42140", Fmt.coordinates(36.7213, -4.4214))
    }
}
