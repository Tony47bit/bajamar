package com.pescasub.app.data.remote

import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.Place
import com.pescasub.app.domain.PlaceSearch

/**
 * Búsqueda de localidades con la API de geocodificación de Open-Meteo.
 * Solo se envía el texto que el usuario escribe al buscar (nunca coordenadas).
 */
class OpenMeteoPlaceSearch(private val api: OpenMeteoGeocodingApi) : PlaceSearch {
    override suspend fun search(query: String): List<Place> =
        api.search(query.trim()).results.mapNotNull { r ->
            val coordinates = try {
                Coordinates(r.latitude, r.longitude)
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            val region = listOfNotNull(r.admin1, r.country).joinToString(", ").ifBlank { null }
            Place(name = r.name, region = region, coordinates = coordinates)
        }
}
