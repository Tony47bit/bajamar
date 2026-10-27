package com.pescasub.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pescasub.app.AppContainer
import com.pescasub.app.domain.Activity
import com.pescasub.app.domain.ApneaStats
import com.pescasub.app.domain.CatchItem
import com.pescasub.app.domain.ConditionsSnapshot
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.FormValidation
import com.pescasub.app.domain.LocationRepository
import com.pescasub.app.domain.LogbookRepository
import com.pescasub.app.domain.PhotoStore
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.Session
import com.pescasub.app.domain.Validated
import com.pescasub.app.domain.WeatherRepository
import com.pescasub.app.domain.normalizeSpecies
import com.pescasub.app.domain.visibility.NoVisibilityEstimates
import com.pescasub.app.domain.visibility.VisibilityCalibrator
import com.pescasub.app.domain.visibility.VisibilityEstimateSource
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatchDraft(
    val key: Long,
    val species: String = "",
    val weight: String = "",
    val count: String = "1",
    val error: String? = null
) {
    val isBlank: Boolean get() = species.isBlank() && weight.isBlank() && (count.isBlank() || count.trim() == "1")
}

data class EditUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val startedAt: ZonedDateTime,
    val activity: Activity = Activity.SPEARFISHING,
    val locationId: Long? = null,
    val durationText: String = "",
    val notes: String = "",
    val apneaDepthText: String = "",
    val apneaTimeText: String = "",
    /** Visibilidad horizontal que viste, en metros (opcional; sirve para ajustar la estimación de este punto). */
    val visibilityText: String = "",
    val catches: List<CatchDraft> = emptyList(),
    val photos: List<String> = emptyList(),
    val conditions: ConditionsSnapshot? = null,
    val conditionsMessage: String? = null,
    val conditionsLoading: Boolean = false,
    /** Errores por campo: duration, apneaDepth, apneaTime, notes. */
    val errors: Map<String, String> = emptyMap(),
    val photoMessage: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false
)

