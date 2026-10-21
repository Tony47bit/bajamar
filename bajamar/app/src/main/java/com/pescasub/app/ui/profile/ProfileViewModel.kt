package com.pescasub.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pescasub.app.AppContainer
import com.pescasub.app.data.repo.DataEraser
import com.pescasub.app.domain.DataExporter
import com.pescasub.app.domain.LocationRepository
import com.pescasub.app.domain.LogbookRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val sessionCount: Int = 0,
    val locationCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null
)

class ProfileViewModel(
    private val logbook: LogbookRepository,
    private val locations: LocationRepository,
    private val eraser: DataEraser
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            logbook.observeAll().catch { emit(emptyList()) }.collect { list ->
                _state.update { it.copy(sessionCount = list.size) }
            }
        }
        viewModelScope.launch {
            locations.observeAll().catch { emit(emptyList()) }.collect { list ->
                _state.update { it.copy(locationCount = list.size) }
            }
        }
    }

    /** Genera el JSON y lo entrega a `sink` (que lo escribe en el archivo que eligió el usuario). */
    fun export(sink: suspend (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                val json = withContext(Dispatchers.Default) {
                    DataExporter.toJson(locations.observeAll().first(), logbook.observeAll().first(), Instant.now())
                }
                withContext(Dispatchers.IO) { sink(json) }
                _state.update { it.copy(busy = false, message = "Datos exportados correctamente.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "No se pudo exportar. Inténtalo de nuevo.") }
            }
        }
    }

    fun eraseAll() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                eraser.eraseAll()
                _state.update { it.copy(busy = false, message = "Se han eliminado todos tus datos de este dispositivo.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "No se pudieron eliminar los datos.") }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(container.logbookRepository, container.locationRepository, container.dataEraser) }
        }
    }
}
