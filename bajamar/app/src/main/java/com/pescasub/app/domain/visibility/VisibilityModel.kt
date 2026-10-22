package com.pescasub.app.domain.visibility

import com.pescasub.app.domain.Coordinates
import java.time.Instant

/**
 * Observación de transparencia del agua obtenida de un producto de satélite (coeficiente de atenuación
 * difusa de la luz a 490 nm, Kd490, en 1/m). Es una OBSERVACIÓN ya pasada (no una previsión).
 */
data class WaterClarityObservation(
    val kd490PerM: Double,
    val observedAt: Instant,
    val sourceName: String
)

/** Resultado de pedir la observación más reciente: la propia observación y si se pudo refrescar ahora. */
data class ClarityFetch(
    val observation: WaterClarityObservation?,
    /** `true` si la consulta de red falló y se devuelve la última observación guardada (si la hay). */
    val refreshFailed: Boolean = false
)

/** Fuente de observaciones de transparencia del agua. Puede lanzar excepciones de red. */
interface WaterClarityRepository {
    suspend fun latest(coordinates: Coordinates): ClarityFetch
}

/** Implementación nula: sin fuente configurada (y base de los dobles de prueba). */
object NoWaterClarity : WaterClarityRepository {
    override suspend fun latest(coordinates: Coordinates) = ClarityFetch(null)
}

enum class VisibilityConfidence { LOW, MEDIUM }

/** Factores que han influido en el resultado o limitan su fiabilidad (cada uno se explica en la interfaz). */
enum class VisibilityFactor {
    /** Siempre: el píxel del satélite (~4 km) no resuelve la costa poco profunda. */
    COARSE_SATELLITE,
    /** La observación tiene más de [VisibilityEstimator.RECENT_MAX_AGE_DAYS] día(s). */
    OBSERVATION_AGED,
    /** No se pudo actualizar la observación: se usa la última guardada. */
    REFRESH_FAILED,
    /** Oleaje significativo desde la observación o previsto en las próximas 24 h. */
    ROUGH_SEA,
    /** Lluvia notable en las últimas 48 h (posible aporte de sedimentos). */
    RECENT_RAIN,
    /** Faltan datos de oleaje para comprobar el estado del mar. */
    WAVE_DATA_MISSING,
    /** Faltan datos de lluvia. */
    RAIN_DATA_MISSING
}

enum class NotEstimableReason {
    /** Ninguna observación (nubes, tierra, sin cobertura, sin conexión, producto detenido…). */
    NO_OBSERVATION,
    /** Hay observación, pero demasiado antigua para representar el agua actual. */
    OBSERVATION_TOO_OLD,
    /** El valor es inválido o incoherente (NaN, fuera del rango válido, fecha futura). */
    INVALID_OBSERVATION
}

sealed interface VisibilityEstimate {
    /**
     * Rango aproximado de visibilidad horizontal en metros.
     * @property upperCapped `true` si las condiciones sugieren que el valor real tiende a ser MENOR que el rango
     * central: el límite superior se recorta (la estimación nunca se "mejora" por el estado del mar).
     */
    data class Estimated(
        val minMeters: Int,
        val maxMeters: Int,
        val confidence: VisibilityConfidence,
        val factors: Set<VisibilityFactor>,
        val observedAt: Instant,
        val sourceName: String,
        val upperCapped: Boolean
    ) : VisibilityEstimate

    data class NotEstimable(
        val reason: NotEstimableReason,
        val observedAt: Instant? = null
    ) : VisibilityEstimate
}