/** Alta y edición de una jornada. `sessionId == 0` crea una nueva. */
class EditSessionViewModel(
    private val sessionId: Long,
    private val logbook: LogbookRepository,
    locations: LocationRepository,
    private val weather: WeatherRepository,
    private val photoStore: PhotoStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val visibilityEstimates: VisibilityEstimateSource = NoVisibilityEstimates
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState(startedAt = ZonedDateTime.now(clock)))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    /** True al crear una jornada nueva (id 0). */
    val isNew: Boolean get() = sessionId == 0L

    val locationOptions: StateFlow<List<SavedLocation>> = locations.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Especies ya usadas, para sugerirlas al escribir una captura. */
    val speciesSuggestions: StateFlow<List<String>> = logbook.observeAll()
        .map { all ->
            all.flatMap { it.catches }
                .map { normalizeSpecies(it.species) }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val importedPhotos = mutableSetOf<String>()
    private var nextCatchKey = 1L
    private var persisted = false
    private var originalObservedM: Int? = null
    private var originalEstimatedM: Int? = null

    init {
        if (sessionId == 0L) {
            _state.update { it.copy(loading = false) }
        } else {
            viewModelScope.launch {
                val session = try {
                    logbook.get(sessionId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (session == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                } else {
                    populate(session)
                }
            }
        }
    }

    private fun populate(s: Session) {
        originalObservedM = s.observedVisibilityM
        originalEstimatedM = s.estimatedVisibilityM
        _state.value = EditUiState(
            loading = false,
            startedAt = s.startedAt.atZone(s.zone),
            activity = s.activity,
            locationId = s.location?.id,
            durationText = s.durationMinutes?.toString().orEmpty(),
            notes = s.notes,
            apneaDepthText = s.apnea?.maxDepthM?.let(::decimalText).orEmpty(),
            apneaTimeText = s.apnea?.maxTimeSec?.toString().orEmpty(),
            visibilityText = s.observedVisibilityM?.toString().orEmpty(),
            catches = s.catches.map {
                CatchDraft(nextCatchKey++, it.species, it.weightKg?.let(::decimalText).orEmpty(), it.count.toString())
            },
            photos = s.photoPaths,
            conditions = s.conditions
        )
    }

    // ---- Campos ---------------------------------------------------------------------------------

    fun setDate(date: LocalDate) = _state.update { it.copy(startedAt = it.startedAt.with(date)) }

    fun setTime(time: LocalTime) = _state.update { it.copy(startedAt = it.startedAt.with(time)) }

    fun setActivity(activity: Activity) = _state.update { it.copy(activity = activity) }

    fun setLocation(id: Long?) = _state.update { it.copy(locationId = id, conditionsMessage = null) }

    fun setDuration(text: String) = _state.update { it.copy(durationText = text, errors = it.errors - "duration") }

    fun setNotes(text: String) = _state.update { it.copy(notes = text, errors = it.errors - "notes") }

    fun setApneaDepth(text: String) = _state.update { it.copy(apneaDepthText = text, errors = it.errors - "apneaDepth") }

    fun setVisibility(text: String) = _state.update { it.copy(visibilityText = text, errors = it.errors - "visibility") }

    fun setApneaTime(text: String) = _state.update { it.copy(apneaTimeText = text, errors = it.errors - "apneaTime") }

    // ---- Capturas -------------------------------------------------------------------------------

    fun addCatch(species: String = "") = _state.update {
        it.copy(catches = it.catches + CatchDraft(nextCatchKey++, species = species))
    }

    fun updateCatch(key: Long, species: String? = null, weight: String? = null, count: String? = null) =
        _state.update { state ->
            state.copy(
                catches = state.catches.map { c ->
                    if (c.key != key) c else c.copy(
                        species = species ?: c.species,
                        weight = weight ?: c.weight,
                        count = count ?: c.count,
                        error = null
                    )
                }
            )
        }

    fun removeCatch(key: Long) = _state.update { it.copy(catches = it.catches.filterNot { c -> c.key == key }) }

    // ---- Fotos ----------------------------------------------------------------------------------

    fun addPhotos(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var skipped = 0
            for (uri in uris) {
                if (_state.value.photos.size >= MAX_PHOTOS) {
                    skipped++
                    continue
                }
                val path = photoStore.import(uri)
                if (path == null) {
                    skipped++
                } else {
                    importedPhotos += path
                    _state.update { it.copy(photos = it.photos + path) }
                }
            }
            if (skipped > 0) {
                _state.update {
                    it.copy(photoMessage = "No se añadieron $skipped foto(s): máximo $MAX_PHOTOS o archivo no válido.")
                }
            }
        }
    }

    fun removePhoto(path: String) {
        // Las importadas en esta edición se borran ya; las de la jornada original, al guardar.
        if (importedPhotos.remove(path)) photoStore.delete(path)
        _state.update { it.copy(photos = it.photos - path) }
    }

    fun photoMessageShown() = _state.update { it.copy(photoMessage = null) }

    // ---- Condiciones de la API ------------------------------------------------------------------

    /** Adjunta las condiciones previstas para la hora de la jornada (solo si hay datos cercanos). */
    fun attachConditions() {
        val s = _state.value
        val location = locationOptions.value.firstOrNull { it.id == s.locationId }
        if (location == null) {
            _state.update { it.copy(conditionsMessage = "Elige primero una ubicación guardada.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(conditionsLoading = true, conditionsMessage = null) }
            val bundle = when (val r = weather.getForecast(location.coordinates)) {
                is ForecastResult.Fresh -> r.bundle
                is ForecastResult.Stale -> r.bundle
                is ForecastResult.Failed -> null
            }
            val target = _state.value.startedAt.toInstant()
            val nearest = bundle?.hourly?.minByOrNull { Math.abs(Duration.between(it.time, target).seconds) }
            val withinRange = nearest != null &&
                Math.abs(Duration.between(nearest.time, target).seconds) <= MAX_CONDITIONS_GAP_SECONDS
            _state.update {
                when {
                    bundle == null -> it.copy(
                        conditionsLoading = false,
                        conditionsMessage = "No se pudieron obtener las condiciones. Revisa la conexión."
                    )
                    nearest == null || !withinRange -> it.copy(
                        conditionsLoading = false,
                        conditionsMessage = "Solo hay datos para las horas cercanas a hoy; no hay previsión para esa fecha."
                    )
                    else -> it.copy(
                        conditionsLoading = false,
                        conditions = ConditionsSnapshot(
                            airTempC = nearest.airTempC,
                            windKmh = nearest.windSpeedKmh,
                            waveM = nearest.waveHeightM,
                            wavePeriodS = nearest.wavePeriodS,
                            seaTempC = nearest.seaTempC,
                            source = bundle.sourceName
                        )
                    )
                }
            }
        }
    }

    fun clearConditions() = _state.update { it.copy(conditions = null, conditionsMessage = null) }

    // ---- Guardado -------------------------------------------------------------------------------

    fun save() {
        val s = _state.value
        if (s.saving) return

        val errors = mutableMapOf<String, String>()
        val duration = FormValidation.optionalInt(s.durationText, 1, 1440, "La duración").valueOr(errors, "duration")
        val notes = FormValidation.notes(s.notes).valueOr(errors, "notes")
        val observedVisibility = FormValidation.optionalInt(
            s.visibilityText, VisibilityCalibrator.MIN_OBSERVED_M, VisibilityCalibrator.MAX_OBSERVED_M, "La visibilidad"
        ).valueOr(errors, "visibility")

        var apnea: ApneaStats? = null
        if (s.activity.hasApneaData) {
            val depth = FormValidation.optionalDecimal(s.apneaDepthText, 0.0, 200.0, "La profundidad")
                .valueOr(errors, "apneaDepth")
            val time = FormValidation.optionalInt(s.apneaTimeText, 1, 1800, "El tiempo de apnea")
                .valueOr(errors, "apneaTime")
            if (depth != null || time != null) apnea = ApneaStats(depth, time)
        }

        val drafts = mutableListOf<CatchDraft>()
        val items = mutableListOf<CatchItem>()
        var catchErrors = false
        for (draft in s.catches) {
            if (draft.isBlank) continue // las filas vacías se ignoran
            when (val r = FormValidation.catchItem(draft.species, draft.weight, draft.count)) {
                is Validated.Valid -> {
                    drafts += draft.copy(error = null)
                    items += r.value
                }
                is Validated.Invalid -> {
                    catchErrors = true
                    drafts += draft.copy(error = r.message)
                }
            }
        }

        if (errors.isNotEmpty() || catchErrors) {
            _state.update { it.copy(errors = errors, catches = drafts) }
            return
        }

        var session = Session(
            id = sessionId,
            startedAt = s.startedAt.toInstant(),
            zone = s.startedAt.zone,
            activity = s.activity,
            durationMinutes = duration,
            location = locationOptions.value.firstOrNull { it.id == s.locationId },
            notes = notes.orEmpty(),
            conditions = s.conditions,
            apnea = apnea,
            catches = items,
            photoPaths = s.photos,
            observedVisibilityM = observedVisibility
        )

        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = null, errors = emptyMap(), catches = drafts) }
            try {
                session = session.copy(estimatedVisibilityM = estimateToStore(session))
                logbook.save(session)
                persisted = true
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveError = "No se pudo guardar la jornada. Inténtalo de nuevo.") }
            }
        }
    }

    /**
     * Cifra del modelo (sin ajustar) que se guarda junto a lo que viste, para poder comparar después. Solo se calcula si
     * hay visibilidad anotada, ubicación guardada y la jornada es de las últimas 24 h (el modelo habla de «ahora»: no
     * sirve para jornadas antiguas). Si la anotación no cambia al editar, se conserva la cifra original.
     */
    private suspend fun estimateToStore(session: Session): Int? {
        val observed = session.observedVisibilityM ?: return null
        if (sessionId != 0L && observed == originalObservedM) return originalEstimatedM
        val location = session.location ?: return null
        val ageHours = Math.abs(Duration.between(session.startedAt, clock.instant()).toHours())
        if (ageHours > MAX_ESTIMATE_AGE_HOURS) return null
        return visibilityEstimates.centralMeters(location.coordinates)
    }

    fun saveErrorShown() = _state.update { it.copy(saveError = null) }

    /** Si se sale sin guardar, se borran las fotos copiadas durante esta edición. */
    override fun onCleared() {
        if (!persisted) importedPhotos.forEach(photoStore::delete)
        super.onCleared()
    }

    private fun <T> Validated<T>.valueOr(errors: MutableMap<String, String>, key: String): T? = when (this) {
        is Validated.Valid -> value
        is Validated.Invalid -> {
            errors[key] = message
            null
        }
    }

    private fun decimalText(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    companion object {
        const val MAX_PHOTOS = 10
        private const val MAX_CONDITIONS_GAP_SECONDS = 90 * 60L
        private const val MAX_ESTIMATE_AGE_HOURS = 24L

        fun factory(container: AppContainer, sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditSessionViewModel(
                    sessionId = sessionId,
                    logbook = container.logbookRepository,
                    locations = container.locationRepository,
                    weather = container.weatherRepository,
                    photoStore = container.photoStore,
                    visibilityEstimates = container.visibilityEstimateSource
                )
            }
        }
    }
}
