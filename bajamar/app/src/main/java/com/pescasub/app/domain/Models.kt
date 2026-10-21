package com.pescasub.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Par de coordenadas WGS84 validado. */
data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Latitud fuera de rango" }
        require(longitude in -180.0..180.0) { "Longitud fuera de rango" }
    }

    /**
     * Coordenadas redondeadas a 2 decimales (~1 km). Es lo único que se envía a servicios
     * externos, para no revelar la posición exacta de un punto de pesca.
     */
    fun coarse(): Coordinates = Coordinates(round2(latitude), round2(longitude))

    fun cacheKey(): String = String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude)

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}

/** Ubicación elegida para consultar condiciones (puede ser una guardada o una temporal). */
data class Spot(
    val name: String,
    val coordinates: Coordinates,
    val locationId: Long? = null
)

data class Place(
    val name: String,
    val region: String?,
    val coordinates: Coordinates
)

/** Valores de una hora. `null` significa que la fuente no proporciona el dato. */
data class HourlyConditions(
    val time: Instant,
    val airTempC: Double? = null,
    val windSpeedKmh: Double? = null,
    val windGustsKmh: Double? = null,
    val windDirectionDeg: Double? = null,
    val waveHeightM: Double? = null,
    val waveDirectionDeg: Double? = null,
    val wavePeriodS: Double? = null,
    val swellHeightM: Double? = null,
    val swellDirectionDeg: Double? = null,
    val swellPeriodS: Double? = null,
    val seaTempC: Double? = null,
    val seaLevelM: Double? = null
)

enum class TideType { HIGH, LOW }

data class TideExtreme(val time: Instant, val type: TideType, val heightM: Double)

data class DailySummary(
    val date: LocalDate,
    val tempMinC: Double?,
    val tempMaxC: Double?,
    val windMaxKmh: Double?,
    val waveMaxM: Double?
)

data class ForecastBundle(
    /** Coordenadas (redondeadas) realmente consultadas. */
    val coordinates: Coordinates,
    val zone: ZoneId,
    val fetchedAt: Instant,
    val sourceName: String,
    val hourly: List<HourlyConditions>,
    val tides: List<TideExtreme>,
    val hasMarineData: Boolean,
    val notes: List<String>,
    val fromCache: Boolean = false
) {
    fun localDate(time: Instant): LocalDate = time.atZone(zone).toLocalDate()

    fun dates(): List<LocalDate> = hourly.map { localDate(it.time) }.distinct()

    fun hoursOf(date: LocalDate): List<HourlyConditions> = hourly.filter { localDate(it.time) == date }

    fun tidesOf(date: LocalDate): List<TideExtreme> = tides.filter { localDate(it.time) == date }

    fun daily(): List<DailySummary> = dates().map { date ->
        val hours = hoursOf(date)
        DailySummary(
            date = date,
            tempMinC = hours.mapNotNull { it.airTempC }.minOrNull(),
            tempMaxC = hours.mapNotNull { it.airTempC }.maxOrNull(),
            windMaxKmh = hours.mapNotNull { it.windSpeedKmh }.maxOrNull(),
            waveMaxM = hours.mapNotNull { it.waveHeightM }.maxOrNull()
        )
    }

    /** Hora vigente en `now`: la última cuyo instante no es posterior; si no hay, la primera. */
    fun at(now: Instant): HourlyConditions? =
        hourly.lastOrNull { !it.time.isAfter(now) } ?: hourly.firstOrNull()
}
