package com.pescasub.app.domain

sealed interface Validated<out T> {
    data class Valid<T>(val value: T) : Validated<T>
    data class Invalid(val message: String) : Validated<Nothing>
}

/** Validación de formularios (texto introducido por el usuario → valores tipados). */
object FormValidation {
    const val MAX_NAME = 60
    const val MAX_DESCRIPTION = 500
    const val MAX_NOTES = 4000
    const val MAX_SPECIES = 60

    /** Acepta coma o punto decimal. Rechaza NaN/Infinity. */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun optionalInt(text: String, min: Int, max: Int, label: String): Validated<Int?> {
        val t = text.trim()
        if (t.isEmpty()) return Validated.Valid(null)
        val v = t.toIntOrNull() ?: return Validated.Invalid("$label debe ser un número entero")
        if (v < min || v > max) return Validated.Invalid("$label debe estar entre $min y $max")
        return Validated.Valid(v)
    }

    fun optionalDecimal(text: String, min: Double, max: Double, label: String): Validated<Double?> {
        val t = text.trim()
        if (t.isEmpty()) return Validated.Valid(null)
        val v = parseDecimal(t) ?: return Validated.Invalid("$label debe ser un número")
        if (v < min || v > max) {
            return Validated.Invalid("$label debe estar entre ${plain(min)} y ${plain(max)}")
        }
        return Validated.Valid(v)
    }

    fun locationName(name: String): Validated<String> {
        val n = name.trim()
        return when {
            n.isEmpty() -> Validated.Invalid("Escribe un nombre")
            n.length > MAX_NAME -> Validated.Invalid("El nombre admite hasta $MAX_NAME caracteres")
            else -> Validated.Valid(n)
        }
    }

    fun description(text: String): Validated<String> {
        val d = text.trim()
        return if (d.length > MAX_DESCRIPTION) {
            Validated.Invalid("La descripción admite hasta $MAX_DESCRIPTION caracteres")
        } else {
            Validated.Valid(d)
        }
    }

    fun notes(text: String): Validated<String> =
        if (text.length > MAX_NOTES) {
            Validated.Invalid("Las notas admiten hasta $MAX_NOTES caracteres")
        } else {
            Validated.Valid(text.trim())
        }

    fun coordinates(latText: String, lonText: String): Validated<Coordinates> {
        val lat = parseDecimal(latText) ?: return Validated.Invalid("La latitud debe ser un número")
        val lon = parseDecimal(lonText) ?: return Validated.Invalid("La longitud debe ser un número")
        if (lat !in -90.0..90.0) return Validated.Invalid("La latitud debe estar entre -90 y 90")
        if (lon !in -180.0..180.0) return Validated.Invalid("La longitud debe estar entre -180 y 180")
        return Validated.Valid(Coordinates(lat, lon))
    }

    /** Valida una fila de captura. `weight` es el peso total de la fila, en kg. */
    fun catchItem(species: String, weight: String, count: String): Validated<CatchItem> {
        val s = normalizeSpecies(species)
        if (s.isEmpty()) return Validated.Invalid("Indica la especie")
        if (s.length > MAX_SPECIES) return Validated.Invalid("La especie admite hasta $MAX_SPECIES caracteres")
        val w = when (val r = optionalDecimal(weight, 0.0, 500.0, "El peso")) {
            is Validated.Valid -> r.value
            is Validated.Invalid -> return r
        }
        val c = when (val r = optionalInt(count.ifBlank { "1" }, 1, 999, "El número de piezas")) {
            is Validated.Valid -> r.value ?: 1
            is Validated.Invalid -> return r
        }
        return Validated.Valid(CatchItem(species = s, weightKg = w, count = c))
    }

    private fun plain(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}

/** Quita espacios sobrantes para que "  Dorada " y "dorada" se agrupen igual. */
fun normalizeSpecies(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
