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

    @Test fun `info for a short GSM-7 text`() {
        val i = SmsLength.info("hello")
        assertEquals(SmsLength.Encoding.GSM7, i.encoding)
        assertEquals(5, i.units); assertEquals(160, i.limit); assertEquals(1, i.smsCount)
    }

    @Test fun `info counts extension characters as two units`() {
        assertEquals(5, SmsLength.info("[a]").units)
    }

    @Test fun `info for a UCS-2 text`() {
        val i = SmsLength.info("⇗395m")
        assertEquals(SmsLength.Encoding.UCS2, i.encoding)
        assertEquals(5, i.units); assertEquals(70, i.limit); assertEquals(1, i.smsCount)
    }

    @Test fun `a long GSM-7 text splits into 153-unit parts`() {
        assertEquals(1, SmsLength.info("a".repeat(160)).smsCount)
        assertEquals(2, SmsLength.info("a".repeat(161)).smsCount)
        assertEquals(2, SmsLength.info("a".repeat(306)).smsCount)
        assertEquals(3, SmsLength.info("a".repeat(307)).smsCount)
    }

    @Test fun `a long UCS-2 text splits into 67-unit parts`() {
        assertEquals(1, SmsLength.info("⇗" + "a".repeat(69)).smsCount)
        assertEquals(2, SmsLength.info("⇗" + "a".repeat(70)).smsCount)
        assertEquals(2, SmsLength.info("⇗" + "a".repeat(133)).smsCount)
        assertEquals(3, SmsLength.info("⇗" + "a".repeat(134)).smsCount)
    }

    @Test fun `info agrees with fitsOneSms`() {
        for (s in listOf("", "a".repeat(160), "a".repeat(161), "⇗", "⇗" + "a".repeat(70), "[".repeat(80), "[".repeat(81))) {
            assertEquals(s, SmsLength.fitsOneSms(s), SmsLength.info(s).smsCount <= 1)
        }
    }
}
