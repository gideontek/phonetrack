package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SmsLengthTest {

    @Test fun `plain ASCII counts one septet per character`() { assertEquals(5, SmsLength.gsm7Septets("hello")) }
    @Test fun `a newline counts one`() { assertEquals(3, SmsLength.gsm7Septets("a\nb")) }
    @Test fun `extension characters count two`() {
        assertEquals(2, SmsLength.gsm7Septets("["))
        assertEquals(2, SmsLength.gsm7Septets("]"))
        assertEquals(2, SmsLength.gsm7Septets("{"))
        assertEquals(2, SmsLength.gsm7Septets("}"))
        assertEquals(2, SmsLength.gsm7Septets("\\"))
        assertEquals(2, SmsLength.gsm7Septets("^"))
        assertEquals(2, SmsLength.gsm7Septets("~"))
        assertEquals(2, SmsLength.gsm7Septets("|"))
    }
    @Test fun `common URL characters are basic`() {
        assertEquals(10, SmsLength.gsm7Septets("a/b?c=d&e#"))
        assertEquals(3, SmsLength.gsm7Septets(":%_"))
    }
    @Test fun `characters outside the alphabet make it non-GSM-7`() {
        assertNull(SmsLength.gsm7Septets("⇗"))
        assertNull(SmsLength.gsm7Septets("é"))
        assertNull(SmsLength.gsm7Septets("`"))
        assertNull(SmsLength.gsm7Septets("a\u0007b"))
    }

    @Test fun `empty fits`() { assertTrue(SmsLength.fitsOneSms("")) }

    @Test fun `160 plain characters fit and 161 do not`() {
        assertTrue(SmsLength.fitsOneSms("a".repeat(160)))
        assertFalse(SmsLength.fitsOneSms("a".repeat(161)))
    }

    @Test fun `extension characters use up the budget twice as fast`() {
        assertTrue(SmsLength.fitsOneSms("[".repeat(80)))
        assertFalse(SmsLength.fitsOneSms("[".repeat(81)))
        assertTrue(SmsLength.fitsOneSms("a".repeat(158) + "["))
        assertFalse(SmsLength.fitsOneSms("a".repeat(159) + "["))
    }

    @Test fun `a message with one arrow switches to UCS-2 and the 70 limit`() {
        assertTrue(SmsLength.fitsOneSms("⇗" + "a".repeat(69)))
        assertFalse(SmsLength.fitsOneSms("⇗" + "a".repeat(70)))
    }

    @Test fun `a long plain-ASCII message is not rescued by the UCS-2 limit logic`() {
        assertFalse(SmsLength.fitsOneSms("a".repeat(100) + "⇗"))
    }
}
