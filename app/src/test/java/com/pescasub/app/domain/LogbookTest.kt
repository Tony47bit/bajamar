package com.pescasub.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogbookTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val cala = SavedLocation(id = 1, name = "Cala", coordinates = Coordinates(36.7, -4.4))
    private val roca = SavedLocation(id = 2, name = "Roca", coordinates = Coordinates(36.5, -4.9))

    private fun session(
        id: Long,
        date: String,
        activity: Activity = Activity.SPEARFISHING,
        location: SavedLocation? = cala,
        minutes: Int? = 120,
        catches: List<CatchItem> = emptyList(),
        apnea: ApneaStats? = null
    ) = Session(
        id = id,
        startedAt = LocalDate.parse(date).atTime(10, 0).atZone(zone).toInstant(),
        zone = zone, activity = activity, durationMinutes = minutes, location = location,
        notes = "", conditions = null, apnea = apnea, catches = catches, photoPaths = emptyList()
    )

    private val sessions = listOf(
        session(1, "2026-08-10", catches = listOf(CatchItem(species = "Dorada", weightKg = 1.2, count = 1))),
        session(
            2, "2026-08-25", location = roca, minutes = 90,
            catches = listOf(
                CatchItem(species = "dorada", weightKg = 0.8, count = 1),
                CatchItem(species = "Sargo", weightKg = 0.9, count = 3)
            ),
            apnea = ApneaStats(maxDepthM = 14.0, maxTimeSec = 95)
        ),
        session(3, "2026-09-02", activity = Activity.FREEDIVING, minutes = 60, apnea = ApneaStats(18.0, 110)),
        session(4, "2026-09-15", activity = Activity.SCUBA, location = null, minutes = null)
    )

    @Test
    fun filterByActivity() {
        val r = LogbookFilter(activity = Activity.FREEDIVING).apply(sessions)
        assertEquals(listOf(3L), r.map { it.id })
    }

    @Test
    fun filterByLocation() {
        assertEquals(listOf(2L), LogbookFilter(locationId = 2).apply(sessions).map { it.id })
    }

    @Test
    fun filterBySpeciesIsCaseInsensitive() {
        val r = LogbookFilter(species = "DORADA").apply(sessions)
        assertEquals(setOf(1L, 2L), r.map { it.id }.toSet())
    }

    @Test
    fun filterByDateRangeIsInclusive() {
        val r = LogbookFilter(from = LocalDate.parse("2026-08-25"), to = LocalDate.parse("2026-09-02")).apply(sessions)
        assertEquals(setOf(2L, 3L), r.map { it.id }.toSet())
    }

    @Test
    fun sortOrders() {
        assertEquals(listOf(4L, 3L, 2L, 1L), LogbookFilter().apply(sessions).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), LogbookFilter(sort = SortOrder.OLDEST).apply(sessions).map { it.id })
        assertEquals(2L, LogbookFilter(sort = SortOrder.MOST_CATCHES).apply(sessions).first().id)
        assertEquals(2L, LogbookFilter(sort = SortOrder.HEAVIEST).apply(sessions).first().id)
    }

    @Test
    fun combinedFiltersAreAnded() {
        val r = LogbookFilter(activity = Activity.SPEARFISHING, locationId = 2, species = "sargo").apply(sessions)
        assertEquals(listOf(2L), r.map { it.id })
        assertTrue(LogbookFilter(activity = Activity.SCUBA, species = "sargo").apply(sessions).isEmpty())
    }

    @Test
    fun statsAreComputedFromRealSessions() {
        val s = LogbookStats.from(sessions)
        assertEquals(4, s.sessions)
        assertEquals(270, s.totalMinutes) // 120 + 90 + 60 + (sin duración)
        assertEquals(5, s.totalCatches)
        assertEquals(2.9, s.totalWeightKg, 1e-9)
        assertEquals(18.0, s.bestApneaDepthM!!, 0.0) // máximo entre 14 m y 18 m
        assertEquals(110, s.bestApneaTimeSec)
    }

    @Test
    fun speciesAreGroupedIgnoringCaseAndSortedByCount() {
        val s = LogbookStats.from(sessions)
        assertEquals(listOf("Sargo", "Dorada"), s.bySpecies.map { it.species })
        assertEquals(3, s.bySpecies[0].count)
        val dorada = s.bySpecies[1]
        assertEquals(2, dorada.count)
        assertEquals(2.0, dorada.weightKg, 1e-9)
    }

    @Test
    fun sessionsAreGroupedByMonthInOrder() {
        val s = LogbookStats.from(sessions)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)), s.byMonth.map { it.month })
        assertEquals(listOf(2, 2), s.byMonth.map { it.sessions })
        assertEquals(listOf(5, 0), s.byMonth.map { it.catches })
    }

    @Test
    fun emptyLogbookGivesZeroStatsWithoutInventingData() {
        val s = LogbookStats.from(emptyList())
        assertEquals(0, s.sessions)
        assertEquals(0, s.totalCatches)
        assertTrue(s.bySpecies.isEmpty() && s.byMonth.isEmpty())
        assertNull(s.bestApneaDepthM)
        assertNull(s.bestApneaTimeSec)
    }

    @Test
    fun localDateUsesTheSessionZone() {
        // 23:30 UTC del día 1 son las 01:30 del día 2 en Madrid (verano).
        val s = session(9, "2026-08-02").copy(startedAt = Instant.parse("2026-08-01T23:30:00Z"))
        assertEquals(LocalDate.parse("2026-08-02"), s.localDate)
    }
}
