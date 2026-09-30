package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class PinLockoutTest {

    private val minute = 60_000L

    /** Applies [n] wrong PINs at time [now] and returns the resulting state. */
    private fun fail(state: PinLockState, n: Int, now: Long): PinLockState {
        var s = state
        repeat(n) { s = PinLockout.onFailure(s, now) }
        return s
    }

    @Test fun `a fresh state is not locked and has a full round of attempts`() {
        val s = PinLockState()
        assertFalse(PinLockout.isLocked(s, 1_000L))
        assertEquals(5, PinLockout.attemptsLeft(s))
    }

    @Test fun `failures before the fifth only count down the attempts`() {
        var s = PinLockState()
        for (i in 1..4) {
            s = PinLockout.onFailure(s, 1_000L)
            assertFalse(PinLockout.isLocked(s, 1_000L))
            assertEquals(5 - i, PinLockout.attemptsLeft(s))
        }
    }

    @Test fun `the fifth failure locks for one minute`() {
        val s = fail(PinLockState(), 5, 10_000L)
        assertTrue(PinLockout.isLocked(s, 10_000L))
        assertEquals(minute, PinLockout.remainingMs(s, 10_000L))
        assertEquals(1, s.level)
        assertEquals(0, s.failures)
    }

    @Test fun `the lock ends after its duration`() {
        val s = fail(PinLockState(), 5, 10_000L)
        assertTrue(PinLockout.isLocked(s, 10_000L + minute - 1))
        assertFalse(PinLockout.isLocked(s, 10_000L + minute))
    }

    @Test fun `lockouts escalate one, five, fifteen, sixty, then stay at sixty`() {
        var s = PinLockState()
        var now = 0L
        val expected = listOf(1L, 5L, 15L, 60L, 60L, 60L).map { it * minute }
        for (want in expected) {
            s = fail(s, 5, now)
            assertEquals(want, PinLockout.remainingMs(s, now))
            now += want            // wait the lock out, then start the next round
        }
        assertEquals(6, s.level)
    }

    @Test fun `each round needs five fresh failures after a lock expires`() {
        var s = fail(PinLockState(), 5, 0L)
        val after = minute + 1
        s = fail(s, 4, after)
        assertFalse(PinLockout.isLocked(s, after))
        assertEquals(1, PinLockout.attemptsLeft(s))
        s = PinLockout.onFailure(s, after)
        assertTrue(PinLockout.isLocked(s, after))
        assertEquals(5 * minute, PinLockout.remainingMs(s, after))
    }

    @Test fun `a correct PIN resets failures and the escalation level`() {
        var s = fail(PinLockState(), 5, 0L)
        s = fail(s, 3, minute + 1)
        assertEquals(PinLockState(), PinLockout.onSuccess())
        assertEquals(0, PinLockout.onSuccess().level)
    }

    @Test fun `remaining time is capped so a backwards clock cannot lock the owner out for long`() {
        val s = PinLockState(failures = 0, lockedUntil = 10_000_000_000L, level = 1)
        assertEquals(PinLockout.MAX_LOCK_MS, PinLockout.remainingMs(s, 0L))
        assertTrue(PinLockout.isLocked(s, 0L))
    }

    @Test fun `remaining time is never negative`() {
        assertEquals(0L, PinLockout.remainingMs(PinLockState(lockedUntil = 5L), 1_000L))
    }

    @Test fun `durations follow the table`() {
        assertEquals(1 * minute, PinLockout.durationMs(1))
        assertEquals(5 * minute, PinLockout.durationMs(2))
        assertEquals(15 * minute, PinLockout.durationMs(3))
        assertEquals(60 * minute, PinLockout.durationMs(4))
        assertEquals(60 * minute, PinLockout.durationMs(50))
        assertEquals(1 * minute, PinLockout.durationMs(0))
    }

    @Test fun `formatRemaining rounds up to whole minutes`() {
        assertEquals("1 minute", PinLockout.formatRemaining(1L))
        assertEquals("1 minute", PinLockout.formatRemaining(minute))
        assertEquals("2 minutes", PinLockout.formatRemaining(minute + 1))
        assertEquals("5 minutes", PinLockout.formatRemaining(5 * minute))
        assertEquals("60 minutes", PinLockout.formatRemaining(60 * minute))
        assertEquals("1 minute", PinLockout.formatRemaining(0L))
    }
}
