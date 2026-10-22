package com.pescasub.app.data.remote

import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.visibility.ClarityFetch
import com.pescasub.app.domain.visibility.WaterClarityObservation
import com.pescasub.app.domain.visibility.WaterClarityRepository
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Lector de la respuesta `.json` de ERDDAP (tabla con `columnNames` y `rows`). Lógica pura, probada en JVM. */
object ErddapKd490Parser {
    const val SOURCE_NAME = "NOAA CoastWatch · VIIRS (S-NPP) Kd490 diario, 4 km"

    /**
     * Devuelve la observación válida MÁS RECIENTE de la respuesta, o `null` si no hay ninguna (nubes, tierra,
     * valores de relleno). Lanza [IllegalArgumentException] si el formato no es el esperado.
     */
    fun latest(body: String, json: Json = Json { ignoreUnknownKeys = true }): WaterClarityObservation? {
        val tableOrNull = try {
            json.parseToJsonElement(body).jsonObject["table"]?.jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Respuesta ERDDAP ilegible", e)
        }
        val table = tableOrNull ?: throw IllegalArgumentException("Respuesta ERDDAP sin tabla")
        val names = (table["columnNames"] as? JsonArray)?.map { it.jsonPrimitive.content }
            ?: throw IllegalArgumentException("Respuesta ERDDAP sin columnas")
        val rows = table["rows"] as? JsonArray ?: throw IllegalArgumentException("Respuesta ERDDAP sin filas")
        val timeIdx = names.indexOf("time")
        val kdIdx = names.indexOf("kd_490")
        require(timeIdx >= 0 && kdIdx >= 0) { "Faltan las columnas time/kd_490" }

        return rows.mapNotNull { row ->
            val cells = row.jsonArray
            val kd = (cells.getOrNull(kdIdx) as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }
                ?.doubleOrNull?.takeIf { it.isFinite() }
            val time = (cells.getOrNull(timeIdx) as? JsonPrimitive)?.content
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (kd == null || time == null) null else WaterClarityObservation(kd, time, SOURCE_NAME)
        }.maxByOrNull { it.observedAt }
    }
}

/**
 * Obtiene Kd490 del producto diario VIIRS de NOAA CoastWatch vía ERDDAP (sin clave). Consulta los últimos 7
 * días del píxel más cercano y se queda con la observación válida más reciente.
 * Guarda en memoria la última observación por posición (~1 km) durante 6 h; si la red falla, devuelve la
 * última guardada marcándola como no refrescada (nunca como actual).
 */
class ErddapClarityRepository(
    private val client: OkHttpClient,
    private val clock: Clock = Clock.systemUTC(),
    private val baseUrl: String = "https://coastwatch.pfeg.noaa.gov/erddap/griddap/",
    private val datasetId: String = DATASET_ID,
    private val freshFor: Duration = Duration.ofHours(6)
) : WaterClarityRepository {

    private data class Entry(val obs: WaterClarityObservation?, val fetchedAt: Instant)

    private val cache = ConcurrentHashMap<String, Entry>()

    override suspend fun latest(coordinates: Coordinates): ClarityFetch {
        val c = coordinates.coarse()
        val key = c.cacheKey()
        val now = clock.instant()
        val cached = cache[key]
        if (cached != null && Duration.between(cached.fetchedAt, now) < freshFor) return ClarityFetch(cached.obs)
        return try {
            val obs = withContext(Dispatchers.IO) { fetch(c) }
            cache[key] = Entry(obs, now)
            ClarityFetch(obs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ClarityFetch(cached?.obs, refreshFailed = true)
        }
    }

    private fun fetch(c: Coordinates): WaterClarityObservation? {
        val url = buildUrl(c).toHttpUrl()
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            // 404 = ERDDAP "sin resultados" (posición fuera de la malla): no hay observación, no es un fallo de red.
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return ErddapKd490Parser.latest(r.body?.string() ?: throw IOException("Respuesta vacía"))
        }
    }

    internal fun buildUrl(c: Coordinates): String {
        val lat = String.format(java.util.Locale.ROOT, "%.4f", c.latitude)
        val lon = String.format(java.util.Locale.ROOT, "%.4f", c.longitude)
        return "${baseUrl}${datasetId}.json?kd_490[last-6:1:last][(0.0)][($lat):1:($lat)][($lon):1:($lon)]"
    }

    companion object {
        const val DATASET_ID = "nesdisVHNSQkd490Daily"
    }
}
