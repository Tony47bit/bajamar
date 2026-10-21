package com.pescasub.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormValidationTest {
    @Test
    fun parseDecimalAcceptsCommaAndDot() {
        assertEquals(1.5, FormValidation.parseDecimal("1,5")!!, 0.0)
        assertEquals(1.5, FormValidation.parseDecimal(" 1.5 ")!!, 0.0)
        assertNull(FormValidation.parseDecimal("abc"))
        assertNull(FormValidation.parseDecimal("NaN"))
        assertNull(FormValidation.parseDecimal("Infinity"))
    }

    @Test
    fun optionalIntBlankIsNullAndRangeIsChecked() {
        assertEquals(Validated.Valid<Int?>(null), FormValidation.optionalInt("  ", 1, 10, "X"))
        assertEquals(Validated.Valid<Int?>(5), FormValidation.optionalInt("5", 1, 10, "X"))
        assertTrue(FormValidation.optionalInt("0", 1, 10, "X") is Validated.Invalid)
        assertTrue(FormValidation.optionalInt("11", 1, 10, "X") is Validated.Invalid)
        assertTrue(FormValidation.optionalInt("2,5", 1, 10, "X") is Validated.Invalid)
    }

    @Test
    fun optionalDecimalChecksRangeAndMessage() {
        assertEquals(Validated.Valid<Double?>(2.5), FormValidation.optionalDecimal("2,5", 0.0, 10.0, "El peso"))
        val bad = FormValidation.optionalDecimal("-1", 0.0, 10.0, "El peso") as Validated.Invalid
        assertEquals("El peso debe estar entre 0 y 10", bad.message)
    }

    @Test
    fun locationNameRules() {
        assertEquals(Validated.Valid("Cala Honda"), FormValidation.locationName("  Cala Honda "))
        assertTrue(FormValidation.locationName("   ") is Validated.Invalid)
        assertTrue(FormValidation.locationName("x".repeat(61)) is Validated.Invalid)
    }

    @Test
    fun coordinatesAreValidatedAndParsed() {
        val ok = FormValidation.coordinates("36,7213", "-4.4214") as Validated.Valid
        assertEquals(36.7213, ok.value.latitude, 1e-9)
        assertEquals(-4.4214, ok.value.longitude, 1e-9)
        assertTrue(FormValidation.coordinates("91", "0") is Validated.Invalid)
        assertTrue(FormValidation.coordinates("0", "181") is Validated.Invalid)
        assertTrue(FormValidation.coordinates("", "0") is Validated.Invalid)
        assertTrue(FormValidation.coordinates("0", "x") is Validated.Invalid)
    }

    @Test
    fun catchItemRequiresSpeciesAndNormalizesIt() {
        val ok = FormValidation.catchItem("  Dorada   real ", "1,2", "") as Validated.Valid
        assertEquals("Dorada real", ok.value.species)
        assertEquals(1.2, ok.value.weightKg!!, 1e-9)
        assertEquals(1, ok.value.count)
        assertTrue(FormValidation.catchItem("  ", "1", "1") is Validated.Invalid)
    }

    @Test
    fun catchItemRejectsBadWeightAndCount() {
        assertTrue(FormValidation.catchItem("Sargo", "-3", "1") is Validated.Invalid)
        assertTrue(FormValidation.catchItem("Sargo", "abc", "1") is Validated.Invalid)
        assertTrue(FormValidation.catchItem("Sargo", "", "0") is Validated.Invalid)
        assertTrue(FormValidation.catchItem("Sargo", "", "1000") is Validated.Invalid)
        val noWeight = FormValidation.catchItem("Sargo", "", "3") as Validated.Valid
        assertNull(noWeight.value.weightKg)
        assertEquals(3, noWeight.value.count)
    }

    @Test
    fun notesLengthIsLimited() {
        assertTrue(FormValidation.notes("a".repeat(FormValidation.MAX_NOTES)) is Validated.Valid)
        assertTrue(FormValidation.notes("a".repeat(FormValidation.MAX_NOTES + 1)) is Validated.Invalid)
    }
}
