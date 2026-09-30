package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SmsLimitsTest {

    @Test
    fun `plain keyword is unchanged`() {
        assertEquals("phonetrack", SmsLimits.sanitizeKeyword("phonetrack"))
    }

    @Test
    fun `whitespace is removed so the keyword stays a single token`() {
        assertEquals("mytrack", SmsLimits.sanitizeKeyword(" my track\t"))
    }

    @Test
    fun `leading bracket is stripped so it cannot match our own replies`() {
        assertEquals("phonetrack]", SmsLimits.sanitizeKeyword("[phonetrack]"))
    }

    @Test
    fun `multiple leading brackets are stripped`() {
        assertEquals("abc", SmsLimits.sanitizeKeyword("[[abc"))
    }

    @Test
    fun `bracket inside the keyword is kept`() {
        assertEquals("a[b", SmsLimits.sanitizeKeyword("a[b"))
    }

    @Test
    fun `empty stays empty`() {
        assertEquals("", SmsLimits.sanitizeKeyword(""))
    }

    @Test
    fun `body limit is 320`() {
        assertEquals(320, SmsLimits.MAX_BODY)
    }
}
