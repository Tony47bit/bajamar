package com.pescasub.app.data.repo

import com.pescasub.app.data.local.ForecastCacheDao
import com.pescasub.app.data.local.ForecastCacheEntity
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.ForecastError
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.WeatherProvider
import com.pescasub.app.domain.WeatherRepository
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * Obtiene previsiones con caché local: sirve datos recientes sin red, y si la actualización falla
 * devuelve los últimos datos guardados indicando el error (nunca bloquea ni inventa valores).
 */
class DefaultWeatherRepository(
    private val provider: WeatherProvider,
    private val cache: ForecastCacheDao,
    private val clock: Clock = Clock.systemUTC(),
    private val freshFor: Duration = Duration.ofMinutes(30),
    private val maxStale: Duration = Duration.ofHours(48)
) : WeatherRepository {

    override suspend fun getForecast(coordinates: Coordinates, forceRefresh: Boolean): ForecastResult {
        val coarse = coordinates.coarse()
        val key = "${provider.id}:${coarse.cacheKey()}"

        val entry = try {
            cache.get(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val cachedAt = entry?.let { Instant.ofEpochMilli(it.fetchedAt) }
        val cachedBundle: ForecastBundle? = if (entry != null && cachedAt != null) {
            try {
                provider.parse(entry.payload, coarse, cachedAt).copy(fromCache = true)
                    .limitedToWindow(clock.instant())
                    .takeIf { it.hourly.isNotEmpty() } // caché de días ya pasados: no sirve
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // carga útil ilegible o de otra versión: se ignora
            }
        } else {
            null
        }
        val cacheAge = cachedAt?.let { Duration.between(it, clock.instant()) }

        // Una caché reciente solo vale si incluye el día local actual (p. ej. tras pasar la medianoche no).
        if (!forceRefresh && cachedBundle != null && cacheAge != null && cacheAge < freshFor &&
            cachedBundle.coversToday(clock.instant())
        ) {
            return ForecastResult.Fresh(cachedBundle)
        }

        return try {
            val raw = provider.fetchRaw(coarse)
            val now = clock.instant()
            val bundle = provider.parse(raw, coarse, now).limitedToWindow(now)
            try {
                cache.upsert(ForecastCacheEntity(cacheKey = key, payload = raw, fetchedAt = now.toEpochMilli()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un fallo al cachear no debe estropear una respuesta válida.
            }
            ForecastResult.Fresh(bundle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toForecastError()
            if (cachedBundle != null && cacheAge != null && cacheAge < maxStale) {
                ForecastResult.Stale(cachedBundle, error)
            } else {
                ForecastResult.Failed(error)
            }
        }
    }
}

internal fun Throwable.toForecastError(): ForecastError = when (this) {
    is HttpException -> if (code() == 429) ForecastError.RATE_LIMITED else ForecastError.SERVER
    is IOException -> ForecastError.NETWORK
    is SerializationException, is IllegalArgumentException -> ForecastError.PARSE
    else -> ForecastError.UNKNOWN
}
