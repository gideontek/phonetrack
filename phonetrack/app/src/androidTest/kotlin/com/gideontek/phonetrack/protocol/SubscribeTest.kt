package com.gideontek.phonetrack.protocol

import com.gideontek.phonetrack.PhoneNumber
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `subscribe`: flag parsing end to end, stored state, replace and the subscription cap. */
class SubscribeTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true)

    private val usage = "Usage: phonetrack subscribe [--dist M] [--freq MIN] [--time H]"

    /** A valid subscribe is acknowledged, then an immediate fix follows: two replies. */
    private fun subscribe(s: Scenario, args: String = ""): List<String> =
        s.ask("phonetrack subscribe $args".trim(), expect = 2)

    @Test
    fun defaultsAre200MetresEvery15MinutesFor4Hours() {
        val s = Scenario().ready()
        val replies = subscribe(s)
        assertEquals(replies.toString(), 2, replies.size)
        assertTrue(replies[0], replies[0].startsWith(
            "[PhoneTrack] Subscribed: update every 15 min, only if moved 200m+, for 4h (ends "))
        assertTrue(replies[0], replies[0].endsWith("Text \"phonetrack unsubscribe\" to stop."))
        assertTrue("the second reply is the immediate fix: ${replies[1]}", replies[1].contains("Lat: 37.7749"))
        val sub = TestState.subscriptions().single()
        assertEquals(PhoneNumber.normalize(s.me), sub.number)
        assertEquals(200, sub.distMeters)
        assertEquals(15, sub.freqMinutes)
        assertEquals(4, sub.durationHours)
        assertEquals(4 * 3_600_000L, sub.expiresAt - sub.subscribedAt)
    }

    @Test
    fun everyFlagAtItsMinimumAndMaximumIsAccepted() {
        data class Case(val args: String, val dist: Int, val freq: Int, val hours: Int, val ackPart: String)
        val cases = listOf(
            Case("--dist 0 --freq 1 --time 1", 0, 1, 1, "update every 1 min, regardless of movement, for 1h"),
            Case("--dist 50000 --freq 1440 --time 168", 50000, 1440, 168, "update every 1440 min, only if moved 50000m+, for 168h"),
        )
        for (c in cases) {
            TestState.seedSubscriptions()
            val s = Scenario().ready()
            val replies = subscribe(s, c.args)
            assertTrue("${c.args}: $replies", replies[0].contains(c.ackPart))
            val sub = TestState.subscriptions().single()
            assertEquals(c.args, Triple(c.dist, c.freq, c.hours), Triple(sub.distMeters, sub.freqMinutes, sub.durationHours))
        }
    }

    @Test
    fun flagsMayComeInAnyOrderInAnyCaseAndWithTypographicDashes() {
        val s = Scenario().ready()
        subscribe(s, "--TIME 2 --Dist 100")
        TestState.subscriptions().single().let { assertEquals(Triple(100, 15, 2), Triple(it.distMeters, it.freqMinutes, it.durationHours)) }
        subscribe(s, "—freq 30 –time 3")
        TestState.subscriptions().single().let { assertEquals(Triple(200, 30, 3), Triple(it.distMeters, it.freqMinutes, it.durationHours)) }
    }

    @Test
    fun invalidSubscribeRequestsGetTheExactErrorAndStoreNothing() {
        val cases = listOf(
            "--dist" to "--dist needs a whole number",
            "--dist abc" to "--dist needs a whole number",
            "--dist 1.5" to "--dist needs a whole number",
            "--dist -1" to "--dist must be 0-50000 (metres)",
            "--dist 50001" to "--dist must be 0-50000 (metres)",
            "--freq 0" to "--freq must be 1-1440 (minutes)",
            "--freq 1441" to "--freq must be 1-1440 (minutes)",
            "--time 0" to "--time must be 1-168 (hours)",
            "--time 169" to "--time must be 1-168 (hours)",
            "--dist 5 --dist 6" to "--dist given twice",
            "--foo 1" to "Unknown option --foo",
            "fast" to "Unknown option fast",
        )
        val s = Scenario().ready()
        for ((args, message) in cases) {
            val reply = assertOneReply(s.ask("phonetrack subscribe $args"))
            assertEquals("subscribe $args", "[PhoneTrack] $message. $usage", reply)
        }
        assertTrue(TestState.subscriptions().isEmpty())
    }

    @Test
    fun subscribingAgainReplacesTheExistingSubscription() {
        val s = Scenario().ready()
        TestState.seedSubscriptions(TestState.subscription(s.me))
        subscribe(s, "--freq 5")
        val sub = TestState.subscriptions().single()
        assertEquals(5, sub.freqMinutes)
    }

    @Test
    fun aSubscriptionIsRefusedWhenTheCapIsReached() {
        val s = Scenario().ready()
        TestState.prefs.edit().putInt("max_subscriptions", 2).commit()
        TestState.seedSubscriptions(TestState.subscription("+15550000001"), TestState.subscription("+15550000002"))
        val reply = assertOneReply(s.ask("phonetrack subscribe"))
        assertEquals("[PhoneTrack] Too many active subscriptions on this phone. Try again later.", reply)
        assertEquals(2, TestState.subscriptions().size)
    }

    @Test
    fun aSubscriberAtTheCapMayStillReplaceTheirOwn() {
        val s = Scenario().ready()
        TestState.prefs.edit().putInt("max_subscriptions", 2).commit()
        TestState.seedSubscriptions(TestState.subscription("+15550000001"), TestState.subscription(s.me))
        subscribe(s, "--freq 7")
        assertEquals(setOf(7, 15), TestState.subscriptions().map { it.freqMinutes }.toSet())
    }

    @Test
    fun oneSlotBelowTheCapStillAcceptsANewSubscriber() {
        val s = Scenario().ready()
        TestState.prefs.edit().putInt("max_subscriptions", 2).commit()
        TestState.seedSubscriptions(TestState.subscription("+15550000001"))
        subscribe(s)
        assertEquals(2, TestState.subscriptions().size)
    }

    @Test
    fun expiredStoredSubscriptionsDoNotCountTowardsTheCap() {
        val s = Scenario().ready()
        TestState.prefs.edit().putInt("max_subscriptions", 1).commit()
        TestState.seedSubscriptions(TestState.subscription("+15550000001", expiresInMs = -60_000))
        subscribe(s)
        assertTrue(TestState.subscriptions().any { PhoneNumber.matches(it.number, s.me) })
    }

    @Test
    fun loweringTheCapCancelsNothingThatIsAlreadyRunning() {
        val s = Scenario().ready()
        TestState.seedSubscriptions(*Array(5) { TestState.subscription("+1555000000$it") })
        TestState.prefs.edit().putInt("max_subscriptions", 1).commit()
        s.ask("phonetrack help")
        assertEquals(5, TestState.subscriptions().size)
    }
}
