package com.pescasub.app.data.remote

import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.WeatherProvider
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * Proveedor basado en Open-Meteo (https://open-meteo.com). Uso gratuito no comercial, sin clave de
 * API, con atribución CC BY 4.0 y límites de uso razonable: la app cachea 30 min por posición y
 * solo envía coordenadas redondeadas a ~1 km.
 *
 * El nivel del mar (mareas) y la temperatura del agua no están garantizados en todas las posiciones:
 * si el servicio no responde o devuelve nulos, el dato figura como "sin datos" y se añade una nota.
 */
class OpenMeteoProvider(
    private val forecastApi: OpenMeteoForecastApi,
    private val marineApi: OpenMeteoMarineApi,
    private val json: Json
) : WeatherProvider {
    override val id = "open-meteo"
    override val sourceName = "Open-Meteo"
    override val attribution = "Datos meteorológicos y marinos: Open-Meteo.com (CC BY 4.0)"

    override suspend fun fetchRaw(coordinates: Coordinates): String = coroutineScope {
        val c = coordinates.coarse()
        val weather = async { forecastApi.forecast(c.latitude, c.longitude, WEATHER_VARS) }
        val marine = async { tryOrNull { marineApi.marine(c.latitude, c.longitude, MARINE_VARS) } }
        val tides = async { tryOrNull { marineApi.marine(c.latitude, c.longitude, TIDE_VARS) } }
        val payload = CachedPayload(weather = weather.await(), marine = marine.await(), tides = tides.await())
        json.encodeToString(CachedPayload.serializer(), payload)
    }

    override fun parse(raw: String, coordinates: Coordinates, fetchedAt: Instant): ForecastBundle {
        val payload = json.decodeFromString(CachedPayload.serializer(), raw)
        return OpenMeteoMapper.toBundle(payload, coordinates.coarse(), fetchedAt, sourceName)
    }

    private suspend fun <T> tryOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        const val WEATHER_VARS = "temperature_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m"
        const val MARINE_VARS = "wave_height,wave_direction,wave_period," +
            "swell_wave_height,swell_wave_direction,swell_wave_period,sea_surface_temperature"
        const val TIDE_VARS = "sea_level_height_msl"
    }
}
