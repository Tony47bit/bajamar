package com.pescasub.app.data

import com.pescasub.app.data.remote.CachedPayload
import com.pescasub.app.data.remote.HourlyDto
import com.pescasub.app.data.remote.OpenMeteoMapper
import com.pescasub.app.data.remote.OpenMeteoResponse
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.TideType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoMapperTest {
    private val coords = Coordinates(36.72, -4.42)
    private val fetchedAt = Instant.parse("2026-10-20T08:00:00Z")

    // 2026-10-20T00:00:00 hora de Madrid (UTC+2) = 2026-10-19T22:00:00Z
    private val base = Instant.parse("2026-10-19T22:00:00Z").epochSecond
    private val hours = 48
    private val times = (0 until hours).map { base + it * 3600L }

    private fun weather(timezone: String? = "Europe/Madrid", offset: Int? = 7200) = OpenMeteoResponse(
        timezone = timezone,
        utcOffsetSeconds = offset,
        hourly = HourlyDto(
            time = times,
            temperature2m = times.indices.map { 20.0 + it * 0.1 },
            windSpeed10m = times.indices.map { 10.0 },
            windGusts10m = times.indices.map { 18.0 },
            windDirection10m = times.indices.map { 45.0 }
        )
    )

    private fun marine() = OpenMeteoResponse(
        hourly = HourlyDto(
            time = times,
            waveHeight = times.indices.map { if (it == 3) null else 0.4 },
            waveDirection = times.indices.map { 90.0 },
            wavePeriod = times.indices.map { 5.5 },
            swellWaveHeight = times.indices.map { 0.2 },
            swellWaveDirection = times.indices.map { 120.0 },
            swellWavePeriod = times.indices.map { 8.0 },
            seaSurfaceTemperature = times.indices.map { 21.5 }
        )
    )

    private fun tides() = OpenMeteoResponse(
        hourly = HourlyDto(
            time = times,
            seaLevelHeightMsl = times.indices.map { 0.4 * sin(2 * PI * it / 12.42) }
        )
    )

    private fun bundle(payload: CachedPayload) = OpenMeteoMapper.toBundle(payload, coords, fetchedAt, "Open-Meteo")

    @Test
    fun mapsWeatherMarineAndTidesByTimestamp() {
        val b = bundle(CachedPayload(weather(), marine(), tides()))
        assertEquals(hours, b.hourly.size)
        val h = b.hourly[5]
        assertEquals(Instant.ofEpochSecond(base + 5 * 3600L), h.time)
        assertEquals(20.5, h.airTempC!!, 1e-9)
        assertEquals(10.0, h.windSpeedKmh!!, 0.0)
        assertEquals(18.0, h.windGustsKmh!!, 0.0)
        assertEquals(45.0, h.windDirectionDeg!!, 0.0)
        assertEquals(0.4, h.waveHeightM!!, 0.0)
        assertEquals(5.5, h.wavePeriodS!!, 0.0)
        assertEquals(0.2, h.swellHeightM!!, 0.0)
        assertEquals(21.5, h.seaTempC!!, 0.0)
        assertTrue(b.hasMarineData)
        assertTrue(b.tides.isNotEmpty())
        assertTrue(b.tides.any { it.type == TideType.HIGH } && b.tides.any { it.type == TideType.LOW })
        assertTrue(b.notes.isEmpty())
    }

    @Test
    fun individualNullsStayNullInsteadOfBecomingZero() {
        val b = bundle(CachedPayload(weather(), marine(), tides()))
        assertNull(b.hourly[3].waveHeightM)
        assertEquals(0.4, b.hourly[4].waveHeightM!!, 0.0)
    }

    @Test
    fun missingMarineDataIsReportedNotInvented() {
        val b = bundle(CachedPayload(weather(), marine = null, tides = null))
        assertFalse(b.hasMarineData)
        assertTrue(b.hourly.all { it.waveHeightM == null && it.seaTempC == null && it.seaLevelM == null })
        assertTrue(OpenMeteoMapper.NOTE_NO_MARINE in b.notes)
        assertTrue(OpenMeteoMapper.NOTE_NO_TIDES in b.notes)
        assertTrue(b.tides.isEmpty())
        // el viento sigue disponible
        assertEquals(10.0, b.hourly.first().windSpeedKmh!!, 0.0)
    }

    @Test
    fun marineResponseWithAllNullsCountsAsNoMarineData() {
        val allNull = OpenMeteoResponse(
            hourly = HourlyDto(time = times, waveHeight = times.indices.map { null })
        )
        val b = bundle(CachedPayload(weather(), allNull, null))
        assertFalse(b.hasMarineData)
        assertTrue(OpenMeteoMapper.NOTE_NO_MARINE in b.notes)
    }

    @Test
    fun marineSeriesAreMatchedByTimeNotByPosition() {
        // El servicio marino empieza 2 horas más tarde: los valores deben alinearse por instante.
        val late = times.drop(2)
        val shifted = OpenMeteoResponse(
            hourly = HourlyDto(time = late, waveHeight = late.indices.map { 1.0 + it })
        )
        val b = bundle(CachedPayload(weather(), shifted, null))
        assertNull(b.hourly[0].waveHeightM)
        assertNull(b.hourly[1].waveHeightM)
        assertEquals(1.0, b.hourly[2].waveHeightM!!, 0.0)
        assertEquals(2.0, b.hourly[3].waveHeightM!!, 0.0)
    }

    @Test
    fun usesTheLocationTimezoneForDays() {
        val b = bundle(CachedPayload(weather(), marine(), tides()))
        assertEquals(ZoneId.of("Europe/Madrid"), b.zone)
        assertEquals(listOf(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-21")), b.dates())
        assertEquals(24, b.hoursOf(LocalDate.parse("2026-10-20")).size)
    }

    @Test
    fun fallsBackToUtcOffsetOrUtcWhenTimezoneIsInvalid() {
        val byOffset = bundle(CachedPayload(weather(timezone = "No/Existe", offset = 7200)))
        assertEquals(ZoneOffset.ofTotalSeconds(7200), byOffset.zone)
        val utc = bundle(CachedPayload(weather(timezone = null, offset = null)))
        assertEquals(ZoneOffset.UTC, utc.zone)
    }

    @Test
    fun responseWithoutHourlyBlockIsRejected() {
        var failed = false
        try {
            bundle(CachedPayload(OpenMeteoResponse(timezone = "UTC", hourly = null)))
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun dailySummaryAggregatesPerLocalDay() {
        val b = bundle(CachedPayload(weather(), marine(), tides()))
        val day1 = b.daily().first()
        assertEquals(LocalDate.parse("2026-10-20"), day1.date)
        assertEquals(20.0, day1.tempMinC!!, 1e-9)
        assertEquals(22.3, day1.tempMaxC!!, 1e-9)
        assertEquals(10.0, day1.windMaxKmh!!, 0.0)
        assertEquals(0.4, day1.waveMaxM!!, 0.0)
    }

    @Test
    fun currentHourIsTheLastNotAfterNow() {
        val b = bundle(CachedPayload(weather(), marine(), tides()))
        val now = Instant.ofEpochSecond(base + 5 * 3600L + 1800)
        assertEquals(Instant.ofEpochSecond(base + 5 * 3600L), b.at(now)!!.time)
        // antes de la primera hora se usa la primera
        assertEquals(Instant.ofEpochSecond(base), b.at(Instant.ofEpochSecond(base - 10_000))!!.time)
    }
}
