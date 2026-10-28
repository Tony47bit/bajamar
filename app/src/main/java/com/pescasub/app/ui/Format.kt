package com.pescasub.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Formato de valores para mostrar. Los datos ausentes se muestran como "—", nunca como 0. */
object Fmt {
    private val es: Locale = Locale.forLanguageTag("es-ES")
    const val MISSING = "—"

    private val POINTS = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"
    )

    /** Rosa de 16 vientos en español (O = oeste). Los grados se normalizan a 0..360. */
    fun compass(deg: Double?): String {
        if (deg == null || !deg.isFinite()) return MISSING
        val normalized = ((deg % 360.0) + 360.0) % 360.0
        val index = ((normalized / 22.5) + 0.5).toInt() % 16
        return POINTS[index]
    }

    fun number(value: Double?, decimals: Int = 1, unit: String = ""): String {
        if (value == null || !value.isFinite()) return MISSING
        val text = String.format(es, "%.${decimals}f", value)
        return if (unit.isEmpty()) text else "$text $unit"
    }

    /** Número sin unidad (para textos que ya incluyen la unidad). */
    fun num(value: Double?, decimals: Int = 1): String = number(value, decimals)

    fun temp(value: Double?) = number(value, 0, "°C")
    fun speed(value: Double?) = number(value, 0, "km/h")
    fun height(value: Double?) = number(value, 1, "m")
    fun period(value: Double?) = number(value, 0, "s")

    fun time(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm", es).format(instant.atZone(zone))

    fun hour(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH'h'", es).format(instant.atZone(zone))

    fun dateTime(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", es).format(instant.atZone(zone))

    fun month(month: YearMonth): String =
        DateTimeFormatter.ofPattern("MMM yy", es).format(month)

    fun dayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, es).replaceFirstChar { it.uppercase() } +
            " " + date.dayOfMonth

    /** «Hoy» / «Mañana» respecto a `today` (el día local de la ubicación); otro día, su nombre corto. */
    fun dayRelative(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> dayShort(date)
    }

    fun dayLong(date: LocalDate): String =
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es).format(date).replaceFirstChar { it.uppercase() }

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

    fun duration(minutes: Int?): String {
        if (minutes == null) return MISSING
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun seconds(sec: Int?): String {
        if (sec == null) return MISSING
        return "${sec / 60}:${String.format(Locale.ROOT, "%02d", sec % 60)} min"
    }

    fun kg(value: Double?) = number(value, 2, "kg")
}
