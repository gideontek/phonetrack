package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SubscriptionLogicTest {

    private fun sub(
        freqMinutes: Int = 15,
        lastSentAt: Long = 0L,
        expiresAt: Long = Long.MAX_VALUE,
        lastLat: Double = 0.0,
        lastLon: Double = 0.0,
        distMeters: Int = 200
    ) = Subscription(
        number = "+1234567890",
        distMeters = distMeters,
        freqMinutes = freqMinutes,
        durationHours = 4,
        subscribedAt = 0L,
        expiresAt = expiresAt,
        lastLat = lastLat,
        lastLon = lastLon,
        lastSentAt = lastSentAt
    )

    // -------------------------------------------------------------------------
    // nextTickDelay
    // -------------------------------------------------------------------------

    @Test
    fun `nextTickDelay empty list returns MAX`() {
        assertEquals(SubscriptionLogic.MAX_TICK_MS, SubscriptionLogic.nextTickDelay(emptyList(), 0L))
    }

    @Test
    fun `nextTickDelay past due clamps to MIN`() {
        // lastSentAt=0, freqMinutes=15 → nextDueAt=900_000; now=1_000_000 → delay=-100_000 → MIN
        val s = sub(freqMinutes = 15, lastSentAt = 0L)
        assertEquals(SubscriptionLogic.MIN_TICK_MS, SubscriptionLogic.nextTickDelay(listOf(s), 1_000_000L))
    }

    @Test
    fun `nextTickDelay future within bounds`() {
        // lastSentAt=0, freqMinutes=5 → nextDueAt=300_000; now=0 → delay=300_000 (in bounds)
        val s = sub(freqMinutes = 5, lastSentAt = 0L)
        assertEquals(300_000L, SubscriptionLogic.nextTickDelay(listOf(s), 0L))
    }

    @Test
    fun `nextTickDelay far future clamps to MAX`() {
        // lastSentAt=0, freqMinutes=60 → nextDueAt=3_600_000; now=0 → delay=3_600_000 > MAX → MAX
        val s = sub(freqMinutes = 60, lastSentAt = 0L)
        assertEquals(SubscriptionLogic.MAX_TICK_MS, SubscriptionLogic.nextTickDelay(listOf(s), 0L))
    }

    @Test
    fun `nextTickDelay multi-sub picks soonest`() {
        val s1 = sub(freqMinutes = 10, lastSentAt = 0L) // nextDueAt=600_000
        val s2 = sub(freqMinutes = 5, lastSentAt = 0L)  // nextDueAt=300_000
        assertEquals(300_000L, SubscriptionLogic.nextTickDelay(listOf(s1, s2), 0L))
    }

    @Test
    fun `nextTickDelay exactly at MIN boundary`() {
        // freqMinutes=1 → nextDueAt=60_000; now=0 → delay=60_000 = MIN_TICK_MS
        val s = sub(freqMinutes = 1, lastSentAt = 0L)
        assertEquals(SubscriptionLogic.MIN_TICK_MS, SubscriptionLogic.nextTickDelay(listOf(s), 0L))
    }

    @Test
    fun `nextTickDelay exactly at MAX boundary`() {
        // freqMinutes=15 → nextDueAt=900_000; now=0 → delay=900_000 = MAX_TICK_MS
        val s = sub(freqMinutes = 15, lastSentAt = 0L)
        assertEquals(SubscriptionLogic.MAX_TICK_MS, SubscriptionLogic.nextTickDelay(listOf(s), 0L))
    }

    @Test
    fun `nextTickDelay uses lastSentAt offset correctly`() {
        // lastSentAt=200_000, freqMinutes=5 → nextDueAt=500_000; now=300_000 → delay=200_000
        val s = sub(freqMinutes = 5, lastSentAt = 200_000L)
        assertEquals(200_000L, SubscriptionLogic.nextTickDelay(listOf(s), 300_000L))
    }

    // -------------------------------------------------------------------------
    // shouldSend
    // -------------------------------------------------------------------------

    @Test
    fun `shouldSend first update (lat=0 lon=0) always true`() {
        assertTrue(SubscriptionLogic.shouldSend(0.0, 0.0, 0f, 200))
    }

    @Test
    fun `shouldSend first update ignores large threshold`() {
        assertTrue(SubscriptionLogic.shouldSend(0.0, 0.0, 0f, 10_000))
    }

    @Test
    fun `shouldSend below threshold returns false`() {
        assertFalse(SubscriptionLogic.shouldSend(1.0, 1.0, 50f, 200))
    }

    @Test
    fun `shouldSend at threshold returns true`() {
        assertTrue(SubscriptionLogic.shouldSend(1.0, 1.0, 200f, 200))
    }

    @Test
    fun `shouldSend above threshold returns true`() {
        assertTrue(SubscriptionLogic.shouldSend(1.0, 1.0, 300f, 200))
    }

    @Test
    fun `shouldSend threshold zero always true`() {
        assertTrue(SubscriptionLogic.shouldSend(1.0, 1.0, 0f, 0))
    }

    // -------------------------------------------------------------------------
    // expiredSubs
    // -------------------------------------------------------------------------

    @Test
    fun `expiredSubs mix returns only expired`() {
        val expired = sub(expiresAt = 500L)
        val active = sub(expiresAt = 2000L)
        assertEquals(listOf(expired), SubscriptionLogic.expiredSubs(listOf(expired, active), 1000L))
    }

    @Test
    fun `expiredSubs all expired`() {
        val s1 = sub(expiresAt = 100L)
        val s2 = sub(expiresAt = 200L)
        assertEquals(listOf(s1, s2), SubscriptionLogic.expiredSubs(listOf(s1, s2), 1000L))
    }

    @Test
    fun `expiredSubs none expired`() {
        val s = sub(expiresAt = 2000L)
        assertEquals(emptyList<Subscription>(), SubscriptionLogic.expiredSubs(listOf(s), 1000L))
    }

    // -------------------------------------------------------------------------
    // dueSubs
    // -------------------------------------------------------------------------

    @Test
    fun `dueSubs none due`() {
        // now=100, lastSentAt=0, freqMinutes=15 → 100 < 900_000 → not due
        val s = sub(freqMinutes = 15, lastSentAt = 0L)
        assertEquals(emptyList<Subscription>(), SubscriptionLogic.dueSubs(listOf(s), 100L))
    }

    @Test
    fun `dueSubs some due`() {
        val due = sub(freqMinutes = 15, lastSentAt = 0L)          // 1_000_000 >= 900_000 ✓
        val notDue = sub(freqMinutes = 15, lastSentAt = 999_000L) // 1_000 < 900_000 ✗
        assertEquals(listOf(due), SubscriptionLogic.dueSubs(listOf(due, notDue), 1_000_000L))
    }

    @Test
    fun `dueSubs all due`() {
        val s1 = sub(freqMinutes = 1, lastSentAt = 0L)  // 1_000_000 >= 60_000 ✓
        val s2 = sub(freqMinutes = 5, lastSentAt = 0L)  // 1_000_000 >= 300_000 ✓
        assertEquals(listOf(s1, s2), SubscriptionLogic.dueSubs(listOf(s1, s2), 1_000_000L))
    }

    // -------------------------------------------------------------------------
    // bearingToArrow
    // -------------------------------------------------------------------------

    @Test fun `bearingToArrow 0 north`()          { assertEquals("⇑", SubscriptionLogic.bearingToArrow(0f)) }
    @Test fun `bearingToArrow 45 northeast`()     { assertEquals("⇗", SubscriptionLogic.bearingToArrow(45f)) }
    @Test fun `bearingToArrow 90 east`()          { assertEquals("⇒", SubscriptionLogic.bearingToArrow(90f)) }
    @Test fun `bearingToArrow 135 southeast`()    { assertEquals("⇘", SubscriptionLogic.bearingToArrow(135f)) }
    @Test fun `bearingToArrow 180 south`()        { assertEquals("⇓", SubscriptionLogic.bearingToArrow(180f)) }
    @Test fun `bearingToArrow 225 southwest`()    { assertEquals("⇙", SubscriptionLogic.bearingToArrow(225f)) }
    @Test fun `bearingToArrow 270 west`()         { assertEquals("⇐", SubscriptionLogic.bearingToArrow(270f)) }
    @Test fun `bearingToArrow 315 northwest`()    { assertEquals("⇖", SubscriptionLogic.bearingToArrow(315f)) }
    @Test fun `bearingToArrow 22_4 still north`() { assertEquals("⇑", SubscriptionLogic.bearingToArrow(22.4f)) }
    @Test fun `bearingToArrow 22_5 tips northeast`() { assertEquals("⇗", SubscriptionLogic.bearingToArrow(22.5f)) }
    @Test fun `bearingToArrow negative wraps`()   { assertEquals("⇑", SubscriptionLogic.bearingToArrow(-22.4f)) }
    @Test fun `bearingToArrow 360 wraps to north`() { assertEquals("⇑", SubscriptionLogic.bearingToArrow(360f)) }
}
