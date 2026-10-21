package com.pescasub.app.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ForecastError { NETWORK, RATE_LIMITED, SERVER, PARSE, UNKNOWN }

sealed interface ForecastResult {
    /** Datos nuevos, o de caché todavía vigente (`bundle.fromCache`). */
    data class Fresh(val bundle: ForecastBundle) : ForecastResult

    /** La actualización falló, pero hay datos guardados anteriores. */
    data class Stale(val bundle: ForecastBundle, val error: ForecastError) : ForecastResult

    data class Failed(val error: ForecastError) : ForecastResult
}

/**
 * Proveedor de datos meteorológicos y marinos. Es intercambiable: la caché guarda la carga útil
 * opaca (`fetchRaw`) y cada proveedor sabe interpretarla (`parse`).
 */
interface WeatherProvider {
    val id: String
    val sourceName: String
    val attribution: String

    suspend fun fetchRaw(coordinates: Coordinates): String

    fun parse(raw: String, coordinates: Coordinates, fetchedAt: Instant): ForecastBundle
}

interface WeatherRepository {
    suspend fun getForecast(coordinates: Coordinates, forceRefresh: Boolean = false): ForecastResult
}

interface PlaceSearch {
    /** Lanza una excepción de E/S si no hay conexión. */
    suspend fun search(query: String): List<Place>
}

interface LocationRepository {
    fun observeAll(): Flow<List<SavedLocation>>
    suspend fun get(id: Long): SavedLocation?
    suspend fun save(location: SavedLocation): Long
    suspend fun delete(id: Long)
}

interface LogbookRepository {
    fun observeAll(): Flow<List<Session>>
    fun observe(id: Long): Flow<Session?>
    suspend fun get(id: Long): Session?

    /** Inserta (id = 0) o actualiza junto con capturas y fotos, de forma atómica. */
    suspend fun save(session: Session): Long
    suspend fun delete(id: Long)
}

interface PhotoStore {
    /** Copia la imagen elegida al almacenamiento privado de la app y devuelve su ruta. */
    suspend fun import(uri: String): String?

    fun delete(path: String)
}

data class SelectedSpotState(val loaded: Boolean = false, val spot: Spot? = null)

interface SpotStore {
    val state: StateFlow<SelectedSpotState>
    suspend fun select(spot: Spot)
    suspend fun clear()
}
