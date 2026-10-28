package com.pescasub.app.domain

import java.time.LocalDate

data class PeriodTotals(val sessions: Int, val catches: Int, val weightKg: Double, val minutes: Int)

/**
 * Compara los últimos `days` días (incluido `today`) con los `days` anteriores.
 * Solo usa jornadas reales; las posteriores a `today` no cuentan en ningún periodo.
 */
data class PeriodComparison(val days: Int, val current: PeriodTotals, val previous: PeriodTotals) {
    companion object {
        fun of(sessions: List<Session>, today: LocalDate, days: Int): PeriodComparison {
            require(days > 0) { "days debe ser positivo" }
            val currentStart = today.minusDays(days.toLong())
            val previousStart = today.minusDays(2L * days)
            val current = sessions.filter { it.localDate.isAfter(currentStart) && !it.localDate.isAfter(today) }
            val previous = sessions.filter {
                it.localDate.isAfter(previousStart) && !it.localDate.isAfter(currentStart)
            }
            return PeriodComparison(days, totals(current), totals(previous))
        }

        private fun totals(list: List<Session>) = PeriodTotals(
            sessions = list.size,
            catches = list.sumOf { it.totalCatches },
            weightKg = list.sumOf { it.totalWeightKg },
            minutes = list.sumOf { it.durationMinutes ?: 0 }
        )
    }
}
