package com.pescasub.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataExporterTest {
    @Test
    fun escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"a\\\"b\"", DataExporter.str("a\"b"))
        assertEquals("\"a\\\\b\"", DataExporter.str("a\\b"))
        assertEquals("\"l1\\nl2\\t\"", DataExporter.str("l1\nl2\t"))
        assertEquals("\"\\u0001\"", DataExporter.str("\u0001"))
        assertEquals("\"ñandú 🐟\"", DataExporter.str("ñandú 🐟"))
    }

    @Test
    fun emptyDataExportsEmptyArrays() {
        val json = DataExporter.toJson(emptyList(), emptyList(), Instant.parse("2026-10-20T10:00:00Z"))
        assertTrue(json.contains("\"locations\": [],"))
        assertTrue(json.contains("\"sessions\": []"))
        assertTrue(json.contains("\"exportedAt\": \"2026-10-20T10:00:00Z\""))
    }

    @Test
    fun exportContainsUserDataAndNeverFilePaths() {
        val loc = SavedLocation(1, "Cala \"Honda\"", "Entrada\nnorte", Coordinates(36.72, -4.42), true, Instant.EPOCH)
        val session = Session(
            id = 5, startedAt = Instant.parse("2026-08-10T08:00:00Z"), zone = ZoneId.of("Europe/Madrid"),
            activity = Activity.SPEARFISHING, durationMinutes = 120, location = loc, notes = "Buen día",
            conditions = ConditionsSnapshot(24.0, 9.0, 0.4, 5.0, null, "Open-Meteo"),
            apnea = ApneaStats(15.5, null),
            catches = listOf(CatchItem(1, "Dorada", 1.2, 2)),
            photoPaths = listOf("/data/user/0/app/files/photos/secret-name.jpg")
        )
        val json = DataExporter.toJson(listOf(loc), listOf(session), Instant.EPOCH)
        assertTrue(json.contains("\"name\": \"Cala \\\"Honda\\\"\""))
        assertTrue(json.contains("\"latitude\": 36.72"))
        assertTrue(json.contains("\"species\": \"Dorada\""))
        assertTrue(json.contains("\"seaTempC\": null"))
        assertTrue(json.contains("\"maxTimeSec\": null"))
        assertTrue(json.contains("\"photoCount\": 1"))
        assertFalse(json.contains("secret-name"))
    }
}
