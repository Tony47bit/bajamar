package com.pescasub.app.ui

import com.pescasub.app.domain.Coordinates
import com.pescasub.app.testing.FakeLocationRepository
import com.pescasub.app.testing.location
import com.pescasub.app.ui.map.MapSelection
import com.pescasub.app.ui.map.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private lateinit var repo: FakeLocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeLocationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectingAPointKeepsItTemporaryUntilSaved() {
        val vm = MapViewModel(repo)
        vm.selectTemp(Coordinates(36.7, -4.4), "Málaga")
        val sel = vm.selection.value as MapSelection.Temp
        assertEquals(Coordinates(36.7, -4.4), sel.coordinates)
        assertEquals("Málaga", sel.label)
        assertTrue(repo.all.isEmpty()) // nada se guarda sin acción del usuario
    }

    @Test
    fun savingANewPointPersistsItAndSelectsTheSavedVersion() {
        val vm = MapViewModel(repo)
        vm.selectTemp(Coordinates(36.7213, -4.4214))
        vm.save(existing = null, name = "Cala Honda", description = "Entrada norte", coordinates = Coordinates(36.7213, -4.4214))

        val stored = repo.all.single()
        assertEquals("Cala Honda", stored.name)
        assertEquals("Entrada norte", stored.description)
        assertEquals(Coordinates(36.7213, -4.4214), stored.coordinates) // coordenadas exactas, solo en local
        assertEquals(MapSelection.Saved(stored.id), vm.selection.value)
    }

    @Test
    fun editingAPointUpdatesNameAndDescriptionKeepingIdAndCreationDate() {
        repo.seed(location(name = "Vieja"))
        val original = repo.all.single()
        val vm = MapViewModel(repo)
        vm.save(original, "Nueva", "Con descripción", original.coordinates)

        val stored = repo.all.single()
        assertEquals(original.id, stored.id)
        assertEquals("Nueva", stored.name)
        assertEquals("Con descripción", stored.description)
        assertEquals(original.createdAt, stored.createdAt)
    }

    @Test
    fun deletingTheSelectedPointClearsTheSelection() {
        repo.seed(location(name = "A"), location(name = "B"))
        val (a, b) = repo.all
        val vm = MapViewModel(repo)
        vm.selectSaved(a.id)
        vm.delete(a.id)
        assertEquals(listOf(b.id), repo.all.map { it.id })
        assertNull(vm.selection.value)

        // borrar otro punto no cambia la selección actual
        vm.selectSaved(b.id)
        repo.seed(location(name = "C"))
        vm.delete(repo.all.last().id)
        assertEquals(MapSelection.Saved(b.id), vm.selection.value)
    }

    @Test
    fun storageFailuresProduceAMessageInsteadOfACrash() {
        val vm = MapViewModel(repo)
        repo.failOnSave = true
        vm.save(null, "X", "", Coordinates(1.0, 1.0))
        assertNotNull(vm.message.value)
        vm.messageShown()
        assertNull(vm.message.value)

        repo.failOnSave = false
        repo.seed(location(name = "Y"))
        repo.failOnDelete = true
        vm.delete(repo.all.single().id)
        assertNotNull(vm.message.value)
        assertEquals(1, repo.all.size)
    }
}
