package com.pescasub.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pescasub.app.data.local.AppDatabase
import com.pescasub.app.data.local.ForecastCacheEntity
import com.pescasub.app.data.local.RoomLocationRepository
import com.pescasub.app.data.local.RoomLogbookRepository
import com.pescasub.app.domain.Activity
import com.pescasub.app.domain.ApneaStats
import com.pescasub.app.domain.CatchItem
import com.pescasub.app.domain.ConditionsSnapshot
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.PhotoStore
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.Session
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de persistencia con Room REAL en un emulador/dispositivo (Room no se puede probar fielmente en JVM).
 * Se ejecutan con `./gradlew connectedDebugAndroidTest` o con el workflow de CI (disparo manual).
 */
@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private class RecordingPhotoStore : PhotoStore {
        val deleted = mutableListOf<String>()
        override suspend fun import(uri: String): String? = uri
        override fun delete(path: String) {
            deleted += path
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var photos: RecordingPhotoStore
    private lateinit var logbook: RoomLogbookRepository
    private lateinit var locations: RoomLocationRepository

    private val zone = ZoneId.of("Europe/Madrid")
    private val started = Instant.parse("2026-10-20T08:30:00Z")

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        photos = RecordingPhotoStore()
        logbook = RoomLogbookRepository(db, photos)
        locations = RoomLocationRepository(db.locationDao())
    }

    @After
    fun close() {
        db.close()
    }

    private fun newSession(
        location: SavedLocation? = null,
        catches: List<CatchItem> = emptyList(),
        photoPaths: List<String> = emptyList()
    ) = Session(
        startedAt = started, zone = zone, activity = Activity.FREEDIVING, durationMinutes = 95,
        location = location, notes = "Notas ñ 🐟",
        conditions = ConditionsSnapshot(21.0, 12.0, 0.6, 5.0, 20.5, "Open-Meteo"),
        apnea = ApneaStats(14.5, 88), catches = catches, photoPaths = photoPaths
    )

    private suspend fun saveLocation(name: String = "Cala"): SavedLocation {
        val id = locations.save(SavedLocation(name = name, coordinates = Coordinates(36.7213, -4.4214)))
        return locations.get(id)!!
    }

    @Test
    fun aSavedSessionRoundTripsWithAllItsData() = runBlocking {
        val cala = saveLocation()
        val id = logbook.save(
            newSession(
                location = cala,
                catches = listOf(
                    CatchItem(species = "Dorada", weightKg = 1.2, count = 2),
                    CatchItem(species = "Sargo", weightKg = null, count = 1)
                ),
                photoPaths = listOf("/p/a.jpg", "/p/b.jpg")
            )
        )
        val s = logbook.get(id)!!
        assertEquals(started, s.startedAt)
        assertEquals(zone, s.zone)
        assertEquals(Activity.FREEDIVING, s.activity)
        assertEquals(95, s.durationMinutes)
        assertEquals("Notas ñ 🐟", s.notes)
        assertEquals(cala.id, s.location?.id)
        assertEquals(Coordinates(36.7213, -4.4214), s.location?.coordinates) // coordenadas exactas en local
        assertEquals("Open-Meteo", s.conditions?.source)
        assertEquals(0.6, s.conditions?.waveM!!, 0.0)
        assertEquals(ApneaStats(14.5, 88), s.apnea)
        assertEquals(listOf("Dorada", "Sargo"), s.catches.map { it.species })
        assertEquals(listOf(1.2, null), s.catches.map { it.weightKg })
        assertEquals(listOf("/p/a.jpg", "/p/b.jpg"), s.photoPaths)
    }

    @Test
    fun catchesStayWithTheirOwnSession() = runBlocking {
        val a = logbook.save(newSession(catches = listOf(CatchItem(species = "Dorada", weightKg = 1.0, count = 1))))
        val b = logbook.save(newSession(catches = listOf(CatchItem(species = "Lubina", weightKg = 2.0, count = 1))))
        assertEquals(listOf("Dorada"), logbook.get(a)!!.catches.map { it.species })
        assertEquals(listOf("Lubina"), logbook.get(b)!!.catches.map { it.species })
    }

    @Test
    fun editingReplacesCatchesInPlaceAndDeletesRemovedPhotoFilesAfterCommit() = runBlocking {
        val id = logbook.save(
            newSession(
                catches = listOf(CatchItem(species = "Sargo", weightKg = 0.5, count = 1)),
                photoPaths = listOf("/p/a.jpg", "/p/b.jpg")
            )
        )
        val original = logbook.get(id)!!
        logbook.save(
            original.copy(
                notes = "editada",
                catches = listOf(CatchItem(species = "Dorada", weightKg = 1.1, count = 3)),
                photoPaths = listOf("/p/b.jpg")
            )
        )
        val edited = logbook.get(id)!!
        assertEquals(1, logbook.observeAll().first().size) // se actualizó, no se duplicó
        assertEquals("editada", edited.notes)
        assertEquals(listOf("Dorada"), edited.catches.map { it.species })
        assertEquals(listOf("/p/b.jpg"), edited.photoPaths)
        assertEquals(listOf("/p/a.jpg"), photos.deleted)
    }

    @Test
    fun deletingASessionCascadesToCatchesAndPhotosAndDeletesTheFiles() = runBlocking {
        val id = logbook.save(
            newSession(
                catches = listOf(CatchItem(species = "Sargo", weightKg = 0.5, count = 1)),
                photoPaths = listOf("/p/a.jpg")
            )
        )
        logbook.delete(id)
        assertNull(logbook.get(id))
        assertEquals(0, count("catches"))
        assertEquals(0, count("photos"))
        assertEquals(listOf("/p/a.jpg"), photos.deleted)
    }

    @Test
    fun deletingALocationKeepsTheSessionsThatUsedIt() = runBlocking {
        val cala = saveLocation()
        val id = logbook.save(newSession(location = cala))
        locations.delete(cala.id)
        val s = logbook.get(id)
        assertNotNull(s)
        assertNull(s!!.location)
    }

    @Test
    fun savedLocationsCanBeEditedAndListed() = runBlocking {
        val cala = saveLocation("Cala")
        locations.save(cala.copy(name = "Cala Honda", description = "Entrada norte"))
        val all = locations.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Cala Honda", all.single().name)
        assertEquals("Entrada norte", all.single().description)
        assertEquals(cala.createdAt.toEpochMilli(), all.single().createdAt.toEpochMilli())
    }

    @Test
    fun savingAnUnknownExistingSessionFailsInsteadOfCreatingGhostRows() {
        var failed = false
        try {
            runBlocking { logbook.save(newSession().copy(id = 999)) }
        } catch (e: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(0, count("sessions"))
        assertEquals(0, count("catches"))
    }

    @Test
    fun forecastCacheUpsertReplacesTheEntry() = runBlocking {
        val dao = db.forecastCacheDao()
        dao.upsert(ForecastCacheEntity("k", "uno", 1L))
        dao.upsert(ForecastCacheEntity("k", "dos", 2L))
        assertEquals("dos", dao.get("k")!!.payload)
        dao.clear()
        assertNull(dao.get("k"))
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabaseFile() = runBlocking {
        val name = "persistencia-test.db"
        context.deleteDatabase(name)
        var file = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val id = RoomLogbookRepository(file, photos).save(
            newSession(catches = listOf(CatchItem(species = "Dorada", weightKg = 1.0, count = 1)))
        )
        file.close() // "cerrar la app"

        file = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val s = RoomLogbookRepository(file, photos).get(id)
            assertNotNull(s)
            assertEquals(listOf("Dorada"), s!!.catches.map { it.species })
        } finally {
            file.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun clearAllTablesRemovesEverythingPersonal() = runBlocking {
        val cala = saveLocation()
        logbook.save(newSession(location = cala, catches = listOf(CatchItem(species = "Dorada", weightKg = 1.0, count = 1))))
        db.forecastCacheDao().upsert(ForecastCacheEntity("k", "x", 1L))
        db.clearAllTables()
        assertEquals(0, count("sessions") + count("catches") + count("photos") + count("locations") + count("forecast_cache"))
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun observedVisibilityAndItsEstimateSurviveRoundTripAndReopen() = runBlocking {
        val id = logbook.save(newSession().copy(observedVisibilityM = 5, estimatedVisibilityM = 8))
        val stored = logbook.get(id)!!
        assertEquals(5, stored.observedVisibilityM)
        assertEquals(8, stored.estimatedVisibilityM)

        val noValue = logbook.get(logbook.save(newSession()))!!
        assertNull(noValue.observedVisibilityM)
        assertNull(noValue.estimatedVisibilityM)
    }
}
