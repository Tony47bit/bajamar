package com.pescasub.app.data.remote

import com.pescasub.app.domain.FORECAST_WINDOW_DAYS
import com.pescasub.app.domain.RECENT_CONTEXT_DAYS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Respuesta común de las APIs de previsión y marina de Open-Meteo con `timeformat=unixtime`
 * (los instantes llegan como segundos UTC). Cada lista puede contener nulos.
 */
@Serializable
data class OpenMeteoResponse(
    val timezone: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    val hourly: HourlyDto? = null
)

@Serializable
data class HourlyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature2m: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts10m: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection10m: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("wave_height") val waveHeight: List<Double?> = emptyList(),
    @SerialName("wave_direction") val waveDirection: List<Double?> = emptyList(),
    @SerialName("wave_period") val wavePeriod: List<Double?> = emptyList(),
    @SerialName("swell_wave_height") val swellWaveHeight: List<Double?> = emptyList(),
    @SerialName("swell_wave_direction") val swellWaveDirection: List<Double?> = emptyList(),
    @SerialName("swell_wave_period") val swellWavePeriod: List<Double?> = emptyList(),
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: List<Double?> = emptyList(),
    @SerialName("sea_level_height_msl") val seaLevelHeightMsl: List<Double?> = emptyList()
)

/** Lo que se guarda en la caché: las tres respuestas tal como llegaron. */
@Serializable
data class CachedPayload(
    val weather: OpenMeteoResponse,
    val marine: OpenMeteoResponse? = null,
    val tides: OpenMeteoResponse? = null
)

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
)

interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("forecast_days") forecastDays: Int = FORECAST_WINDOW_DAYS,
        @Query("past_days") pastDays: Int = RECENT_CONTEXT_DAYS
    ): OpenMeteoResponse
}

interface OpenMeteoMarineApi {
    @GET("v1/marine")
    suspend fun marine(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("forecast_days") forecastDays: Int = FORECAST_WINDOW_DAYS,
        @Query("past_days") pastDays: Int = RECENT_CONTEXT_DAYS
    ): OpenMeteoResponse
}

interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 8,
        @Query("language") language: String = "es",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}
