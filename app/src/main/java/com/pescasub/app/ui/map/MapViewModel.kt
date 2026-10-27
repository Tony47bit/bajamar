package com.pescasub.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pescasub.app.AppContainer
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.LocationRepository
import com.pescasub.app.domain.SavedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MapSelection {
    /** Punto tocado en el mapa, resultado de búsqueda o "mi ubicación": aún no guardado. */
    data class Temp(val coordinates: Coordinates, val label: String?) : MapSelection

    data class Saved(val id: Long) : MapSelection
}

class MapViewModel(private val locations: LocationRepository) : ViewModel() {
    val saved: StateFlow<List<SavedLocation>> = locations.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<MapSelection?>(null)
    val selection: StateFlow<MapSelection?> = _selection.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun selectTemp(coordinates: Coordinates, label: String? = null) {
        _selection.value = MapSelection.Temp(coordinates, label)
    }

    fun selectSaved(id: Long) {
        _selection.value = MapSelection.Saved(id)
    }

    fun clearSelection() {
        _selection.value = null
    }

    fun messageShown() {
        _message.value = null
    }

    /** Crea (id = 0) o actualiza una ubicación guardada. Es privada: solo se almacena en el dispositivo. */
    fun save(
        existing: SavedLocation?,
        name: String,
        description: String,
        coordinates: Coordinates
    ) {
        viewModelScope.launch {
            try {
                val location = SavedLocation(
                    id = existing?.id ?: 0,
                    name = name,
                    description = description,
                    coordinates = coordinates,
                    isFavorite = existing?.isFavorite ?: true,
                    createdAt = existing?.createdAt ?: java.time.Instant.now()
                )
                val id = locations.save(location)
                _selection.value = MapSelection.Saved(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "No se pudo guardar la ubicación."
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                locations.delete(id)
                if ((_selection.value as? MapSelection.Saved)?.id == id) _selection.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "No se pudo eliminar la ubicación."
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MapViewModel(container.locationRepository) }
        }
    }
}
