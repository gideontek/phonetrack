package com.gideontek.phonetrack

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatTest {

    @Test fun `under a kilometer is whole meters`() {
        assertEquals("0m", DistanceFormat.compact(0))
        assertEquals("1m", DistanceFormat.compact(1))
        assertEquals("200m", DistanceFormat.compact(200))
        assertEquals("999m", DistanceFormat.compact(999))
        assertEquals("999m", DistanceFormat.compact(999.4))
    }

    @Test fun `a kilometer up to ten has one decimal, and no decimal when it is a whole number`() {
        assertEquals("1km", DistanceFormat.compact(1000))
        assertEquals("1.2km", DistanceFormat.compact(1200))
        assertEquals("1.5km", DistanceFormat.compact(1500))
        assertEquals("2km", DistanceFormat.compact(2000))
        assertEquals("9.9km", DistanceFormat.compact(9949))
    }

    @Test fun `from ten kilometers it is whole kilometers`() {
        assertEquals("10km", DistanceFormat.compact(9950))
        assertEquals("10km", DistanceFormat.compact(10_000))
        assertEquals("10km", DistanceFormat.compact(10_499))
        assertEquals("11km", DistanceFormat.compact(10_500))
        assertEquals("50km", DistanceFormat.compact(50_000))
        assertEquals("950km", DistanceFormat.compact(950_000))
    }

    @Test fun `rounding comes before the unit, so there is no 1000m and no 10_0km`() {
        assertEquals("1km", DistanceFormat.compact(999.6))
        assertEquals("1km", DistanceFormat.compact(1049))
        assertEquals("1.1km", DistanceFormat.compact(1050))
        assertEquals("10km", DistanceFormat.compact(9999))
    }

    @Test fun `a negative distance is zero`() = assertEquals("0m", DistanceFormat.compact(-5.0))

    @Test fun `the separator is a dot whatever the default locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.2km", DistanceFormat.compact(1200))
        } finally {
            Locale.setDefault(original)
        }
    }
}
