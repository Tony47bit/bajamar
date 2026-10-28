package com.pescasub.app.data

import com.pescasub.app.data.remote.CachedPayload
import com.pescasub.app.data.remote.GeocodingResponse
import com.pescasub.app.data.remote.OpenMeteoMapper
import com.pescasub.app.data.remote.OpenMeteoResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba el parseo con kotlinx.serialization real. Los JSON son EJEMPLOS escritos a mano con la
 * estructura documentada de Open-Meteo (no son respuestas reales ni datos meteorológicos reales).
 */
class OpenMeteoJsonTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val weatherJson = """
        {
          "latitude": 36.72, "longitude": -4.42, "generationtime_ms": 0.1,
          "utc_offset_seconds": 7200, "timezone": "Europe/Madrid", "timezone_abbreviation": "CEST",
          "hourly_units": {"time": "unixtime", "temperature_2m": "°C"},
          "hourly": {
            "time": [1792447200, 1792450800, 1792454400],
            "temperature_2m": [20.1, 19.8, null],
            "wind_speed_10m": [8.0, 9.5, 11.0],
            "wind_gusts_10m": [15.0, 17.5, 20.0],
            "wind_direction_10m": [40, 45, 50]
          }
        }
    """.trimIndent()

    private val marineJson = """
        {
          "timezone": "Europe/Madrid",
          "hourly": {
            "time": [1792447200, 1792450800, 1792454400],
            "wave_height": [0.4, null, 0.5],
            "wave_period": [5.0, 5.2, 5.4]
          }
        }
    """.trimIndent()

    @Test
    fun parsesWeatherWithNullsAndUnknownKeys() {
        val r = json.decodeFromString(OpenMeteoResponse.serializer(), weatherJson)
        assertEquals("Europe/Madrid", r.timezone)
        assertEquals(3, r.hourly!!.time.size)
        assertNull(r.hourly!!.temperature2m[2])
        assertEquals(45.0, r.hourly!!.windDirection10m[1]!!, 0.0) // un entero JSON llega como Double
        assertTrue(r.hourly!!.waveHeight.isEmpty()) // variable no solicitada → lista vacía
    }

    @Test
    fun payloadRoundTripsThroughTheCacheFormat() {
        val payload = CachedPayload(
            weather = json.decodeFromString(OpenMeteoResponse.serializer(), weatherJson),
            marine = json.decodeFromString(OpenMeteoResponse.serializer(), marineJson),
            tides = null
        )
        val raw = json.encodeToString(CachedPayload.serializer(), payload)
        val back = json.decodeFromString(CachedPayload.serializer(), raw)
        assertEquals(payload, back)

        val bundle = OpenMeteoMapper.toBundle(back, com.pescasub.app.domain.Coordinates(36.72, -4.42), Instant.EPOCH, "Open-Meteo")
        assertEquals(3, bundle.hourly.size)
        assertEquals(0.4, bundle.hourly[0].waveHeightM!!, 0.0)
        assertNull(bundle.hourly[1].waveHeightM)
        assertEquals(5.4, bundle.hourly[2].wavePeriodS!!, 0.0)
    }

    @Test
    fun parsesGeocodingResultsAndEmptyResponse() {
        val ok = json.decodeFromString(
            GeocodingResponse.serializer(),
            """{"results":[{"id":1,"name":"Tarifa","latitude":36.01,"longitude":-5.6,"country":"España","admin1":"Andalucía"}]}"""
        )
        assertEquals("Tarifa", ok.results.single().name)
        assertEquals("Andalucía", ok.results.single().admin1)

        // Si no hay coincidencias la API omite "results".
        val empty = json.decodeFromString(GeocodingResponse.serializer(), """{"generationtime_ms":0.2}""")
        assertTrue(empty.results.isEmpty())
    }
}
