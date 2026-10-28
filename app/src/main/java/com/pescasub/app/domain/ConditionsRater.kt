package com.pescasub.app.domain

enum class Rating { GOOD, FAIR, POOR, UNKNOWN }

enum class RatingFactor {
    WAVES_CALM, WAVES_MODERATE, WAVES_HIGH,
    WIND_CALM, WIND_MODERATE, WIND_STRONG,
    GUSTS_STRONG,
    WAVES_MISSING, WIND_MISSING
}

data class Assessment(val rating: Rating, val factors: List<RatingFactor>)

/**
 * Valoración orientativa de las condiciones para pesca submarina/apnea a partir de oleaje y viento.
 *
 * Es una heurística PROPIA y sencilla (umbrales conservadores), no un algoritmo de Crystal ni una
 * garantía de seguridad. Si falta oleaje o viento nunca se devuelve GOOD: lo que no se puede
 * comprobar no se da por bueno.
 */
object ConditionsRater {
    const val WAVE_GOOD_MAX_M = 0.5
    const val WAVE_FAIR_MAX_M = 1.0
    const val WIND_GOOD_MAX_KMH = 15.0
    const val WIND_FAIR_MAX_KMH = 25.0
    const val GUSTS_POOR_KMH = 40.0

    fun assess(c: HourlyConditions): Assessment {
        val wave = c.waveHeightM
        val wind = c.windSpeedKmh
        val factors = mutableListOf<RatingFactor>()
        if (wave == null) factors += RatingFactor.WAVES_MISSING
        if (wind == null) factors += RatingFactor.WIND_MISSING
        if (wave == null && wind == null) return Assessment(Rating.UNKNOWN, factors)

        var score = 0 // 0 = bueno, 1 = aceptable, 2 = desfavorable
        if (wave != null) {
            when {
                wave <= WAVE_GOOD_MAX_M -> factors += RatingFactor.WAVES_CALM
                wave <= WAVE_FAIR_MAX_M -> {
                    factors += RatingFactor.WAVES_MODERATE
                    score = maxOf(score, 1)
                }
                else -> {
                    factors += RatingFactor.WAVES_HIGH
                    score = 2
                }
            }
        }
        if (wind != null) {
            when {
                wind <= WIND_GOOD_MAX_KMH -> factors += RatingFactor.WIND_CALM
                wind <= WIND_FAIR_MAX_KMH -> {
                    factors += RatingFactor.WIND_MODERATE
                    score = maxOf(score, 1)
                }
                else -> {
                    factors += RatingFactor.WIND_STRONG
                    score = 2
                }
            }
        }
        val gusts = c.windGustsKmh
        if (gusts != null && gusts > GUSTS_POOR_KMH) {
            factors += RatingFactor.GUSTS_STRONG
            score = 2
        }

        var rating = when (score) {
            0 -> Rating.GOOD
            1 -> Rating.FAIR
            else -> Rating.POOR
        }
        if (rating == Rating.GOOD && (wave == null || wind == null)) rating = Rating.FAIR
        return Assessment(rating, factors)
    }
}
