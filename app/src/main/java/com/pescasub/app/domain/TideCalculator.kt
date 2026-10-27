package com.pescasub.app.domain

import java.time.Instant

/**
 * Obtiene pleamares y bajamares a partir de una serie de nivel del mar con paso regular.
 *
 * Busca máximos/mínimos locales y afina hora y altura con una parábola por tres puntos, de modo que
 * la precisión temporal es mejor que el paso de la serie. Son estimaciones del modelo, no tablas
 * oficiales de mareas.
 */
object TideCalculator {
    /** Diferencia mínima respecto a los vecinos para descartar ruido (en metros). */
    const val MIN_DELTA_M = 0.01

    fun extremes(series: List<Pair<Instant, Double>>): List<TideExtreme> {
        val result = mutableListOf<TideExtreme>()
        for (i in 1 until series.size - 1) {
            val (t0, y0) = series[i - 1]
            val (t1, y1) = series[i]
            val (t2, y2) = series[i + 1]
            val step = t1.epochSecond - t0.epochSecond
            if (step <= 0 || t2.epochSecond - t1.epochSecond != step) continue // serie no regular

            val isHigh = y1 >= y0 && y1 > y2
            val isLow = y1 <= y0 && y1 < y2
            if (!isHigh && !isLow) continue
            if (Math.abs(y1 - y0) + Math.abs(y1 - y2) < MIN_DELTA_M) continue

            var offsetSteps = 0.0
            var height = y1
            val denominator = y0 - 2 * y1 + y2
            if (denominator != 0.0) {
                val candidate = 0.5 * (y0 - y2) / denominator
                if (candidate in -1.0..1.0) {
                    offsetSteps = candidate
                    height = y1 - 0.25 * (y0 - y2) * candidate
                }
            }
            val time = t1.plusMillis(Math.round(offsetSteps * step * 1000.0))
            result += TideExtreme(time, if (isHigh) TideType.HIGH else TideType.LOW, height)
        }
        return result
    }
}
