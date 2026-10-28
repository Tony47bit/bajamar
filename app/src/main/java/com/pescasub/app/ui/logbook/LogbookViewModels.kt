package com.pescasub.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pescasub.app.AppContainer
import com.pescasub.app.domain.LogbookFilter
import com.pescasub.app.domain.LogbookRepository
import com.pescasub.app.domain.LogbookStats
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.Session
import com.pescasub.app.domain.normalizeSpecies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogbookUiState(
    val loaded: Boolean = false,
    val filter: LogbookFilter = LogbookFilter(),
    val sessions: List<Session> = emptyList(),
    val totalCount: Int = 0,
    val stats: LogbookStats = LogbookStats.from(emptyList()),
    val speciesOptions: List<String> = emptyList(),
    val locationOptions: List<SavedLocation> = emptyList()
)

class LogbookViewModel(repository: LogbookRepository) : ViewModel() {
    private val filter = MutableStateFlow(LogbookFilter())

    val state: StateFlow<LogbookUiState> = combine(
        repository.observeAll().catch { emit(emptyList()) },
        filter
    ) { all, f ->
        val filtered = f.apply(all)
        LogbookUiState(
            loaded = true,
            filter = f,
            sessions = filtered,
            totalCount = all.size,
            stats = LogbookStats.from(filtered),
            speciesOptions = all.flatMap { it.catches }
                .map { normalizeSpecies(it.species) }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() },
            locationOptions = all.mapNotNull { it.location }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookUiState())

    fun setFilter(newFilter: LogbookFilter) {
        filter.value = newFilter
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LogbookViewModel(container.logbookRepository) }
        }
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Content(val session: Session) : DetailUiState
}

class SessionDetailViewModel(
    private val sessionId: Long,
    private val repository: LogbookRepository
) : ViewModel() {
    val state: StateFlow<DetailUiState> = repository.observe(sessionId)
        .map<Session?, DetailUiState> { s -> if (s == null) DetailUiState.NotFound else DetailUiState.Content(s) }
        .catch { emit(DetailUiState.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.delete(sessionId)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "No se pudo eliminar la jornada."
            }
        }
    }

    fun errorShown() {
        _error.value = null
    }

    companion object {
        fun factory(container: AppContainer, sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionDetailViewModel(sessionId, container.logbookRepository) }
        }
    }
}
