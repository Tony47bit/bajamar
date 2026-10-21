package com.pescasub.app.ui

import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.ForecastError
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.HourlyConditions
import com.pescasub.app.domain.Place
import com.pescasub.app.domain.Spot
import com.pescasub.app.testing.FakeLocationRepository
import com.pescasub.app.testing.FakePlaceSearch
import com.pescasub.app.testing.FakeSpotStore
import com.pescasub.app.testing.FakeWeatherRepository
import com.pescasub.app.testing.bundle
import com.pescasub.app.testing.location
import com.pescasub.app.ui.conditions.ConditionsViewModel
import java.io.IOException
import java.time.Instant
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
class ConditionsViewModelTest {
    private val malaga = Spot("Málaga", Coordinates(36.7213, -4.4214))
    private val okBundle = bundle(listOf(HourlyConditions(time = Instant.parse("2026-10-20T08:00:00Z"), windSpeedKmh = 10.0)))

    private lateinit var weather: FakeWeatherRepository
    private lateinit var places: FakePlaceSearch
    private lateinit var locations: FakeLocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        weather = FakeWeatherRepository(ForecastResult.Fresh(okBundle))
        places = FakePlaceSearch()
        locations = FakeLocationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(store: FakeSpotStore) = ConditionsViewModel(store, weather, locations, places)

    @Test
    fun withoutAStoredSpotNothingIsLoadedAndNoRequestIsMade() {
        val vm = vm(FakeSpotStore(initial = null))
        val s = vm.state.value
        assertTrue(s.spotLoaded)
        assertNull(s.spot)
        assertNull(s.bundle)
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun whileTheStoredSpotIsStillLoadingTheStateSaysSo() {
        val vm = vm(FakeSpotStore(initial = null, loaded = false))
        assertFalse(vm.state.value.spotLoaded)
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun aRestoredSpotLoadsItsForecast() {
        val vm = vm(FakeSpotStore(initial = malaga))
        val s = vm.state.value
        assertEquals(malaga, s.spot)
        assertEquals(okBundle, s.bundle)
        assertFalse(s.loading)
        assertNull(s.error)
        assertEquals(listOf(malaga.coordinates to false), weather.requests)
    }

    @Test
    fun failureWithoutSavedDataShowsTheErrorAndNoBundle() {
        weather.result = ForecastResult.Failed(ForecastError.NETWORK)
        val s = vm(FakeSpotStore(initial = malaga)).state.value
        assertNull(s.bundle)
        assertEquals(ForecastError.NETWORK, s.error)
        assertFalse(s.loading)
    }

    @Test
    fun staleDataIsShownTogetherWithTheErrorSoTheUserKnows() {
        weather.result = ForecastResult.Stale(okBundle.copy(fromCache = true), ForecastError.RATE_LIMITED)
        val s = vm(FakeSpotStore(initial = malaga)).state.value
        assertNotNull(s.bundle)
        assertTrue(s.bundle!!.fromCache)
        assertEquals(ForecastError.RATE_LIMITED, s.error)
    }

    @Test
    fun refreshForcesANewRequestAndRecoversFromAnEarlierError() {
        weather.result = ForecastResult.Failed(ForecastError.NETWORK)
        val vm = vm(FakeSpotStore(initial = malaga))
        weather.result = ForecastResult.Fresh(okBundle)
        vm.refresh()
        assertEquals(okBundle, vm.state.value.bundle)
        assertNull(vm.state.value.error)
        assertEquals(true, weather.requests.last().second)
    }

    @Test
    fun selectingASpotPersistsItAndLoadsItsForecast() {
        val store = FakeSpotStore(initial = null)
        val vm = vm(store)
        vm.selectSpot(malaga)
        assertEquals(malaga, store.state.value.spot)
        assertEquals(malaga, vm.state.value.spot)
        assertEquals(okBundle, vm.state.value.bundle)
    }

    @Test
    fun selectingASavedLocationKeepsItsId() {
        val store = FakeSpotStore(initial = null)
        val vm = vm(store)
        vm.selectSaved(location(5, "Cala Honda", 36.5, -4.9))
        val spot = vm.state.value.spot!!
        assertEquals("Cala Honda", spot.name)
        assertEquals(5L, spot.locationId)
        assertEquals(Coordinates(36.5, -4.9), spot.coordinates)
    }

    @Test
    fun switchingToAnotherPlaceDoesNotKeepShowingTheOldPlacesData() {
        val vm = vm(FakeSpotStore(initial = malaga))
        assertNotNull(vm.state.value.bundle)
        weather.result = ForecastResult.Failed(ForecastError.NETWORK)
        vm.selectSpot(Spot("Tarifa", Coordinates(36.01, -5.6)))
        // el bundle de Málaga (otras coordenadas) no debe mostrarse como si fuera Tarifa
        assertNull(vm.state.value.bundle)
        assertEquals(ForecastError.NETWORK, vm.state.value.error)
    }

    // ---- Búsqueda de lugares -----------------------------------------------------------------------

    @Test
    fun tooShortQueriesAreRejectedWithoutCallingTheService() {
        val vm = vm(FakeSpotStore())
        vm.search(" a ")
        assertNotNull(vm.search.value.error)
        assertTrue(places.queries.isEmpty())
    }

    @Test
    fun searchReturnsResultsAndTrimsTheQuery() {
        places.results = listOf(Place("Tarifa", "Andalucía, España", Coordinates(36.01, -5.6)))
        val vm = vm(FakeSpotStore())
        vm.search("  Tarifa ")
        val s = vm.search.value
        assertEquals(listOf("Tarifa"), places.queries)
        assertEquals(1, s.results.size)
        assertTrue(s.searched)
        assertNull(s.error)
        assertFalse(s.loading)
    }

    @Test
    fun searchWithoutMatchesIsMarkedAsSearchedWithEmptyResults() {
        val vm = vm(FakeSpotStore())
        vm.search("Zzzz")
        assertTrue(vm.search.value.searched)
        assertTrue(vm.search.value.results.isEmpty())
        assertNull(vm.search.value.error)
    }

    @Test
    fun searchNetworkFailureShowsAMessageAndDoesNotCrash() {
        places.failure = IOException("sin red")
        val vm = vm(FakeSpotStore())
        vm.search("Tarifa")
        assertNotNull(vm.search.value.error)
        assertTrue(vm.search.value.results.isEmpty())
        assertFalse(vm.search.value.loading)
    }

    @Test
    fun clearSearchResetsTheState() {
        places.results = listOf(Place("Tarifa", null, Coordinates(36.01, -5.6)))
        val vm = vm(FakeSpotStore())
        vm.search("Tarifa")
        vm.clearSearch()
        assertEquals(com.pescasub.app.ui.conditions.SearchUiState(), vm.search.value)
    }
}
