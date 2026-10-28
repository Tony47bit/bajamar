package com.pescasub.app.data

import com.pescasub.app.data.local.ForecastCacheDao
import com.pescasub.app.data.local.ForecastCacheEntity
import com.pescasub.app.data.repo.DefaultWeatherRepository
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.ForecastBundle
import com.pescasub.app.domain.ForecastError
import com.pescasub.app.domain.ForecastResult
import com.pescasub.app.domain.HourlyConditions
import com.pescasub.app.domain.WeatherProvider
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

private class FakeCacheDao : ForecastCacheDao {
    val store = mutableMapOf<String, ForecastCacheEntity>()
    var failOnUpsert = false
    override suspend fun get(key: String): ForecastCacheEntity? = store[key]
    override suspend fun upsert(entry: ForecastCacheEntity) {
        if (failOnUpsert) throw IllegalStateException("disco lleno")
        store[entry.cacheKey] = entry
    }
    override suspend fun clear() = store.clear()
}

private class FakeProvider : WeatherProvider {
    override val id = "fake"
    override val sourceName = "Fake"
    override val attribution = "Datos de prueba"
    var fetchCount = 0
    var error: Exception? = null
    var nextRaw: String? = null
    var lastRequested: Coordinates? = null

    override suspend fun fetchRaw(coordinates: Coordinates): String {
        fetchCount++
        lastRequested = coordinates
        error?.let { throw it }
        return nextRaw ?: "raw-$fetchCount"
    }

    override fun parse(raw: String, coordinates: Coordinates, fetchedAt: Instant): ForecastBundle {
        if (raw == "corrupt") throw IllegalArgumentException("carga ilegible")
        return ForecastBundle(
            coordinates = coordinates,
            zone = ZoneOffset.UTC,
            fetchedAt = fetchedAt,
            sourceName = sourceName,
            hourly = listOf(HourlyConditions(time = fetchedAt, windSpeedKmh = raw.length.toDouble())),
            tides = emptyList(),
            hasMarineData = false,
            notes = emptyList()
        )
    }
}

class WeatherRepositoryTest {
    private val t0 = Instant.parse("2026-10-20T08:00:00Z")
    private val spot = Coordinates(36.7213, -4.4214)

    private lateinit var clock: MutableClock
    private lateinit var dao: FakeCacheDao
    private lateinit var provider: FakeProvider
    private lateinit var repo: DefaultWeatherRepository

    @Before
    fun setUp() {
        clock = MutableClock(t0)
        dao = FakeCacheDao()
        provider = FakeProvider()
        repo = DefaultWeatherRepository(provider, dao, clock, Duration.ofMinutes(30), Duration.ofHours(48))
    }

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun firstCallFetchesAndCaches() = run {
        val r = repo.getForecast(spot) as ForecastResult.Fresh
        assertEquals(1, provider.fetchCount)
        assertEquals(false, r.bundle.fromCache)
        assertEquals(t0, r.bundle.fetchedAt)
        assertEquals(1, dao.store.size)
    }

    @Test
    fun recentCacheIsServedWithoutNetwork() = run {
        repo.getForecast(spot)
        clock.now = t0.plus(Duration.ofMinutes(10))
        val r = repo.getForecast(spot) as ForecastResult.Fresh
        assertEquals(1, provider.fetchCount)
        assertEquals(true, r.bundle.fromCache)
    }

    @Test
    fun forceRefreshIgnoresFreshCache() = run {
        repo.getForecast(spot)
        repo.getForecast(spot, forceRefresh = true)
        assertEquals(2, provider.fetchCount)
    }

    @Test
    fun expiredCacheTriggersNewFetch() = run {
        repo.getForecast(spot)
        clock.now = t0.plus(Duration.ofMinutes(31))
        repo.getForecast(spot)
        assertEquals(2, provider.fetchCount)
    }

    @Test
    fun networkFailureWithOldCacheReturnsStaleDataAndError() = run {
        repo.getForecast(spot)
        clock.now = t0.plus(Duration.ofHours(3))
        provider.error = IOException("sin red")
        val r = repo.getForecast(spot) as ForecastResult.Stale
        assertEquals(ForecastError.NETWORK, r.error)
        assertEquals(true, r.bundle.fromCache)
        assertEquals(t0, r.bundle.fetchedAt)
    }

    @Test
    fun networkFailureWithoutCacheIsFailedNotACrash() = run {
        provider.error = IOException("sin red")
        val r = repo.getForecast(spot) as ForecastResult.Failed
        assertEquals(ForecastError.NETWORK, r.error)
    }

