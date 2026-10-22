package com.pescasub.app.ui

import com.pescasub.app.domain.ConditionsRater
import com.pescasub.app.domain.ForecastError
import com.pescasub.app.domain.Rating
import com.pescasub.app.domain.RatingFactor

fun forecastErrorText(error: ForecastError): String = when (error) {
    ForecastError.NETWORK -> "Sin conexión o el servicio no responde."
    ForecastError.RATE_LIMITED -> "Se alcanzó el límite de consultas del servicio. Inténtalo más tarde."
    ForecastError.SERVER -> "El servicio meteorológico devolvió un error."
    ForecastError.PARSE -> "La respuesta del servicio no tiene el formato esperado."
    ForecastError.UNKNOWN -> "Ocurrió un error inesperado al obtener la previsión."
}

fun ratingLabel(rating: Rating): String = when (rating) {
    Rating.GOOD -> "Favorable"
    Rating.FAIR -> "Aceptable"
    Rating.POOR -> "Desfavorable"
    Rating.UNKNOWN -> "Sin datos suficientes"
}

fun factorText(factor: RatingFactor): String = when (factor) {
    RatingFactor.WAVES_CALM -> "Oleaje bajo (hasta ${Fmt.num(ConditionsRater.WAVE_GOOD_MAX_M)} m)"
    RatingFactor.WAVES_MODERATE -> "Oleaje moderado (hasta ${Fmt.num(ConditionsRater.WAVE_FAIR_MAX_M)} m)"
    RatingFactor.WAVES_HIGH -> "Oleaje alto (más de ${Fmt.num(ConditionsRater.WAVE_FAIR_MAX_M)} m)"
    RatingFactor.WIND_CALM -> "Viento flojo (hasta ${Fmt.num(ConditionsRater.WIND_GOOD_MAX_KMH, 0)} km/h)"
    RatingFactor.WIND_MODERATE -> "Viento moderado (hasta ${Fmt.num(ConditionsRater.WIND_FAIR_MAX_KMH, 0)} km/h)"
    RatingFactor.WIND_STRONG -> "Viento fuerte (más de ${Fmt.num(ConditionsRater.WIND_FAIR_MAX_KMH, 0)} km/h)"
    RatingFactor.GUSTS_STRONG -> "Rachas fuertes (más de ${Fmt.num(ConditionsRater.GUSTS_POOR_KMH, 0)} km/h)"
    RatingFactor.WAVES_MISSING -> "Sin datos de oleaje"
    RatingFactor.WIND_MISSING -> "Sin datos de viento"
}

enum class LocationFailure { DENIED, UNAVAILABLE }

fun locationFailureText(failure: LocationFailure): String = when (failure) {
    LocationFailure.DENIED -> "Sin permiso de ubicación. Puedes buscar un lugar o elegirlo en el mapa."
    LocationFailure.UNAVAILABLE -> "No se pudo obtener tu ubicación. Comprueba que el GPS está activado."
}

fun tideTypeText(type: com.pescasub.app.domain.TideType): String =
    if (type == com.pescasub.app.domain.TideType.HIGH) "Pleamar" else "Bajamar"

const val RATING_DISCLAIMER =
    "Valoración orientativa basada solo en oleaje y viento previstos. No garantiza la seguridad: " +
        "revisa siempre las condiciones reales y no te sumerjas solo."

// --- Visibilidad submarina estimada (texto puro; el algoritmo vive en domain/visibility) ---

const val VISIBILITY_TITLE = "Visibilidad submarina (estimación)"

const val VISIBILITY_DISCLAIMER =
    "Estimación a partir de un satélite con píxeles de unos 4 km, no una medición. La transparencia real del agua " +
        "puede variar mucho en tu zona, sobre todo cerca de la costa. Compruébala siempre in situ."

fun visibilityRangeText(e: com.pescasub.app.domain.visibility.VisibilityEstimate.Estimated): String {
    val range = "${e.minMeters}–${e.maxMeters} m"
    return if (e.upperCapped) "Hasta ~${e.maxMeters} m (rango $range)" else "Visibilidad estimada: $range"
}

fun visibilityConfidenceText(c: com.pescasub.app.domain.visibility.VisibilityConfidence): String = when (c) {
    com.pescasub.app.domain.visibility.VisibilityConfidence.LOW -> "Confianza: baja"
    com.pescasub.app.domain.visibility.VisibilityConfidence.MEDIUM -> "Confianza: media"
}

fun visibilityNotEstimableText(r: com.pescasub.app.domain.visibility.NotEstimableReason): String = when (r) {
    com.pescasub.app.domain.visibility.NotEstimableReason.NO_OBSERVATION ->
        "Visibilidad no estimable con los datos disponibles: no hay observación de satélite reciente para este " +
            "punto (nubes, zona sin cobertura o sin conexión)."
    com.pescasub.app.domain.visibility.NotEstimableReason.OBSERVATION_TOO_OLD ->
        "Visibilidad no estimable con los datos disponibles: la última observación de satélite es demasiado antigua."
    com.pescasub.app.domain.visibility.NotEstimableReason.INVALID_OBSERVATION ->
        "Visibilidad no estimable con los datos disponibles: el dato de satélite recibido no es coherente."
}

fun visibilityFactorText(f: com.pescasub.app.domain.visibility.VisibilityFactor): String = when (f) {
    com.pescasub.app.domain.visibility.VisibilityFactor.COARSE_SATELLITE ->
        "El satélite no resuelve bien la costa poco profunda."
    com.pescasub.app.domain.visibility.VisibilityFactor.OBSERVATION_AGED ->
        "La observación tiene más de 2 días."
    com.pescasub.app.domain.visibility.VisibilityFactor.REFRESH_FAILED ->
        "No se pudo actualizar: se usa la última observación guardada."
    com.pescasub.app.domain.visibility.VisibilityFactor.ROUGH_SEA ->
        "Oleaje de más de ${Fmt.num(com.pescasub.app.domain.visibility.VisibilityEstimator.ROUGH_WAVE_M)} m desde la " +
            "observación o previsto en 24 h: el agua puede estar más turbia; se recorta el máximo."
    com.pescasub.app.domain.visibility.VisibilityFactor.RECENT_RAIN ->
        "Lluvia notable en 48 h: posible aporte de sedimentos; se recorta el máximo."
    com.pescasub.app.domain.visibility.VisibilityFactor.WAVE_DATA_MISSING -> "Faltan datos de oleaje."
    com.pescasub.app.domain.visibility.VisibilityFactor.RAIN_DATA_MISSING -> "Faltan datos de lluvia."
}
