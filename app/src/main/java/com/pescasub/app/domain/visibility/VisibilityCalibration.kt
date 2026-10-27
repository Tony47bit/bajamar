package com.pescasub.app.domain.visibility

import com.pescasub.app.domain.Coordinates
import java.time.Duration
import java.time.Instant

/**
 * Una anotación del usuario: la visibilidad que VIO en un punto y lo que el modelo estimaba (cifra central, SIN
 * calibrar) en el momento de guardar la jornada.
 */
data class CalibrationSample(val observedM: Int, val estimatedM: Int, val at: Instant)

/** Factor multiplicativo que se aplica a la estimación de UN punto y nº de anotaciones en que se basa. */
data class VisibilityCalibration(val factor: Double, val samples: Int)

/**
 * Ajusta la estimación de un punto con lo que el usuario ha visto allí. Para cada anotación se calcula
 * `observada / estimada` y el factor es la MEDIANA (robusta frente a un día raro).
 *  - Hacen falta al menos [MIN_SAMPLES] anotaciones de los últimos [MAX_AGE_DAYS] días.
 *  - El factor se limita a [MIN_FACTOR]..[MAX_FACTOR] para que un par de datos extraños no desvirtúen el resultado.
 * Es un ajuste empírico simple (no un modelo estadístico): refleja lo que el usuario suele ver respecto a lo estimado.
 */
object VisibilityCalibrator {
    const val MIN_SAMPLES = 3
    const val MAX_AGE_DAYS = 365L
    const val MIN_FACTOR = 0.4
    const val MAX_FACTOR = 2.5
    const val MIN_OBSERVED_M = 1
    const val MAX_OBSERVED_M = 60

    fun calibrate(samples: List<CalibrationSample>, now: Instant): VisibilityCalibration? {
        val oldest = now.minus(Duration.ofDays(MAX_AGE_DAYS))
        val ratios = samples
            .filter {
                it.observedM in MIN_OBSERVED_M..MAX_OBSERVED_M && it.estimatedM >= 1 &&
                    !it.at.isBefore(oldest) && !it.at.isAfter(now.plus(Duration.ofDays(1)))
            }
            .map { it.observedM.toDouble() / it.estimatedM }
        if (ratios.size < MIN_SAMPLES) return null
        val sorted = ratios.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        return VisibilityCalibration(median.coerceIn(MIN_FACTOR, MAX_FACTOR), ratios.size)
    }
}

/**
 * Cifra central (metros) que estima el modelo SIN calibrar para un punto ahora mismo, o `null` si no hay una
 * estimación en metros. Se usa al guardar una jornada para emparejarla con lo que el usuario vio.
 */
interface VisibilityEstimateSource {
    suspend fun centralMeters(coordinates: Coordinates): Int?
}

object NoVisibilityEstimates : VisibilityEstimateSource {
    override suspend fun centralMeters(coordinates: Coordinates): Int? = null
}
