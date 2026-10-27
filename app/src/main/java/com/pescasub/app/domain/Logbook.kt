package com.pescasub.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class Activity(val label: String) {
    SPEARFISHING("Pesca submarina"),
    FREEDIVING("Apnea"),
    SCUBA("Buceo");

    /** La profundidad y el tiempo de apnea solo tienen sentido sin botella. */
    val hasApneaData: Boolean get() = this != SCUBA

    companion object {
        fun fromName(name: String?): Activity = entries.firstOrNull { it.name == name } ?: SPEARFISHING
    }
}

data class SavedLocation(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val coordinates: Coordinates,
    val isFavorite: Boolean = true,
    val createdAt: Instant = Instant.now()
)

/** Una fila de captura: una o varias piezas de una especie. `weightKg` es el peso total de la fila. */
data class CatchItem(
    val id: Long = 0,
    val species: String,
    val weightKg: Double?,
    val count: Int
)

/** Condiciones obtenidas de una API en el momento de la jornada (no escritas a mano). */
data class ConditionsSnapshot(
    val airTempC: Double?,
    val windKmh: Double?,
    val waveM: Double?,
    val wavePeriodS: Double?,
    val seaTempC: Double?,
    val source: String
)

data class ApneaStats(val maxDepthM: Double?, val maxTimeSec: Int?)

data class Session(
    val id: Long = 0,
    val startedAt: Instant,
    val zone: ZoneId,
    val activity: Activity,
    val durationMinutes: Int?,
    val location: SavedLocation?,
    val notes: String,
    val conditions: ConditionsSnapshot?,
    val apnea: ApneaStats?,
    val catches: List<CatchItem>,
    val photoPaths: List<String>,
    /** Visibilidad horizontal que vio el usuario, en metros enteros (sirve para ajustar la estimación del punto). */
    val observedVisibilityM: Int? = null,
    /** Cifra central que estimaba el modelo (sin ajustar) al guardar la jornada; `null` si no se pudo calcular. */
    val estimatedVisibilityM: Int? = null
) {
    val localDate: LocalDate get() = startedAt.atZone(zone).toLocalDate()
    val totalCatches: Int get() = catches.sumOf { it.count }
    val totalWeightKg: Double get() = catches.sumOf { it.weightKg ?: 0.0 }
}

enum class SortOrder(val label: String) {
    NEWEST("Más recientes"),
    OLDEST("Más antiguas"),
    MOST_CATCHES("Más capturas"),
    HEAVIEST("Mayor peso")
}

data class LogbookFilter(
    val activity: Activity? = null,
    val locationId: Long? = null,
    val species: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val sort: SortOrder = SortOrder.NEWEST
) {
    val isActive: Boolean
        get() = activity != null || locationId != null || species != null || from != null || to != null

    fun apply(sessions: List<Session>): List<Session> {
        val filtered = sessions.filter { s ->
            (activity == null || s.activity == activity) &&
                (locationId == null || s.location?.id == locationId) &&
                (species == null || s.catches.any { it.species.equals(species, ignoreCase = true) }) &&
                (from == null || !s.localDate.isBefore(from)) &&
                (to == null || !s.localDate.isAfter(to))
        }
        return when (sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.startedAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.startedAt }
            SortOrder.MOST_CATCHES -> filtered.sortedByDescending { it.totalCatches }
            SortOrder.HEAVIEST -> filtered.sortedByDescending { it.totalWeightKg }
        }
    }
}

data class SpeciesStat(val species: String, val count: Int, val weightKg: Double)

data class MonthStat(val month: YearMonth, val sessions: Int, val catches: Int, val weightKg: Double)

/** Estadísticas calculadas únicamente a partir de las jornadas reales recibidas. */
data class LogbookStats(
    val sessions: Int,
    val totalMinutes: Int,
    val totalCatches: Int,
    val totalWeightKg: Double,
    val bySpecies: List<SpeciesStat>,
    val byMonth: List<MonthStat>,
    val bestApneaDepthM: Double?,
    val bestApneaTimeSec: Int?
) {
    companion object {
        fun from(sessions: List<Session>): LogbookStats {
            val bySpecies = sessions.flatMap { it.catches }
                .groupBy { normalizeSpecies(it.species).lowercase() }
                .map { (_, rows) ->
                    SpeciesStat(
                        species = normalizeSpecies(rows.first().species),
                        count = rows.sumOf { it.count },
                        weightKg = rows.sumOf { it.weightKg ?: 0.0 }
                    )
                }
                .sortedWith(compareByDescending<SpeciesStat> { it.count }.thenBy { it.species })

            val byMonth = sessions.groupBy { YearMonth.from(it.localDate) }
                .map { (month, list) ->
                    MonthStat(
                        month = month,
                        sessions = list.size,
                        catches = list.sumOf { it.totalCatches },
                        weightKg = list.sumOf { it.totalWeightKg }
                    )
                }
                .sortedBy { it.month }

            return LogbookStats(
                sessions = sessions.size,
                totalMinutes = sessions.sumOf { it.durationMinutes ?: 0 },
                totalCatches = sessions.sumOf { it.totalCatches },
                totalWeightKg = sessions.sumOf { it.totalWeightKg },
                bySpecies = bySpecies,
                byMonth = byMonth,
                bestApneaDepthM = sessions.mapNotNull { it.apnea?.maxDepthM }.maxOrNull(),
                bestApneaTimeSec = sessions.mapNotNull { it.apnea?.maxTimeSec }.maxOrNull()
            )
        }
    }
}
