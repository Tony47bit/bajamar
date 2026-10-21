package com.pescasub.app.domain

import java.time.Instant
import java.util.Locale

/**
 * Exporta los datos personales del usuario a JSON (derecho de portabilidad). Se escribe a mano para
 * no depender de ninguna biblioteca y poder probarlo en JVM. Las fotos no se incluyen (solo cuántas
 * hay); las coordenadas sí, porque son datos del propio usuario y el archivo lo guarda él donde quiera.
 */
object DataExporter {
    const val FORMAT_VERSION = 1

    fun toJson(locations: List<SavedLocation>, sessions: List<Session>, exportedAt: Instant): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n")
        sb.append("  \"exportedAt\": ").append(str(exportedAt.toString())).append(",\n")
        sb.append("  \"locations\": [")
        locations.forEachIndexed { i, l ->
            sb.append(if (i == 0) "\n" else ",\n")
            sb.append("    {")
            sb.append("\"id\": ").append(l.id)
            sb.append(", \"name\": ").append(str(l.name))
            sb.append(", \"description\": ").append(str(l.description))
            sb.append(", \"latitude\": ").append(num(l.coordinates.latitude))
            sb.append(", \"longitude\": ").append(num(l.coordinates.longitude))
            sb.append(", \"favorite\": ").append(l.isFavorite)
            sb.append(", \"createdAt\": ").append(str(l.createdAt.toString()))
            sb.append("}")
        }
        sb.append(if (locations.isEmpty()) "],\n" else "\n  ],\n")
        sb.append("  \"sessions\": [")
        sessions.forEachIndexed { i, s ->
            sb.append(if (i == 0) "\n" else ",\n")
            appendSession(sb, s)
        }
        sb.append(if (sessions.isEmpty()) "]\n" else "\n  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun appendSession(sb: StringBuilder, s: Session) {
        sb.append("    {\n")
        sb.append("      \"id\": ").append(s.id).append(",\n")
        sb.append("      \"startedAt\": ").append(str(s.startedAt.toString())).append(",\n")
        sb.append("      \"zone\": ").append(str(s.zone.id)).append(",\n")
        sb.append("      \"activity\": ").append(str(s.activity.name)).append(",\n")
        sb.append("      \"durationMinutes\": ").append(s.durationMinutes?.toString() ?: "null").append(",\n")
        sb.append("      \"locationId\": ").append(s.location?.id?.toString() ?: "null").append(",\n")
        sb.append("      \"notes\": ").append(str(s.notes)).append(",\n")
        val c = s.conditions
        if (c == null) {
            sb.append("      \"conditions\": null,\n")
        } else {
            sb.append("      \"conditions\": {")
            sb.append("\"source\": ").append(str(c.source))
            sb.append(", \"airTempC\": ").append(num(c.airTempC))
            sb.append(", \"windKmh\": ").append(num(c.windKmh))
            sb.append(", \"waveM\": ").append(num(c.waveM))
            sb.append(", \"wavePeriodS\": ").append(num(c.wavePeriodS))
            sb.append(", \"seaTempC\": ").append(num(c.seaTempC))
            sb.append("},\n")
        }
        val a = s.apnea
        if (a == null) {
            sb.append("      \"apnea\": null,\n")
        } else {
            sb.append("      \"apnea\": {\"maxDepthM\": ").append(num(a.maxDepthM))
            sb.append(", \"maxTimeSec\": ").append(a.maxTimeSec?.toString() ?: "null").append("},\n")
        }
        sb.append("      \"catches\": [")
        s.catches.forEachIndexed { i, ci ->
            if (i > 0) sb.append(", ")
            sb.append("{\"species\": ").append(str(ci.species))
            sb.append(", \"weightKg\": ").append(num(ci.weightKg))
            sb.append(", \"count\": ").append(ci.count).append("}")
        }
        sb.append("],\n")
        sb.append("      \"photoCount\": ").append(s.photoPaths.size).append("\n")
        sb.append("    }")
    }

    private fun num(v: Double?): String =
        if (v == null || !v.isFinite()) "null" else String.format(Locale.ROOT, "%s", v.toString())

    /** Cadena JSON con escape de comillas, barras y caracteres de control. */
    fun str(value: String): String {
        val sb = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append(String.format(Locale.ROOT, "\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }
}
