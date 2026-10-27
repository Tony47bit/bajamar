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
 *     Por eso se devuelve una BANDA estrecha (±15 % de Zsd) pensada para leerse bien; es una DECISIÓN DE
 *     DISEÑO, no una incertidumbre calibrada con mediciones (la real es mayor).
 *  3. Oleaje y lluvia no se convierten en metros (no hay calibración que lo respalde): solo desplazan la banda hacia
 *     abajo (0,65–0,95× Zsd) y rebajan la confianza. Nunca aumentan la estimación.
 *  4. Confianza: como máximo MEDIA; nunca ALTA, porque el producto no está validado en aguas costeras someras.
 *
 * Los umbrales de oleaje reutilizan los de la valoración de condiciones; el de lluvia (20 mm en 48 h) es una
 * regla práctica de diseño, no un valor calibrado.
 */
object VisibilityEstimator {
    const val SECCHI_FACTOR = 1.7
    /**
     * Banda mostrada alrededor de la cifra central (1,0 × Zsd): ±15 %. Es una BANDA DE PRESENTACIÓN estrecha para que
     * el dato sea legible (p. ej. «6–8 m»), NO un intervalo de confianza: la incertidumbre real es mayor y así se
     * dice en la interfaz. Con mar agitado o lluvia la banda se desplaza hacia abajo (valor real tiende a ser menor).
     */
    const val RANGE_LOW = 0.85
    const val RANGE_HIGH = 1.15
    const val RANGE_LOW_CAPPED = 0.65
    const val RANGE_HIGH_CAPPED = 0.95
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
        refreshFailed: Boolean = false,
        calibration: VisibilityCalibration? = null
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

        val sea = analyzeSea(all, from = obs.observedAt, now = now)
        factors += sea.factors
        val waveMissing = sea.waveMissing
        val rainMissing = sea.rainMissing
        val rough = sea.rough
        val rainy = sea.rainy

        val secchi = SECCHI_FACTOR / obs.kd490PerM * (calibration?.factor ?: 1.0)
        val capped = rough || rainy
        val (lo, hi) = rangeFor(secchi, capped)

        val medium = age <= Duration.ofDays(RECENT_MAX_AGE_DAYS) && !refreshFailed && !capped &&
            !waveMissing && !rainMissing
        return VisibilityEstimate.Estimated(
            minMeters = lo,
            maxMeters = hi,
            confidence = if (medium) VisibilityConfidence.MEDIUM else VisibilityConfidence.LOW,
            factors = factors,
            observedAt = obs.observedAt,
            sourceName = obs.sourceName,
            upperCapped = capped,
            calibratedSamples = calibration?.samples ?: 0
        )
    }

    /** Estado del mar y lluvia en la ventana `from`→+24 h (oleaje) y últimas 48 h (lluvia). */
    internal data class SeaContext(
        val rough: Boolean,
        val rainy: Boolean,
        val waveMissing: Boolean,
        val rainMissing: Boolean
    ) {
        val factors: Set<VisibilityFactor>
            get() = buildSet {
                if (waveMissing) add(VisibilityFactor.WAVE_DATA_MISSING)
                if (rough) add(VisibilityFactor.ROUGH_SEA)
                if (rainMissing) add(VisibilityFactor.RAIN_DATA_MISSING)
                if (rainy) add(VisibilityFactor.RECENT_RAIN)
            }
    }

    internal fun analyzeSea(all: List<HourlyConditions>, from: Instant, now: Instant): SeaContext {
        val waves = all.filter { !it.time.isBefore(from) && !it.time.isAfter(now.plus(LOOKAHEAD)) }
            .mapNotNull { it.waveHeightM }
        val rain = all.filter { !it.time.isBefore(now.minus(RAIN_WINDOW)) && !it.time.isAfter(now) }
            .mapNotNull { it.precipitationMm }
        return SeaContext(
            rough = waves.any { it > ROUGH_WAVE_M },
            rainy = rain.sum() >= RAIN_NOTABLE_MM_48H,
            waveMissing = waves.isEmpty(),
            rainMissing = rain.isEmpty()
        )
    }

    /** Banda en metros para una profundidad de Secchi dada; `capped` = mar agitado o lluvia reciente. */
    internal fun rangeFor(secchiM: Double, capped: Boolean): Pair<Int, Int> =
        if (capped) roundRange(secchiM * RANGE_LOW_CAPPED, secchiM * RANGE_HIGH_CAPPED)
        else roundRange(secchiM * RANGE_LOW, secchiM * RANGE_HIGH)

    /** Redondeo sin falsa precisión: a metros enteros hasta 20 m y a múltiplos de 5 m por encima. */
    internal fun roundRange(low: Double, high: Double): Pair<Int, Int> {
        val lo = roundMeters(low).coerceAtLeast(1)
        var hi = roundMeters(high).coerceAtLeast(1)
        if (hi <= lo) hi = if (lo < 20) lo + 1 else lo + 5
        return lo to hi
    }

    private fun roundMeters(v: Double): Int =
        if (v < 20.0) v.roundToInt() else ((v / 5.0).roundToInt() * 5)
}
