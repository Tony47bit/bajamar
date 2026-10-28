package com.pescasub.app.testing

import com.pescasub.app.domain.Activity
import com.pescasub.app.domain.CatchItem
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.HourlyConditions
import com.pescasub.app.domain.LocationRepository
import com.pescasub.app.domain.LogbookRepository
import com.pescasub.app.domain.Place
import com.pescasub.app.domain.PlaceSearch
import com.pescasub.app.domain.PhotoStore
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.SelectedSpotState
import com.pescasub.app.domain.Session
import com.pescasub.app.domain.Spot
import com.pescasub.app.domain.SpotStore
import com.pescasub.app.domain.WeatherRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

/** Repositorio de bitácora en memoria. Reproduce lo importante: ids asignados y capturas ligadas a la jornada. */
class FakeLogbookRepository : LogbookRepository {
    private val sessions = MutableStateFlow<List<Session>>(emptyList())
    private var nextId = 1L
    var failOnSave = false
    var failOnDelete = false
    var saveCalls = 0
    val deleted = mutableListOf<Long>()

    val all: List<Session> get() = sessions.value

    fun seed(vararg list: Session) {
        list.forEach { s ->
            val id = if (s.id == 0L) nextId++ else s.id.also { nextId = maxOf(nextId, it + 1) }
            sessions.value = sessions.value + s.copy(id = id)
        }
    }

    override fun observeAll(): Flow<List<Session>> = sessions

    override fun observe(id: Long): Flow<Session?> = sessions.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: Long): Session? = sessions.value.firstOrNull { it.id == id }

    override suspend fun save(session: Session): Long {
        if (failOnSave) throw IllegalStateException("fallo simulado")
        saveCalls++
        val id = if (session.id == 0L) nextId++ else session.id
        val stored = session.copy(
            id = id,
            catches = session.catches.mapIndexed { i, c -> c.copy(id = id * 100 + i + 1) }
        )
        sessions.value = sessions.value.filter { it.id != id } + stored
        return id
    }

    override suspend fun delete(id: Long) {
        if (failOnDelete) throw IllegalStateException("fallo simulado")
        deleted += id
        sessions.value = sessions.value.filter { it.id != id }
    }
}

class FakeLocationRepository : LocationRepository {
    private val items = MutableStateFlow<List<SavedLocation>>(emptyList())
    private var nextId = 1L
    var failOnSave = false
    var failOnDelete = false

    val all: List<SavedLocation> get() = items.value

    fun seed(vararg list: SavedLocation) {
        list.forEach { l ->
            val id = if (l.id == 0L) nextId++ else l.id.also { nextId = maxOf(nextId, it + 1) }
            items.value = items.value + l.copy(id = id)
        }
    }

    override fun observeAll(): Flow<List<SavedLocation>> = items

    override suspend fun get(id: Long): SavedLocation? = items.value.firstOrNull { it.id == id }

    override suspend fun save(location: SavedLocation): Long {
        if (failOnSave) throw IllegalStateException("fallo simulado")
        val id = if (location.id == 0L) nextId++ else location.id
        items.value = items.value.filter { it.id != id } + location.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        if (failOnDelete) throw IllegalStateException("fallo simulado")
        items.value = items.value.filter { it.id != id }
    }
}

class FakeWeatherRepository(var result: ForecastResult) : WeatherRepository {
    val requests = mutableListOf<Pair<Coordinates, Boolean>>()

    override suspend fun getForecast(coordinates: Coordinates, forceRefresh: Boolean): ForecastResult {
        requests += coordinates to forceRefresh
        return result
    }
}

class FakeSpotStore(initial: Spot? = null, loaded: Boolean = true) : SpotStore {
    private val flow = MutableStateFlow(SelectedSpotState(loaded = loaded, spot = initial))
    override val state: StateFlow<SelectedSpotState> = flow

    override suspend fun select(spot: Spot) {
        flow.value = SelectedSpotState(loaded = true, spot = spot)
    }

    override suspend fun clear() {
        flow.value = SelectedSpotState(loaded = true, spot = null)
    }
}

class FakePhotoStore : PhotoStore {
    val failUris = mutableSetOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun import(uri: String): String? = if (uri in failUris) null else "private/$uri.jpg"

    override fun delete(path: String) {
        deleted += path
    }
}

class FakePlaceSearch : PlaceSearch {
    var results: List<Place> = emptyList()
    var failure: Exception? = null
    val queries = mutableListOf<String>()

    override suspend fun search(query: String): List<Place> {
        queries += query
        failure?.let { throw it }
        return results
    }
}

// ---- Constructores de datos de prueba ----------------------------------------------------------------

fun location(id: Long = 0, name: String = "Cala", lat: Double = 36.72, lon: Double = -4.42) =
    SavedLocation(id = id, name = name, coordinates = Coordinates(lat, lon), createdAt = Instant.EPOCH)

fun session(
    id: Long = 0,
    date: String = "2026-10-01",
    activity: Activity = Activity.SPEARFISHING,
    location: SavedLocation? = null,
    catches: List<CatchItem> = emptyList(),
    notes: String = ""
) = Session(
    id = id,
    startedAt = LocalDate.parse(date).atTime(10, 0).atZone(MADRID).toInstant(),
    zone = MADRID,
    activity = activity,
    durationMinutes = 120,
    location = location,
    notes = notes,
    conditions = null,
    apnea = null,
    catches = catches,
    photoPaths = emptyList()
)

fun bundle(
    hours: List<HourlyConditions>,
    source: String = "Prueba",
    fetchedAt: Instant = Instant.parse("2026-10-20T08:00:00Z")
) = ForecastBundle(
    coordinates = Coordinates(36.72, -4.42),
    zone = MADRID,
    fetchedAt = fetchedAt,
    sourceName = source,
    hourly = hours,
    tides = emptyList(),
    hasMarineData = hours.any { it.waveHeightM != null },
    notes = emptyList()
)
