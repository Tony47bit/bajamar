package com.pescasub.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pescasub.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migración 1 → 2 con Room REAL: los datos del usuario se conservan íntegros y las columnas nuevas
 * (`observedVisibilityM`, `estimatedVisibilityM`) quedan en NULL. El esquema de la versión 1 se lee de app/schemas
 * (assets de androidTest). Se ejecuta en emulador/dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migration1To2KeepsAllUserDataAndAddsNullableColumns() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO locations (id, name, description, latitude, longitude, isFavorite, createdAt) VALUES (1, 'Cala', 'mi cala', 36.72, -4.42, 1, 1000)")
            execSQL(
                "INSERT INTO sessions (id, startedAt, zoneId, activity, durationMinutes, locationId, notes, snapAirTempC, snapWindKmh, " +
                    "snapWaveM, snapWavePeriodS, snapSeaTempC, snapSource, apneaMaxDepthM, apneaMaxTimeSec) " +
                    "VALUES (1, 2000, 'Europe/Madrid', 'SPEARFISHING', 90, 1, 'notas', 20.0, 10.0, 0.5, 5.0, 19.0, 'Open-Meteo', 12.5, 80)"
            )
            execSQL("INSERT INTO catches (id, sessionId, species, weightKg, count) VALUES (1, 1, 'Dorada', 1.2, 2)")
            execSQL("INSERT INTO photos (id, sessionId, filePath) VALUES (1, 1, '/p/a.jpg')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT name, description, latitude, longitude, isFavorite, createdAt FROM locations WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Cala", it.getString(0))
            assertEquals("mi cala", it.getString(1))
            assertEquals(36.72, it.getDouble(2), 1e-9)
            assertEquals(-4.42, it.getDouble(3), 1e-9)
            assertEquals(1, it.getInt(4))
            assertEquals(1000L, it.getLong(5))
        }
        db.query("SELECT notes, locationId, snapWaveM, apneaMaxDepthM, apneaMaxTimeSec, observedVisibilityM, estimatedVisibilityM FROM sessions WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("notas", it.getString(0))
            assertEquals(1L, it.getLong(1))
            assertEquals(0.5, it.getDouble(2), 1e-9)
            assertEquals(12.5, it.getDouble(3), 1e-9)
            assertEquals(80, it.getInt(4))
            assertTrue("visibilidad sin anotar", it.isNull(5))
            assertTrue("estimación sin emparejar", it.isNull(6))
        }
        db.query("SELECT COUNT(*) FROM catches").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM photos").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
