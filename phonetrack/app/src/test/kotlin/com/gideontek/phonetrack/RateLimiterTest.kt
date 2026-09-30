package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class RateLimiterTest {

    private val hour = SmsLimits.RATE_WINDOW_MS

    /** Feeds [hits] (timestamps) through the limiter for one key and returns every decision. */
    private fun run(limit: Int, hits: List<Long>, key: String = "k"): List<RateDecision> {
        var state = emptyMap<String, RateWindow>()
        return hits.map { now ->
            RateLimiter.check(state, key, now, limit).also { state = it.state }
        }
    }

    @Test fun `hits up to the limit are allowed`() {
        val d = run(3, listOf(0L, 1L, 2L))
        assertTrue(d.all { it.allowed })
        assertTrue(d.none { it.sendNotice })
    }

    @Test fun `the hit after the limit is denied and sends exactly one notice`() {
        val d = run(3, listOf(0L, 1L, 2L, 3L, 4L, 5L))
        assertEquals(listOf(true, true, true, false, false, false), d.map { it.allowed })
        assertEquals(listOf(false, false, false, true, false, false), d.map { it.sendNotice })
    }

    @Test fun `a limit of one allows a single hit per window`() {
        val d = run(1, listOf(0L, 10L))
        assertEquals(listOf(true, false), d.map { it.allowed })
    }

    @Test fun `the window resets after the window length and the notice can fire again`() {
        val d = run(2, listOf(0L, 1L, 2L, hour, hour + 1, hour + 2))
        assertEquals(listOf(true, true, false, true, true, false), d.map { it.allowed })
        assertEquals(listOf(false, false, true, false, false, true), d.map { it.sendNotice })
    }

    @Test fun `one millisecond before the window ends is still the same window`() {
        val d = run(1, listOf(0L, hour - 1))
        assertFalse(d[1].allowed)
    }

    @Test fun `keys are counted independently`() {
        var state = emptyMap<String, RateWindow>()
        val a1 = RateLimiter.check(state, "a", 0L, 1).also { state = it.state }
        val b1 = RateLimiter.check(state, "b", 1L, 1).also { state = it.state }
        val a2 = RateLimiter.check(state, "a", 2L, 1).also { state = it.state }
        assertTrue(a1.allowed)
        assertTrue(b1.allowed)
        assertFalse(a2.allowed)
    }

    @Test fun `denied hits do not extend the window`() {
        val d = run(1, listOf(0L, 100L, 200L, hour))
        assertEquals(listOf(true, false, false, true), d.map { it.allowed })
    }

    @Test fun `keys untouched for two windows are forgotten`() {
        var state = emptyMap<String, RateWindow>()
        state = RateLimiter.check(state, "old", 0L, 5).state
        state = RateLimiter.check(state, "new", 2 * hour, 5).state
        assertFalse(state.containsKey("old"))
        assertTrue(state.containsKey("new"))
    }

    @Test fun `a key used within two windows is kept`() {
        var state = emptyMap<String, RateWindow>()
        state = RateLimiter.check(state, "old", 0L, 5).state
        state = RateLimiter.check(state, "new", 2 * hour - 1, 5).state
        assertTrue(state.containsKey("old"))
    }

    @Test fun `a clock that moves backwards starts a fresh window instead of locking the key out`() {
        var state = emptyMap<String, RateWindow>()
        state = RateLimiter.check(state, "k", 10 * hour, 1).state
        val denied = RateLimiter.check(state, "k", 10 * hour + 1, 1)
        assertFalse(denied.allowed)
        val afterClockChange = RateLimiter.check(denied.state, "k", 5 * hour, 1)
        assertTrue(afterClockChange.allowed)
    }

    @Test fun `the input state is not modified`() {
        val state = mapOf("k" to RateWindow(0L, 1, false))
        RateLimiter.check(state, "k", 1L, 5)
        assertEquals(mapOf("k" to RateWindow(0L, 1, false)), state)
    }

    @Test fun `the default limit allows exactly twenty commands`() {
        val d = run(SmsLimits.DEFAULT_RATE_LIMIT_PER_HOUR, (0L until 21L).toList())
        assertEquals(20, d.count { it.allowed })
        assertFalse(d[20].allowed)
        assertTrue(d[20].sendNotice)
    }
}