    @Test
    fun cacheOlderThanMaxStaleIsNotShown() = run {
        repo.getForecast(spot)
        clock.now = t0.plus(Duration.ofHours(49))
        provider.error = IOException("sin red")
        assertTrue(repo.getForecast(spot) is ForecastResult.Failed)
    }

    @Test
    fun unreadableResponseIsReportedAsParseError() = run {
        provider.nextRaw = "corrupt"
        val r = repo.getForecast(spot) as ForecastResult.Failed
        assertEquals(ForecastError.PARSE, r.error)
        assertEquals(0, dao.store.size) // una respuesta inválida no se cachea
    }

    @Test
    fun unexpectedExceptionsBecomeUnknownError() = run {
        provider.error = IllegalStateException("inesperado")
        val r = repo.getForecast(spot) as ForecastResult.Failed
        assertEquals(ForecastError.UNKNOWN, r.error)
    }

    @Test
    fun corruptCacheEntryIsIgnoredAndRefetched() = run {
        repo.getForecast(spot)
        val key = dao.store.keys.single()
        dao.store[key] = dao.store.getValue(key).copy(payload = "corrupt")
        val r = repo.getForecast(spot) as ForecastResult.Fresh
        assertEquals(2, provider.fetchCount)
        assertEquals(false, r.bundle.fromCache)
    }

    @Test
    fun cacheWriteFailureDoesNotSpoilAValidResponse() = run {
        dao.failOnUpsert = true
        val r = repo.getForecast(spot)
        assertTrue(r is ForecastResult.Fresh)
    }

    @Test
    fun onlyRoundedCoordinatesLeaveTheDeviceAndShareTheCache() = run {
        repo.getForecast(Coordinates(36.7213, -4.4214))
        assertEquals(Coordinates(36.72, -4.42), provider.lastRequested)
        // Otra posición dentro de la misma celda de ~1 km reutiliza la caché.
        repo.getForecast(Coordinates(36.7249, -4.4151))
        assertEquals(1, provider.fetchCount)
        // Una posición distinta no.
        repo.getForecast(Coordinates(36.80, -4.42))
        assertEquals(2, provider.fetchCount)
        assertEquals(Coordinates(36.80, -4.42), provider.lastRequested)
    }

    @Test
    fun coordinatesAreRoundedToTwoDecimals() {
        assertEquals(Coordinates(36.72, -4.42), Coordinates(36.7213, -4.4214).coarse())
        assertEquals(Coordinates(-0.01, 179.99), Coordinates(-0.0149, 179.994).coarse())
        assertEquals("36.72,-4.42", Coordinates(36.7213, -4.4214).coarse().cacheKey())
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        var rejected = 0
        for ((lat, lon) in listOf(91.0 to 0.0, -91.0 to 0.0, 0.0 to 181.0, Double.NaN to 0.0)) {
            try {
                Coordinates(lat, lon)
            } catch (e: IllegalArgumentException) {
                rejected++
            }
        }
        assertEquals(4, rejected)
    }

    @Test
    fun recentCacheFromYesterdayIsNotServedAsFreshAfterLocalMidnight() = run {
        clock.now = Instant.parse("2026-10-20T23:50:00Z")
        repo.getForecast(spot) // guarda en caché
        assertEquals(1, provider.fetchCount)

        clock.now = Instant.parse("2026-10-21T00:10:00Z") // 20 min después, pero ya es otro día (zona UTC)
        val r = repo.getForecast(spot) as ForecastResult.Fresh
        assertEquals("debe volver a pedir: la caché no cubre el día actual", 2, provider.fetchCount)
        assertEquals(false, r.bundle.fromCache)
    }

    @Test
    fun staleCacheWithNoHoursInTheWindowIsNotServed() = run {
        repo.getForecast(spot)
        clock.now = t0.plus(Duration.ofHours(40)) // dentro de 48 h, pero los datos son de anteayer
        provider.error = IOException("sin red")
        val r = repo.getForecast(spot)
        assertTrue("no debe mostrar días pasados como si fueran hoy", r is ForecastResult.Failed)
    }

    @Test
    fun staleCacheStillCoveringTodayIsServedWithTheError() = run {
        repo.getForecast(spot)
        clock.now = t0.plus(Duration.ofHours(2))
        provider.error = IOException("sin red")
        val r = repo.getForecast(spot) as ForecastResult.Stale
        assertEquals(ForecastError.NETWORK, r.error)
        assertTrue(r.bundle.hourly.all { r.bundle.localDate(it.time) in r.bundle.windowDates(clock.now) })
    }
}
