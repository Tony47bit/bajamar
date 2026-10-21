package com.pescasub.app.data.local

import com.pescasub.app.domain.Activity
import com.pescasub.app.domain.ApneaStats
import com.pescasub.app.domain.CatchItem
import com.pescasub.app.domain.ConditionsSnapshot
import com.pescasub.app.domain.Coordinates
import com.pescasub.app.domain.SavedLocation
import com.pescasub.app.domain.Session
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

internal fun LocationEntity.toDomain() = SavedLocation(
    id = id,
    name = name,
    description = description,
    coordinates = Coordinates(latitude, longitude),
    isFavorite = isFavorite,
    createdAt = Instant.ofEpochMilli(createdAt)
)

internal fun SavedLocation.toEntity() = LocationEntity(
    id = id,
    name = name,
    description = description,
    latitude = coordinates.latitude,
    longitude = coordinates.longitude,
    isFavorite = isFavorite,
    createdAt = createdAt.toEpochMilli()
)

private fun zoneOrDefault(id: String): ZoneId =
    try {
        ZoneId.of(id)
    } catch (e: DateTimeException) {
        ZoneId.systemDefault()
    }

internal fun SessionWithDetails.toDomain(): Session {
    val s = session
    val hasApnea = s.apneaMaxDepthM != null || s.apneaMaxTimeSec != null
    return Session(
        id = s.id,
        startedAt = Instant.ofEpochMilli(s.startedAt),
        zone = zoneOrDefault(s.zoneId),
        activity = Activity.fromName(s.activity),
        durationMinutes = s.durationMinutes,
        location = location?.toDomain(),
        notes = s.notes,
        conditions = s.snapSource?.let {
            ConditionsSnapshot(
                airTempC = s.snapAirTempC,
                windKmh = s.snapWindKmh,
                waveM = s.snapWaveM,
                wavePeriodS = s.snapWavePeriodS,
                seaTempC = s.snapSeaTempC,
                source = it
            )
        },
        apnea = if (hasApnea) ApneaStats(s.apneaMaxDepthM, s.apneaMaxTimeSec) else null,
        catches = catches.sortedBy { it.id }.map { CatchItem(it.id, it.species, it.weightKg, it.count) },
        photoPaths = photos.sortedBy { it.id }.map { it.filePath }
    )
}

internal fun Session.toEntity() = SessionEntity(
    id = id,
    startedAt = startedAt.toEpochMilli(),
    zoneId = zone.id,
    activity = activity.name,
    durationMinutes = durationMinutes,
    locationId = location?.id,
    notes = notes,
    snapAirTempC = conditions?.airTempC,
    snapWindKmh = conditions?.windKmh,
    snapWaveM = conditions?.waveM,
    snapWavePeriodS = conditions?.wavePeriodS,
    snapSeaTempC = conditions?.seaTempC,
    snapSource = conditions?.source,
    apneaMaxDepthM = apnea?.maxDepthM,
    apneaMaxTimeSec = apnea?.maxTimeSec
)

internal fun CatchItem.toEntity(sessionId: Long) = CatchEntity(
    sessionId = sessionId,
    species = species,
    weightKg = weightKg,
    count = count
)
