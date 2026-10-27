package com.pescasub.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodComparisonTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.parse("2026-10-20")

    private fun session(date: String, minutes: Int?, vararg catches: CatchItem) = Session(
        startedAt = LocalDate.parse(date).atTime(10, 0).atZone(zone).toInstant(),
        zone = zone, activity = Activity.SPEARFISHING, durationMinutes = minutes, location = null,
        notes = "", conditions = null, apnea = null, catches = catches.toList(), photoPaths = emptyList()
    )

    private val fish = CatchItem(species = "Dorada", weightKg = 1.0, count = 2)

    @Test
    fun splitsSessionsIntoCurrentAndPreviousWindows() {
        val sessions = listOf(
            session("2026-10-20", 60, fish),          // hoy → actual
            session("2026-10-14", 30, fish),          // límite inferior del actual (30 días: > 09-20)
            session("2026-09-21", 45),                // primer día del actual
            session("2026-09-20", 90, fish, fish),    // último día del anterior
            session("2026-08-22", 20),                // primer día del anterior
            session("2026-08-21", 999, fish)          // fuera de ambos
        )
        val c = PeriodComparison.of(sessions, today, 30)
        assertEquals(PeriodTotals(sessions = 3, catches = 4, weightKg = 2.0, minutes = 135), c.current)
        assertEquals(PeriodTotals(sessions = 2, catches = 4, weightKg = 2.0, minutes = 110), c.previous)
    }

    @Test
    fun futureSessionsAreIgnored() {
        val c = PeriodComparison.of(listOf(session("2026-10-21", 60, fish)), today, 7)
        assertEquals(0, c.current.sessions)
        assertEquals(0, c.previous.sessions)
    }

    @Test
    fun emptyInputGivesZeros() {
        val c = PeriodComparison.of(emptyList(), today, 30)
        assertEquals(PeriodTotals(0, 0, 0.0, 0), c.current)
        assertEquals(PeriodTotals(0, 0, 0.0, 0), c.previous)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveWindowIsRejected() {
        PeriodComparison.of(emptyList(), today, 0)
    }
}
