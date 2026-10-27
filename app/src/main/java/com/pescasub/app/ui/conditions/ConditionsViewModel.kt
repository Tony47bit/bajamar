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
import com.pescasub.app.domain.LogbookRepository
import com.pescasub.app.domain.visibility.CalibrationSample
import com.pescasub.app.domain.visibility.ClarityFetch
import com.pescasub.app.domain.visibility.VisibilityCalibration
import com.pescasub.app.domain.visibility.VisibilityCalibrator
import com.pescasub.app.domain.visibility.NoWaterClarity
import com.pescasub.app.domain.visibility.VisibilityEstimate
import com.pescasub.app.domain.visibility.VisibilityResolver
import com.pescasub.app.domain.visibility.WaterClarityRepository
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConditionsUiState(
    val spotLoaded: Boolean = false,
    val spot: Spot? = null,
    val loading: Boolean = false,
    val bundle: ForecastBundle? = null,
    /** Error del último intento. Si `bundle` no es nulo, son datos guardados anteriores. */
    val error: ForecastError? = null,
    /** Visibilidad submarina estimada para `bundle` (null mientras se calcula o si no hay previsión). */
    val visibility: VisibilityEstimate? = null,
    val visibilityLoading: Boolean = false
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
    private val places: PlaceSearch,
    private val clarity: WaterClarityRepository = NoWaterClarity,
    private val clock: Clock = Clock.systemUTC(),
    private val logbook: LogbookRepository? = null
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
                    _state.update { it.copy(bundle = null, error = null, loading = false, visibility = null, visibilityLoading = false) }
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
                val same = it.bundle?.takeIf { b -> b.coordinates == target }
                it.copy(
                    loading = true, error = null, bundle = same,
                    visibility = if (same != null) it.visibility else null
                )
            }
            val bundle = when (val result = weather.getForecast(spot.coordinates, force)) {
                is ForecastResult.Fresh -> {
                    _state.update { it.copy(loading = false, bundle = result.bundle, error = null) }
                    result.bundle
                }
                is ForecastResult.Stale -> {
                    _state.update { it.copy(loading = false, bundle = result.bundle, error = result.error) }
                    result.bundle
                }
                is ForecastResult.Failed -> {
                    _state.update { it.copy(loading = false, error = result.error) }
                    _state.value.bundle
                }
            }
            if (bundle != null) estimateVisibility(spot, bundle)
        }
    }

    /** Pide la última observación de transparencia y calcula la estimación; nunca bloquea la previsión. */
    private suspend fun estimateVisibility(spot: Spot, bundle: ForecastBundle) {
        _state.update { it.copy(visibilityLoading = true) }
        val fetch = try {
            clarity.latest(spot.coordinates)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ClarityFetch(null, refreshFailed = true)
        }
        val now = clock.instant()
        val estimate = VisibilityResolver.resolve(
            fetch = fetch,
            climatology = {
                try {
                    clarity.climatology(spot.coordinates, now)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            },
            recent = bundle.recent,
            upcoming = bundle.hourly,
            now = now,
            calibration = calibrationFor(spot, now)
        )
        _state.update { it.copy(visibility = estimate, visibilityLoading = false) }
    }

    /** Ajuste de la estimación con lo que el usuario anotó en este punto guardado (null si no hay suficientes). */
    private suspend fun calibrationFor(spot: Spot, now: java.time.Instant): VisibilityCalibration? {
        val id = spot.locationId ?: return null
        val repo = logbook ?: return null
        return try {
            val samples = repo.observeAll().first().mapNotNull { s ->
                val observed = s.observedVisibilityM ?: return@mapNotNull null
                val estimated = s.estimatedVisibilityM ?: return@mapNotNull null
                if (s.location?.id != id) return@mapNotNull null
                CalibrationSample(observed, estimated, s.startedAt)
            }
            VisibilityCalibrator.calibrate(samples, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
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
                    container.placeSearch,
                    container.waterClarityRepository,
                    logbook = container.logbookRepository
                )
            }
        }
    }
}
