package com.gideontek.phonetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainUiStateTest {

    private fun status(
        enabled: Boolean = true,
        sms: Boolean = true,
        loc: Boolean = true,
        bg: Boolean = true,
        services: Boolean = true
    ) = StatusSummary.from(enabled, sms, loc, bg, services)

    @Test fun allGood() {
        val s = status()
        assertEquals("Listening", s.headline)
        assertNull(s.problem)
        assertNull(s.fix)
    }

    @Test fun offIsNotNaggedAboutPermissions() {
        val s = status(enabled = false, sms = false, loc = false, bg = false, services = false)
        assertEquals("Listening is off", s.headline)
        assertNull(s.fix)
    }

    @Test fun missingSmsWinsOverEverything() =
        assertEquals(StatusFix.RequestSms, status(sms = false, loc = false, bg = false, services = false).fix)

    @Test fun locationBeforeBackground() =
        assertEquals(StatusFix.RequestLocation, status(loc = false, bg = false).fix)

    @Test fun backgroundBeforeServices() =
        assertEquals(StatusFix.RequestBgLocation, status(bg = false, services = false).fix)

    @Test fun servicesOff() =
        assertEquals(StatusFix.OpenLocationSettings, status(services = false).fix)

    @Test fun problemTextAccompaniesFix() {
        val s = status(bg = false)
        assertEquals("Background location is off, so replies can't start.", s.problem)
    }

    // --- RelativeTime ---

    @Test fun agoBoundaries() {
        assertEquals("just now", RelativeTime.ago(100_000, 100_000))
        assertEquals("just now", RelativeTime.ago(159_999, 100_000))
        assertEquals("1 min ago", RelativeTime.ago(160_000, 100_000))
        assertEquals("59 min ago", RelativeTime.ago(100_000 + 59 * 60_000L + 59_000L, 100_000))
        assertEquals("1 h ago", RelativeTime.ago(100_000 + 3_600_000L, 100_000))
        assertEquals("23 h ago", RelativeTime.ago(100_000 + 23 * 3_600_000L + 1L, 100_000))
        assertEquals("1 d ago", RelativeTime.ago(100_000 + 86_400_000L, 100_000))
        assertEquals("3 d ago", RelativeTime.ago(100_000 + 3 * 86_400_000L + 5L, 100_000))
    }

    @Test fun agoClockSkewIsJustNow() = assertEquals("just now", RelativeTime.ago(100, 5_000))

    @Test fun leftBoundaries() {
        assertEquals("less than a minute left", RelativeTime.left(59_999))
        assertEquals("less than a minute left", RelativeTime.left(-5))
        assertEquals("1 min left", RelativeTime.left(60_000))
        assertEquals("59 min left", RelativeTime.left(59 * 60_000L + 59_000L))
        assertEquals("1 h left", RelativeTime.left(3_600_000L))
        assertEquals("2 h 10 min left", RelativeTime.left(2 * 3_600_000L + 10 * 60_000L + 30_000L))
    }

    // --- SubscriptionView ---

    private fun sub(
        number: String = "+15550102290",
        dist: Int = 200,
        freq: Int = 15,
        hours: Int = 4,
        expiresAt: Long
    ) = Subscription(number, dist, freq, hours, expiresAt - hours * 3_600_000L, expiresAt, 0.0, 0.0, 0L)

    @Test fun cadenceWithDistance() {
        val v = SubscriptionView.of(sub(expiresAt = 10 * 3_600_000L), now = 0L)
        assertEquals("every 15 min · moves of 200 m+", v.cadence)
    }

    @Test fun cadenceAnyMovementWhenDistanceZero() {
        val v = SubscriptionView.of(sub(dist = 0, freq = 5, expiresAt = 10 * 3_600_000L), now = 0L)
        assertEquals("every 5 min · any movement", v.cadence)
    }

    @Test fun fractionAndTexts() {
        val total = 4 * 3_600_000L
        val v = SubscriptionView.of(sub(expiresAt = total), now = total / 2)
        assertEquals(0.5f, v.fraction, 0.0001f)
        assertEquals("2 h left", v.leftText)
        assertEquals("of 4 h", v.totalText)
    }

    @Test fun fractionIsClamped() {
        val total = 4 * 3_600_000L
        // now before the subscription even started (clock change): capped at 1
        assertEquals(1f, SubscriptionView.of(sub(expiresAt = total), now = -total).fraction, 0f)
        assertEquals(0f, SubscriptionView.of(sub(expiresAt = total), now = total + 1).fraction, 0f)
    }

    @Test fun activeDropsExpiredAndSortsBySoonestEnd() {
        val now = 1_000_000L
        val list = listOf(
            sub(number = "late", expiresAt = now + 5_000_000L),
            sub(number = "expired", expiresAt = now - 1L),
            sub(number = "at-now", expiresAt = now),
            sub(number = "soon", expiresAt = now + 60_000L)
        )
        assertEquals(listOf("soon", "late"), SubscriptionView.active(list, now).map { it.number })
    }

    // --- KnownNumbers ---

    @Test fun splitGroupsAndOrdersPendingNewestFirst() {
        val entries = listOf(
            ApprovalEntry("a", ApprovalState.PENDING, 1, 10),
            ApprovalEntry("b", ApprovalState.APPROVED, 1, 99),
            ApprovalEntry("c", ApprovalState.PENDING, 1, 30),
            ApprovalEntry("d", ApprovalState.BLOCKED, 1, 5),
            ApprovalEntry("e", ApprovalState.PENDING, 0, 0)
        )
        val k = KnownNumbers.split(entries)
        assertEquals(listOf("c", "a", "e"), k.pending.map { it.number })
        assertEquals(listOf("b"), k.approved.map { it.number })
        assertEquals(listOf("d"), k.blocked.map { it.number })
    }
}
