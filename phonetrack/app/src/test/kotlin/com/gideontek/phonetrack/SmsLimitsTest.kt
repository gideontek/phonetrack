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

    @Test
    fun `rate limit is at least one`() {
        assertEquals(1, SmsLimits.coerceRateLimit(0))
        assertEquals(1, SmsLimits.coerceRateLimit(-5))
        assertEquals(20, SmsLimits.coerceRateLimit(20))
        assertEquals(500, SmsLimits.coerceRateLimit(500))
    }

    @Test
    fun `max subscriptions is clamped to 1 through the ceiling`() {
        assertEquals(1, SmsLimits.coerceMaxSubscriptions(0))
        assertEquals(1, SmsLimits.coerceMaxSubscriptions(-3))
        assertEquals(10, SmsLimits.coerceMaxSubscriptions(10))
        assertEquals(20, SmsLimits.coerceMaxSubscriptions(999))
    }

    @Test
    fun `the defaults are the agreed values`() {
        assertEquals(20, SmsLimits.DEFAULT_RATE_LIMIT_PER_HOUR)
        assertEquals(10, SmsLimits.DEFAULT_MAX_SUBSCRIPTIONS)
        assertEquals(50, SmsLimits.MAX_PENDING)
        assertEquals(10, SmsLimits.NEW_PENDING_PER_HOUR)
    }
}
