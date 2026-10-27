package com.pescasub.app.ui

import com.pescasub.app.domain.Activity
import com.pescasub.app.domain.CatchItem
import com.pescasub.app.domain.LogbookFilter
import com.pescasub.app.domain.SortOrder
import com.pescasub.app.testing.FakeLogbookRepository
import com.pescasub.app.testing.location
import com.pescasub.app.testing.session
import com.pescasub.app.ui.logbook.DetailUiState
import com.pescasub.app.ui.logbook.LogbookViewModel
import com.pescasub.app.ui.logbook.SessionDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
class LogbookViewModelTest {
    private lateinit var repo: FakeLogbookRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeLogbookRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val cala = location(1, "Cala")
    private val roca = location(2, "Roca")

    private fun seedSample() {
        repo.seed(
            session(
                id = 1, date = "2026-08-10", location = cala,
                catches = listOf(CatchItem(species = "Dorada", weightKg = 1.2, count = 1))
            ),
            session(
                id = 2, date = "2026-09-02", activity = Activity.FREEDIVING, location = roca
            ),
            session(
                id = 3, date = "2026-09-20", location = roca,
                catches = listOf(
                    CatchItem(species = "dorada", weightKg = 0.8, count = 2),
                    CatchItem(species = "Sargo", weightKg = 0.5, count = 1)
                )
            )
        )
    }

    private fun <T> withCollector(vm: LogbookViewModel, block: () -> T): T = runBlocking {
        val job = launch(Dispatchers.Unconfined) { vm.state.collect { } }
        try {
            block()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun emptyLogbookIsLoadedButEmptyAndStatsAreZero() {
        val vm = LogbookViewModel(repo)
        withCollector(vm) {
            val s = vm.state.value
            assertTrue(s.loaded)
            assertEquals(0, s.totalCount)
            assertEquals(0, s.stats.sessions)
            assertTrue(s.sessions.isEmpty())
        }
    }

    @Test
    fun sessionsAreListedNewestFirstWithOptionsDerivedFromRealData() {
        seedSample()
        val vm = LogbookViewModel(repo)
        withCollector(vm) {
            val s = vm.state.value
            assertEquals(listOf(3L, 2L, 1L), s.sessions.map { it.id })
            assertEquals(3, s.totalCount)
            assertEquals(listOf("Dorada", "Sargo"), s.speciesOptions) // sin duplicar por mayúsculas
            assertEquals(listOf("Cala", "Roca"), s.locationOptions.map { it.name })
        }
    }

    @Test
    fun filtersNarrowTheListButNotTheTotalAndStatsFollowTheFilter() {
        seedSample()
        val vm = LogbookViewModel(repo)
        withCollector(vm) {
            vm.setFilter(LogbookFilter(locationId = 2))
            val s = vm.state.value
            assertEquals(listOf(3L, 2L), s.sessions.map { it.id })
            assertEquals(3, s.totalCount)
            assertEquals(2, s.stats.sessions)
            assertEquals(3, s.stats.totalCatches)

            vm.setFilter(LogbookFilter(species = "dorada"))
            assertEquals(setOf(1L, 3L), vm.state.value.sessions.map { it.id }.toSet())

            vm.setFilter(LogbookFilter(activity = Activity.FREEDIVING))
            assertEquals(listOf(2L), vm.state.value.sessions.map { it.id })
        }
    }

    @Test
    fun sortingIsApplied() {
        seedSample()
        val vm = LogbookViewModel(repo)
        withCollector(vm) {
            vm.setFilter(LogbookFilter(sort = SortOrder.OLDEST))
            assertEquals(listOf(1L, 2L, 3L), vm.state.value.sessions.map { it.id })
            vm.setFilter(LogbookFilter(sort = SortOrder.MOST_CATCHES))
            assertEquals(3L, vm.state.value.sessions.first().id)
        }
    }

    @Test
    fun newSessionsAppearAndDeletedOnesDisappear() {
        seedSample()
        val vm = LogbookViewModel(repo)
        withCollector(vm) {
            runBlocking { repo.delete(1) }
            assertEquals(setOf(2L, 3L), vm.state.value.sessions.map { it.id }.toSet())
            // queda solo "dorada" (minúscula) de la jornada 3: se conserva la grafía realmente guardada
            assertEquals(listOf("dorada", "Sargo"), vm.state.value.speciesOptions)
            runBlocking { repo.delete(3) }
            assertTrue(vm.state.value.speciesOptions.isEmpty())
        }
    }

    // ---- Detalle ---------------------------------------------------------------------------------

    private fun <T> withDetailCollector(vm: SessionDetailViewModel, block: () -> T): T = runBlocking {
        val job = launch(Dispatchers.Unconfined) { vm.state.collect { } }
        try {
            block()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun detailShowsTheSessionOrNotFound() {
        seedSample()
        val existing = SessionDetailViewModel(1, repo)
        withDetailCollector(existing) {
            val state = existing.state.value
            assertTrue(state is DetailUiState.Content)
            assertEquals(1L, (state as DetailUiState.Content).session.id)
        }
        val missing = SessionDetailViewModel(404, repo)
        withDetailCollector(missing) {
            assertEquals(DetailUiState.NotFound, missing.state.value)
        }
    }

    @Test
    fun deletingFromDetailRemovesTheSessionAndNotifiesCompletion() {
        seedSample()
        val vm = SessionDetailViewModel(2, repo)
        var done = false
        vm.delete { done = true }
        assertTrue(done)
        assertEquals(listOf(2L), repo.deleted)
        assertNull(repo.all.firstOrNull { it.id == 2L })
        assertNull(vm.error.value)
    }

    @Test
    fun deleteFailureIsReportedAndDoesNotNavigateAway() {
        seedSample()
        repo.failOnDelete = true
        val vm = SessionDetailViewModel(2, repo)
        var done = false
        vm.delete { done = true }
        assertFalse(done)
        assertNotNull(vm.error.value)
        assertNotNull(repo.all.firstOrNull { it.id == 2L })
        vm.errorShown()
        assertNull(vm.error.value)
    }
}
