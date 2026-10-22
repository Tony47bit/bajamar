package com.pescasub.app.domain.visibility

import com.pescasub.app.domain.ConditionsRater
import com.pescasub.app.domain.HourlyConditions
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Validación de la observación de entrada (separada del cálculo). */
object VisibilityValidator {
    /** Rango válido de `kd_490` según los metadatos del producto NOAA (0,01–6,0 1/m). */
    const val KD_MIN = 0.01
    const val KD_MAX = 6.0

    /** Devuelve el motivo por el que la observación no sirve, o `null` si es utilizable. */
    fun problem(obs: WaterClarityObservation?, now: Instant): NotEstimableReason? = when {
        obs == null -> NotEstimableReason.NO_OBSERVATION
        !obs.kd490PerM.isFinite() || obs.kd490PerM !in KD_MIN..KD_MAX -> NotEstimableReason.INVALID_OBSERVATION
        obs.observedAt.isAfter(now.plus(Duration.ofHours(24))) -> NotEstimableReason.INVALID_OBSERVATION
        Duration.between(obs.observedAt, now) > VisibilityEstimator.MAX_AGE -> NotEstimableReason.OBSERVATION_TOO_OLD
        else -> null
    }
}

/**
 * Estimador de visibilidad submarina. Lógica pura (sin Android ni Compose).
 *
 * MODELO (ver docs/VISIBILIDAD_SUBMARINA.md para fuentes y limitaciones):
 *  1. Ancla: Kd490 de satélite → profundidad de disco de Secchi, `Zsd ≈ 1,7 / Kd` (relación de Poole y Atkins, 1929,
 *     que usa Kd de la luz visible; Kd490 es solo un indicador cercano, lo que añade incertidumbre).
 *  2. La visibilidad horizontal de un buceador NO es la profundidad de Secchi, pero es del mismo orden de magnitud.
 *     Por eso se devuelve un RANGO. Su amplitud (0,5×–1,5× Zsd) es una DECISIÓN DE DISEÑO, no una incertidumbre
 *     calibrada con mediciones.
 *  3. Oleaje y lluvia no se convierten en metros (no hay calibración que lo respalde): solo pueden recortar el
 *     límite superior a 1,0× Zsd y rebajar la confianza. Nunca aumentan la estimación.
 *  4. Confianza: como máximo MEDIA; nunca ALTA, porque el producto no está validado en aguas costeras someras.
 *
 * Los umbrales de oleaje reutilizan los de la valoración de condiciones; el de lluvia (20 mm en 48 h) es una
 * regla práctica de diseño, no un valor calibrado.
 */
object VisibilityEstimator {
    const val SECCHI_FACTOR = 1.7
    const val RANGE_LOW = 0.5
    const val RANGE_HIGH = 1.5
    /** Observaciones más antiguas que esto se consideran no representativas (cobertura de nubes frecuente). */
    val MAX_AGE: Duration = Duration.ofDays(7)
    const val RECENT_MAX_AGE_DAYS = 2L
    const val RAIN_NOTABLE_MM_48H = 20.0
    val ROUGH_WAVE_M = ConditionsRater.WAVE_FAIR_MAX_M
    private val LOOKAHEAD: Duration = Duration.ofHours(24)
    private val RAIN_WINDOW: Duration = Duration.ofHours(48)

    /**
     * @param recent horas pasadas (contexto) y @param upcoming horas de hoy/mañana, ambas del mismo punto.
     * @param refreshFailed la actualización de la observación falló y `obs` es la última guardada.
     */
    fun estimate(
        obs: WaterClarityObservation?,
        recent: List<HourlyConditions>,
        upcoming: List<HourlyConditions>,
        now: Instant,
        refreshFailed: Boolean = false
    ): VisibilityEstimate {
        VisibilityValidator.problem(obs, now)?.let {
            return VisibilityEstimate.NotEstimable(it, obs?.observedAt)
        }
        obs!!
        val all = recent + upcoming
        val factors = linkedSetOf(VisibilityFactor.COARSE_SATELLITE)
        val age = Duration.between(obs.observedAt, now)
        if (age > Duration.ofDays(RECENT_MAX_AGE_DAYS)) factors += VisibilityFactor.OBSERVATION_AGED
        if (refreshFailed) factors += VisibilityFactor.REFRESH_FAILED

        // Estado del mar desde la observación hasta dentro de 24 h.
        val seaWindow = all.filter { !it.time.isBefore(obs.observedAt) && !it.time.isAfter(now.plus(LOOKAHEAD)) }
        val waves = seaWindow.mapNotNull { it.waveHeightM }
        val waveMissing = waves.isEmpty()
        if (waveMissing) factors += VisibilityFactor.WAVE_DATA_MISSING
        val rough = waves.any { it > ROUGH_WAVE_M }
        if (rough) factors += VisibilityFactor.ROUGH_SEA

        // Lluvia de las últimas 48 h.
        val rainHours = all.filter { !it.time.isBefore(now.minus(RAIN_WINDOW)) && !it.time.isAfter(now) }
            .mapNotNull { it.precipitationMm }
        val rainMissing = rainHours.isEmpty()
        if (rainMissing) factors += VisibilityFactor.RAIN_DATA_MISSING
        val rainy = rainHours.sum() >= RAIN_NOTABLE_MM_48H
        if (rainy) factors += VisibilityFactor.RECENT_RAIN

        val secchi = SECCHI_FACTOR / obs.kd490PerM
        val capped = rough || rainy
        val (lo, hi) = roundRange(secchi * RANGE_LOW, secchi * if (capped) 1.0 else RANGE_HIGH)

        val medium = age <= Duration.ofDays(RECENT_MAX_AGE_DAYS) && !refreshFailed && !capped &&
            !waveMissing && !rainMissing
        return VisibilityEstimate.Estimated(
            minMeters = lo,
            maxMeters = hi,
            confidence = if (medium) VisibilityConfidence.MEDIUM else VisibilityConfidence.LOW,
            factors = factors,
            observedAt = obs.observedAt,
            sourceName = obs.sourceName,
            upperCapped = capped
        )
    }

    /** Redondeo sin falsa precisión: a metros enteros por debajo de 10 m y a múltiplos de 5 m por encima. */
    internal fun roundRange(low: Double, high: Double): Pair<Int, Int> {
        val lo = roundMeters(low).coerceAtLeast(1)
        var hi = roundMeters(high).coerceAtLeast(1)
        if (hi <= lo) hi = if (lo < 10) lo + 1 else lo + 5
        return lo to hi
    }

    private fun roundMeters(v: Double): Int =
        if (v < 10.0) v.roundToInt() else ((v / 5.0).roundToInt() * 5)
}
