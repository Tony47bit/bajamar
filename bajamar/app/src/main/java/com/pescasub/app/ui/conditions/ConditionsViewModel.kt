package com.pescasub.app.ui.conditions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pescasub.app.AppContainer
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.ForecastError
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.LocationRepository
import com.pescasub.app.domain.Place
import com.pescasub.app.domain.PlaceSearch
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.Spot
import com.pescasub.app.domain.SpotStore
import com.pescasub.app.domain.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConditionsUiState(
    val spotLoaded: Boolean = false,
    val spot: Spot? = null,
    val loading: Boolean = false,
    val bundle: ForecastBundle? = null,
    /** Error del último intento. Si `bundle` no es nulo, son datos guardados anteriores. */
    val error: ForecastError? = null
)

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Place> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false
)

/**
 * Estado compartido por Inicio, Previsión y Mapa: la ubicación activa y su previsión.
 * Nunca registra coordenadas en logs.
 */
class ConditionsViewModel(
    private val spotStore: SpotStore,
    private val weather: WeatherRepository,
    locations: LocationRepository,
    private val places: PlaceSearch
) : ViewModel() {

    private val _state = MutableStateFlow(ConditionsUiState())
    val state: StateFlow<ConditionsUiState> = _state.asStateFlow()

    val savedLocations: StateFlow<List<SavedLocation>> = locations.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            spotStore.state.collect { s ->
                _state.update { it.copy(spotLoaded = s.loaded, spot = s.spot) }
                val spot = s.spot
                if (s.loaded && spot != null) {
                    load(spot, force = false)
                } else if (s.loaded) {
                    loadJob?.cancel()
                    _state.update { it.copy(bundle = null, error = null, loading = false) }
                }
            }
        }
    }

    private fun load(spot: Spot, force: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val target = spot.coordinates.coarse()
            _state.update {
                // Se conservan los datos solo si son de la misma posición (evita mostrar otro lugar).
                it.copy(loading = true, error = null, bundle = it.bundle?.takeIf { b -> b.coordinates == target })
            }
            when (val result = weather.getForecast(spot.coordinates, force)) {
                is ForecastResult.Fresh -> _state.update { it.copy(loading = false, bundle = result.bundle, error = null) }
                is ForecastResult.Stale -> _state.update { it.copy(loading = false, bundle = result.bundle, error = result.error) }
                is ForecastResult.Failed -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun refresh() {
        _state.value.spot?.let { load(it, force = true) }
    }

    fun selectSpot(spot: Spot) {
        viewModelScope.launch { spotStore.select(spot) }
    }

    fun selectSaved(location: SavedLocation) {
        selectSpot(Spot(location.name, location.coordinates, location.id))
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.length < 2) {
            _search.value = SearchUiState(query = query, error = "Escribe al menos 2 letras.")
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _search.value = SearchUiState(query = query, loading = true)
            try {
                val results = places.search(q)
                _search.value = SearchUiState(query = query, results = results, searched = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.value = SearchUiState(
                    query = query,
                    error = "No se pudo buscar. Comprueba la conexión.",
                    searched = true
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchUiState()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConditionsViewModel(
                    container.spotStore,
                    container.weatherRepository,
                    container.locationRepository,
                    container.placeSearch
                )
            }
        }
    }
}
