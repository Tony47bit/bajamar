package com.pescasub.app.ui

import com.pescasub.app.domain.Activity
import com.pescasub.app.domain.ApneaStats
import com.pescasub.app.domain.CatchItem
import com.pescasub.app.domain.ForecastError
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.HourlyConditions
import com.pescasub.app.testing.FakeLocationRepository
import com.pescasub.app.testing.FakeLogbookRepository
import com.pescasub.app.testing.FakePhotoStore
import com.pescasub.app.testing.FakeWeatherRepository
import com.pescasub.app.testing.MADRID
import com.pescasub.app.testing.bundle
import com.pescasub.app.testing.location
import com.pescasub.app.testing.session
import com.pescasub.app.ui.logbook.EditSessionViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditSessionViewModelTest {
    // 2026-10-20 08:30 UTC = 10:30 en Madrid (horario de verano).
    private val clock = Clock.fixed(Instant.parse("2026-10-20T08:30:00Z"), MADRID)

    private lateinit var logbook: FakeLogbookRepository
    private lateinit var locations: FakeLocationRepository
    private lateinit var weather: FakeWeatherRepository
    private lateinit var photos: FakePhotoStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        logbook = FakeLogbookRepository()
        locations = FakeLocationRepository()
        weather = FakeWeatherRepository(ForecastResult.Failed(ForecastError.NETWORK))
        photos = FakePhotoStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(id: Long = 0) = EditSessionViewModel(id, logbook, locations, weather, photos, clock)

    private fun EditSessionViewModel.addCatchRow(species: String, weight: String, count: String): Long {
        addCatch()
        val key = state.value.catches.last().key
        updateCatch(key, species = species, weight = weight, count = count)
        return key
    }

    // ---- Crear ---------------------------------------------------------------------------------

    @Test
    fun newSessionStartsNowInLocalZone() {
        val vm = vm()
        assertTrue(vm.isNew)
        assertFalse(vm.state.value.loading)
        assertEquals(clock.instant(), vm.state.value.startedAt.toInstant())
        assertEquals(MADRID, vm.state.value.startedAt.zone)
    }

    @Test
    fun savingANewSessionStoresItWithItsCatchesAndNormalizedSpecies() {
        locations.seed(location(name = "Cala Honda"))
        val vm = vm()
        vm.setActivity(Activity.SPEARFISHING)
        vm.setLocation(locations.all.single().id)
        vm.setDuration("90")
        vm.setNotes("  Buena visibilidad  ")
        vm.addCatchRow("  Dorada   real ", "1,2", "2")
        vm.addCatch() // fila vacía: debe ignorarse
        vm.save()

        assertTrue(vm.state.value.saved)
        val stored = logbook.all.single()
        assertTrue(stored.id > 0)
        assertEquals(90, stored.durationMinutes)
        assertEquals("Buena visibilidad", stored.notes)
        assertEquals("Cala Honda", stored.location?.name)
        assertEquals(1, stored.catches.size)
        assertEquals("Dorada real", stored.catches.single().species)
        assertEquals(1.2, stored.catches.single().weightKg!!, 1e-9)
        assertEquals(2, stored.catches.single().count)
        assertEquals(clock.instant(), stored.startedAt)
        assertEquals(MADRID, stored.zone)
    }

    @Test
    fun invalidDurationBlocksSavingAndReportsTheFieldError() {
        val vm = vm()
        vm.setDuration("abc")
        vm.save()
        assertFalse(vm.state.value.saved)
        assertNotNull(vm.state.value.errors["duration"])
        assertEquals(0, logbook.saveCalls)
        // al corregir el campo desaparece su error
        vm.setDuration("45")
        assertNull(vm.state.value.errors["duration"])
    }

    @Test
    fun durationOutOfRangeIsRejected() {
        val vm = vm()
        vm.setDuration("2000")
        vm.save()
        assertNotNull(vm.state.value.errors["duration"])
        assertEquals(0, logbook.saveCalls)
    }

    @Test
    fun invalidCatchRowIsFlaggedAndNothingIsSaved() {
        val vm = vm()
        val key = vm.addCatchRow("", "1", "1") // peso sin especie
        vm.save()
        assertEquals(0, logbook.saveCalls)
        assertFalse(vm.state.value.saved)
        assertNotNull(vm.state.value.catches.single { it.key == key }.error)
    }

    @Test
    fun negativeWeightIsRejected() {
        val vm = vm()
        vm.addCatchRow("Sargo", "-2", "1")
        vm.save()
        assertEquals(0, logbook.saveCalls)
        assertNotNull(vm.state.value.catches.single().error)
    }

    @Test
    fun sessionWithoutCatchesCanBeSaved() {
        val vm = vm()
        vm.save()
        assertTrue(vm.state.value.saved)
        assertTrue(logbook.all.single().catches.isEmpty())
    }

    @Test
    fun apneaDataIsSavedForFreedivingButDroppedForScuba() {
        val free = vm()
        free.setActivity(Activity.FREEDIVING)
        free.setApneaDepth("12,5")
        free.setApneaTime("95")
        free.save()
        assertEquals(ApneaStats(12.5, 95), logbook.all.single().apnea)

        val scuba = vm()
        scuba.setActivity(Activity.SCUBA)
        scuba.setApneaDepth("30")
        scuba.save()
        assertNull(logbook.all.last().apnea)
    }

    @Test
    fun invalidApneaValuesAreReported() {
        val vm = vm()
        vm.setApneaDepth("-3")
        vm.setApneaTime("x")
        vm.save()
        assertNotNull(vm.state.value.errors["apneaDepth"])
        assertNotNull(vm.state.value.errors["apneaTime"])
        assertEquals(0, logbook.saveCalls)
    }

    @Test
    fun saveFailureShowsMessageAndAllowsRetry() {
        val vm = vm()
        logbook.failOnSave = true
        vm.save()
        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.saveError)

        logbook.failOnSave = false
        vm.save()
        assertTrue(vm.state.value.saved)
        assertEquals(1, logbook.all.size)
    }

    @Test
    fun dateAndTimeChangesKeepTheZone() {
        val vm = vm()
        vm.setDate(LocalDate.parse("2026-08-15"))
        vm.setTime(LocalTime.of(7, 45))
        val s = vm.state.value.startedAt
        assertEquals(LocalDate.parse("2026-08-15"), s.toLocalDate())
        assertEquals(LocalTime.of(7, 45), s.toLocalTime())
        assertEquals(MADRID, s.zone)
        vm.save()
        assertEquals(LocalDate.parse("2026-08-15"), logbook.all.single().localDate)
    }

    // ---- Editar --------------------------------------------------------------------------------

    @Test
    fun editingLoadsTheExistingSessionIntoTheForm() {
        val cala = location(1, "Cala")
        locations.seed(cala)
        logbook.seed(
            session(
                id = 7, location = cala, notes = "nota previa",
                catches = listOf(CatchItem(species = "Dorada", weightKg = 1.0, count = 3))
            )
        )
        val vm = vm(7)
        assertFalse(vm.isNew)
        val s = vm.state.value
        assertFalse(s.loading)
        assertFalse(s.notFound)
        assertEquals("nota previa", s.notes)
        assertEquals(1L, s.locationId)
        assertEquals("120", s.durationText)
        assertEquals("Dorada", s.catches.single().species)
        assertEquals("1", s.catches.single().weight)
        assertEquals("3", s.catches.single().count)
    }

    @Test
    fun editingUpdatesTheSameSessionInsteadOfCreatingAnother() {
        logbook.seed(session(id = 7, notes = "antes", catches = listOf(CatchItem(species = "Sargo", weightKg = 0.5, count = 1))))
        val vm = vm(7)
        vm.setNotes("después")
        val key = vm.state.value.catches.single().key
        vm.updateCatch(key, species = "Dorada", count = "4")
        vm.save()

        assertTrue(vm.state.value.saved)
        val stored = logbook.all.single()
        assertEquals(7L, stored.id)
        assertEquals("después", stored.notes)
        assertEquals(listOf("Dorada"), stored.catches.map { it.species })
        assertEquals(4, stored.catches.single().count)
    }

    @Test
    fun removingACatchRowRemovesItFromTheStoredSession() {
        logbook.seed(
            session(
                id = 3,
                catches = listOf(
                    CatchItem(species = "Sargo", weightKg = 0.5, count = 1),
                    CatchItem(species = "Dorada", weightKg = 1.0, count = 1)
                )
            )
        )
        val vm = vm(3)
        vm.removeCatch(vm.state.value.catches.first().key)
        vm.save()
        assertEquals(listOf("Dorada"), logbook.all.single().catches.map { it.species })
    }

    @Test
    fun editingAMissingSessionReportsNotFound() {
        val vm = vm(99)
        assertTrue(vm.state.value.notFound)
        assertFalse(vm.state.value.loading)
    }

    // ---- Condiciones de la API -------------------------------------------------------------------

    private fun hour(iso: String, wave: Double?) =
        HourlyConditions(
            time = Instant.parse(iso), airTempC = 21.0, windSpeedKmh = 9.0, waveHeightM = wave,
            wavePeriodS = 5.0, seaTempC = 20.0
        )

    @Test
    fun attachingConditionsWithoutALocationAsksForOne() {
        val vm = vm()
        vm.attachConditions()
        assertNotNull(vm.state.value.conditionsMessage)
        assertNull(vm.state.value.conditions)
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun attachingConditionsUsesTheNearestForecastHourAndMarksTheSource() {
        locations.seed(location(1, "Cala"))
        weather.result = ForecastResult.Fresh(
            bundle(
                listOf(hour("2026-10-20T08:00:00Z", 0.4), hour("2026-10-20T10:00:00Z", 1.5)),
                source = "Open-Meteo"
            )
        )
        val vm = vm()
        vm.setLocation(1)
        vm.attachConditions()

        val c = vm.state.value.conditions!!
        assertEquals(0.4, c.waveM!!, 0.0) // 08:30Z está a 30 min de las 08:00Z y a 90 min de las 10:00Z
        assertEquals(21.0, c.airTempC!!, 0.0)
        assertEquals("Open-Meteo", c.source)
        assertFalse(vm.state.value.conditionsLoading)

        vm.save()
        assertEquals("Open-Meteo", logbook.all.single().conditions?.source)
    }

    @Test
    fun conditionsAreNotAttachedWhenForecastIsFarFromTheSessionTime() {
        locations.seed(location(1))
        weather.result = ForecastResult.Fresh(bundle(listOf(hour("2026-10-25T10:00:00Z", 0.4))))
        val vm = vm()
        vm.setLocation(1)
        vm.attachConditions()
        assertNull(vm.state.value.conditions)
        assertNotNull(vm.state.value.conditionsMessage)
    }

    @Test
    fun conditionsFailureIsReportedWithoutInventingValues() {
        locations.seed(location(1))
        val vm = vm()
        vm.setLocation(1)
        vm.attachConditions() // el repositorio de prueba devuelve Failed
        assertNull(vm.state.value.conditions)
        assertNotNull(vm.state.value.conditionsMessage)
        assertFalse(vm.state.value.conditionsLoading)
    }

    @Test
    fun staleForecastStillProvidesConditions() {
        locations.seed(location(1))
        weather.result = ForecastResult.Stale(
            bundle(listOf(hour("2026-10-20T08:00:00Z", 0.6))), ForecastError.NETWORK
        )
        val vm = vm()
        vm.setLocation(1)
        vm.attachConditions()
        assertEquals(0.6, vm.state.value.conditions!!.waveM!!, 0.0)
    }

    // ---- Fotos -----------------------------------------------------------------------------------

    @Test
    fun photosAreImportedAndCappedAtTheMaximum() {
        val vm = vm()
        val uris = (1..12).map { "u$it" }
        vm.addPhotos(uris)
        assertEquals(EditSessionViewModel.MAX_PHOTOS, vm.state.value.photos.size)
        assertNotNull(vm.state.value.photoMessage)
        vm.photoMessageShown()
        assertNull(vm.state.value.photoMessage)
    }

    @Test
    fun unreadablePhotosAreSkippedWithAMessage() {
        photos.failUris += "bad"
        val vm = vm()
        vm.addPhotos(listOf("ok", "bad"))
        assertEquals(listOf("private/ok.jpg"), vm.state.value.photos)
        assertNotNull(vm.state.value.photoMessage)
    }

    @Test
    fun removingAPhotoImportedInThisEditDeletesItsFileRightAway() {
        val vm = vm()
        vm.addPhotos(listOf("a"))
        val path = vm.state.value.photos.single()
        vm.removePhoto(path)
        assertTrue(vm.state.value.photos.isEmpty())
        assertEquals(listOf(path), photos.deleted)
    }

    @Test
    fun savedPhotosAreLinkedToTheSession() {
        val vm = vm()
        vm.addPhotos(listOf("a", "b"))
        vm.save()
        assertEquals(listOf("private/a.jpg", "private/b.jpg"), logbook.all.single().photoPaths)
    }

    @Test
    fun removingAnOriginalPhotoDoesNotDeleteTheFileUntilSaved() {
        logbook.seed(session(id = 1).copy(photoPaths = listOf("private/old.jpg")))
        val vm = vm(1)
        vm.removePhoto("private/old.jpg")
        assertTrue(photos.deleted.isEmpty()) // la eliminación real la hace el repositorio al guardar
        assertTrue(vm.state.value.photos.isEmpty())
    }

    // ---- Visibilidad que viste (calibración) ----------------------------------------------------

    private class FakeEstimates(var value: Int?) : com.pescasub.app.domain.visibility.VisibilityEstimateSource {
        var calls = 0
        override suspend fun centralMeters(coordinates: com.pescasub.app.domain.Coordinates): Int? {
            calls++
            return value
        }
    }

    private fun vmWith(est: FakeEstimates, id: Long = 0) =
        EditSessionViewModel(id, logbook, locations, weather, photos, clock, est)

    @Test
    fun observedVisibilityIsStoredWithTheModelEstimateOfThatMoment() {
        locations.seed(location(name = "Cala"))
        val est = FakeEstimates(8)
        val vm = vmWith(est)
        vm.setLocation(locations.all.single().id)
        vm.setVisibility("5")
        vm.save()
        val stored = logbook.all.single()
        assertEquals(5, stored.observedVisibilityM)
        assertEquals(8, stored.estimatedVisibilityM)
    }

    @Test
    fun withoutAnObservedValueNothingIsAskedOrStored() {
        locations.seed(location(name = "Cala"))
        val est = FakeEstimates(8)
        val vm = vmWith(est)
        vm.setLocation(locations.all.single().id)
        vm.save()
        val stored = logbook.all.single()
        assertNull(stored.observedVisibilityM)
        assertNull(stored.estimatedVisibilityM)
        assertEquals(0, est.calls)
    }

    @Test
    fun withoutASavedLocationTheObservationIsKeptButNotPairedWithAnEstimate() {
        val est = FakeEstimates(8)
        val vm = vmWith(est)
        vm.setVisibility("5")
        vm.save()
        val stored = logbook.all.single()
        assertEquals(5, stored.observedVisibilityM)
        assertNull(stored.estimatedVisibilityM)
    }

    @Test
    fun anOldSessionIsNotPairedWithTodaysEstimate() {
        locations.seed(location(name = "Cala"))
        val est = FakeEstimates(8)
        val vm = vmWith(est)
        vm.setLocation(locations.all.single().id)
        vm.setDate(java.time.LocalDate.of(2026, 10, 1)) // 19 días antes del reloj de la prueba
        vm.setVisibility("5")
        vm.save()
        assertEquals(5, logbook.all.single().observedVisibilityM)
        assertNull(logbook.all.single().estimatedVisibilityM)
    }

    @Test
    fun aFailingEstimateNeverBlocksSavingTheSession() {
        locations.seed(location(name = "Cala"))
        val vm = vmWith(FakeEstimates(null))
        vm.setLocation(locations.all.single().id)
        vm.setVisibility("5")
        vm.save()
        assertTrue(vm.state.value.saved)
        assertNull(logbook.all.single().estimatedVisibilityM)
    }

    @Test
    fun outOfRangeOrNonNumericVisibilityIsRejectedAndNothingIsSaved() {
        listOf("0", "61", "-3", "abc", "2,5").forEach { text ->
            val vm = vmWith(FakeEstimates(8))
            vm.setVisibility(text)
            vm.save()
            assertNotNull("'$text' debía dar error", vm.state.value.errors["visibility"])
            assertTrue("'$text'", logbook.all.isEmpty())
        }
    }

    @Test
    fun editingWithoutChangingTheObservationKeepsTheOriginalPair() {
        locations.seed(location(name = "Cala"))
        val loc = locations.all.single()
        logbook.seed(session(location = loc).copy(observedVisibilityM = 4, estimatedVisibilityM = 9))
        val id = logbook.all.single().id
        val est = FakeEstimates(20) // si se recalculase, cambiaría
        val vm = vmWith(est, id)
        vm.setNotes("solo cambio las notas")
        vm.save()
        assertEquals(9, logbook.all.single().estimatedVisibilityM)
        assertEquals(0, est.calls)
    }

    @Test
    fun clearingTheObservationRemovesBothValues() {
        locations.seed(location(name = "Cala"))
        logbook.seed(session(location = locations.all.single()).copy(observedVisibilityM = 4, estimatedVisibilityM = 9))
        val vm = vmWith(FakeEstimates(8), logbook.all.single().id)
        vm.setVisibility("")
        vm.save()
        assertNull(logbook.all.single().observedVisibilityM)
        assertNull(logbook.all.single().estimatedVisibilityM)
    }
}
