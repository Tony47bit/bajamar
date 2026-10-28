package com.pescasub.app.data.repo

import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.WeatherRepository
import com.pescasub.app.domain.visibility.VisibilityEstimate
import com.pescasub.app.domain.visibility.VisibilityEstimateSource
import com.pescasub.app.domain.visibility.VisibilityResolver
import com.pescasub.app.domain.visibility.WaterClarityRepository
import java.time.Clock
import kotlinx.coroutines.CancellationException

/** Calcula la estimación SIN calibrar de un punto con la misma lógica que muestra la tarjeta de visibilidad. */
class ResolverVisibilityEstimateSource(
    private val weather: WeatherRepository,
    private val clarity: WaterClarityRepository,
    private val clock: Clock = Clock.systemUTC()
) : VisibilityEstimateSource {
    override suspend fun centralMeters(coordinates: Coordinates): Int? = try {
        val bundle = when (val r = weather.getForecast(coordinates)) {
            is ForecastResult.Fresh -> r.bundle
            is ForecastResult.Stale -> r.bundle
            is ForecastResult.Failed -> null
        } ?: return null
        val now = clock.instant()
        val estimate = VisibilityResolver.resolve(
            fetch = clarity.latest(coordinates),
            climatology = { clarity.climatology(coordinates, now) },
            recent = bundle.recent,
            upcoming = bundle.hourly,
            now = now,
            calibration = null // siempre sin calibrar: si no, el ajuste se retroalimentaría
        )
        when (estimate) {
            is VisibilityEstimate.Estimated -> (estimate.minMeters + estimate.maxMeters) / 2
            is VisibilityEstimate.Climatological -> (estimate.minMeters + estimate.maxMeters) / 2
            else -> null
        }?.takeIf { it >= 1 }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
