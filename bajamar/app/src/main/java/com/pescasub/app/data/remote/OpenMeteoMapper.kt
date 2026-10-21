package com.pescasub.app.data.remote

import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.HourlyConditions
import com.pescasub.app.domain.TideCalculator
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Convierte las respuestas de Open-Meteo en el modelo de dominio. Lógica pura, probada en JVM. */
object OpenMeteoMapper {
    const val NOTE_NO_MARINE =
        "Sin datos de oleaje para esta posición (el servicio no respondió o no cubre este punto)."
    const val NOTE_NO_TIDES = "Sin datos de marea para esta posición."

    fun toBundle(
        payload: CachedPayload,
        coordinates: Coordinates,
        fetchedAt: Instant,
        sourceName: String
    ): ForecastBundle {
        val w = payload.weather.hourly
            ?: throw IllegalArgumentException("La respuesta no contiene datos horarios")
        val marine = payload.marine?.hourly
        val tides = payload.tides?.hourly
        val marineIndex = indexByTime(marine?.time)
        val tideIndex = indexByTime(tides?.time)

        val hourly = w.time.mapIndexed { i, t ->
            val mi = marineIndex[t]
            val ti = tideIndex[t]
            HourlyConditions(
                time = Instant.ofEpochSecond(t),
                airTempC = w.temperature2m.valueAt(i),
                windSpeedKmh = w.windSpeed10m.valueAt(i),
                windGustsKmh = w.windGusts10m.valueAt(i),
                windDirectionDeg = w.windDirection10m.valueAt(i),
                waveHeightM = marine?.waveHeight?.valueAt(mi),
                waveDirectionDeg = marine?.waveDirection?.valueAt(mi),
                wavePeriodS = marine?.wavePeriod?.valueAt(mi),
                swellHeightM = marine?.swellWaveHeight?.valueAt(mi),
                swellDirectionDeg = marine?.swellWaveDirection?.valueAt(mi),
                swellPeriodS = marine?.swellWavePeriod?.valueAt(mi),
                seaTempC = marine?.seaSurfaceTemperature?.valueAt(mi),
                seaLevelM = tides?.seaLevelHeightMsl?.valueAt(ti)
            )
        }

        val hasMarine = hourly.any { it.waveHeightM != null }
        val tideSeries = hourly.mapNotNull { h -> h.seaLevelM?.let { h.time to it } }
        val tideExtremes = TideCalculator.extremes(tideSeries)

        val notes = mutableListOf<String>()
        if (!hasMarine) notes += NOTE_NO_MARINE
        if (tideExtremes.isEmpty()) notes += NOTE_NO_TIDES

        return ForecastBundle(
            coordinates = coordinates,
            zone = zoneOf(payload.weather),
            fetchedAt = fetchedAt,
            sourceName = sourceName,
            hourly = hourly,
            tides = tideExtremes,
            hasMarineData = hasMarine,
            notes = notes
        )
    }

    private fun indexByTime(times: List<Long>?): Map<Long, Int> =
        times?.withIndex()?.associate { it.value to it.index } ?: emptyMap()

    private fun zoneOf(response: OpenMeteoResponse): ZoneId {
        response.timezone?.let { name ->
            try {
                return ZoneId.of(name)
            } catch (e: DateTimeException) {
                // se intenta con el desfase
            }
        }
        response.utcOffsetSeconds?.let { seconds ->
            try {
                return ZoneOffset.ofTotalSeconds(seconds)
            } catch (e: DateTimeException) {
                // se usa UTC
            }
        }
        return ZoneOffset.UTC
    }
}

private fun List<Double?>.valueAt(index: Int?): Double? = if (index == null) null else getOrNull(index)
