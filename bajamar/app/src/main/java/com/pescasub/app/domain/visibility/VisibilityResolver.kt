package com.pescasub.app.domain.visibility

import com.pescasub.app.domain.HourlyConditions
import java.time.Instant

/**
 * Elige la MEJOR capa disponible y nunca presenta una capa inferior como si fuera la superior:
 *  1. [VisibilityEstimate.Estimated]: observación de satélite reciente (≤ 7 días).
 *  2. [VisibilityEstimate.Climatological]: valor típico de la zona en esta época del año (no es el de hoy).
 *  3. [VisibilityEstimate.SeaStateOnly]: solo una señal cualitativa del estado del mar, SIN metros.
 *  4. [VisibilityEstimate.NotEstimable]: ni siquiera hay datos de mar ni de lluvia.
 * Lógica pura. Ver docs/VISIBILIDAD_SUBMARINA.md.
 */
object VisibilityResolver {
    suspend fun resolve(
        fetch: ClarityFetch,
        climatology: suspend () -> ClimatologyObservation?,
        recent: List<HourlyConditions>,
        upcoming: List<HourlyConditions>,
        now: Instant
    ): VisibilityEstimate {
        val primary = VisibilityEstimator.estimate(
            fetch.observation, recent, upcoming, now, refreshFailed = fetch.refreshFailed
        )
        if (primary is VisibilityEstimate.Estimated) return primary

        val all = recent + upcoming
        val sea = VisibilityEstimator.analyzeSea(all, from = now.minusSeconds(SEA_LOOKBACK_S), now = now)

        val clim = climatology()?.takeIf {
            it.kd490PerM.isFinite() && it.kd490PerM in VisibilityValidator.KD_MIN..VisibilityValidator.KD_MAX &&
                it.samples >= 1
        }
        if (clim != null) {
            val secchi = VisibilityEstimator.SECCHI_FACTOR / clim.kd490PerM
            val capped = sea.rough || sea.rainy
            val (lo, hi) = VisibilityEstimator.roundRange(
                secchi * VisibilityEstimator.RANGE_LOW,
                secchi * if (capped) 1.0 else VisibilityEstimator.RANGE_HIGH
            )
            return VisibilityEstimate.Climatological(
                minMeters = lo,
                maxMeters = hi,
                samples = clim.samples,
                factors = linkedSetOf(VisibilityFactor.COARSE_SATELLITE) + sea.factors,
                sourceName = clim.sourceName,
                upperCapped = capped
            )
        }

        // Capa 3: hace falta al menos un dato de mar o de lluvia; sin ninguno, no se dice nada.
        if (sea.waveMissing && sea.rainMissing) return primary
        val outlook = if (sea.rough || sea.rainy) SeaOutlook.WORSE_THAN_USUAL else SeaOutlook.NO_WARNING_SIGNS
        return VisibilityEstimate.SeaStateOnly(outlook, sea.factors)
    }

    /** Ventana hacia atrás para el estado del mar cuando no hay fecha de observación: 48 h. */
    private const val SEA_LOOKBACK_S = 48L * 3600L
}
