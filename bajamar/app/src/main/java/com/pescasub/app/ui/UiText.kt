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

const val NO_VISIBILITY_TEXT =
    "Visibilidad submarina: no disponible. Ninguna de las fuentes de datos configuradas la ofrece, " +
        "así que la app no la estima."

const val RATING_DISCLAIMER =
    "Valoración orientativa basada solo en oleaje y viento previstos. No garantiza la seguridad: " +
        "revisa siempre las condiciones reales y no te sumerjas solo."
