package com.gideontek.phonetrack.protocol

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.Bucket
import com.gideontek.phonetrack.EventEntry
import com.gideontek.phonetrack.EventLogLogic
import com.gideontek.phonetrack.EventLogStore
import com.gideontek.phonetrack.EventType
import com.gideontek.phonetrack.OutcomeKind
import com.gideontek.phonetrack.PhoneNumber
import com.gideontek.phonetrack.Reason
import com.gideontek.phonetrack.Subscription
import com.gideontek.phonetrack.SubscriptionManager
import com.gideontek.phonetrack.support.Loopback
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset

/** What the real receiver and service write to the event log, with the GPS fixed at 37.7749, -122.4194. */
class EventLogTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun log() = EventLogStore.read(context)

    private fun waitForLog(timeoutMs: Long = 25_000, predicate: (List<EventEntry>) -> Boolean): List<EventEntry> {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            val current = log()
            if (predicate(current)) return current
            SystemClock.sleep(300)
        }
        val current = log()
        assertTrue("log was:\n" + EventLogLogic.render(current, "t", 0, ZoneOffset.UTC), predicate(current))
        return current
    }

    private fun subscribe(s: Scenario, args: String = "") =
        s.ask("phonetrack subscribe $args".trim(), expect = 2)

    @Test
    fun nothingIsRecordedWhileTheLogIsOff() {
        // Make the service really tick and send, then check that it left no log behind.
        TestState.enableListening()
        val seeded = dueSub("+15550001111", 0.0, 0.0)
        TestState.seedSubscriptions(seeded)
        SubscriptionManager.ensureServiceRunning(context)
        val end = SystemClock.elapsedRealtime() + 25_000
        while (SystemClock.elapsedRealtime() < end &&
            TestState.subscriptions().none { it.lastSentAt > seeded.lastSentAt }
        ) SystemClock.sleep(300)
        assertTrue("the service never sent an update", TestState.subscriptions().any { it.lastSentAt > seeded.lastSentAt })
        subscribe(Scenario().ready())
        assertTrue(log().isEmpty())
        assertFalse(TestState.prefs.contains("event_log"))
        assertFalse(TestState.prefs.contains("event_log_aliases"))
    }

    @Test
    fun aSubscribeLogsTheRequestedSettingsAndASecondOneLogsAReplacement() {
        EventLogStore.setEnabled(context, true)
        val s = Scenario().ready()
        subscribe(s, "--freq 30 --dist 100 --time 2")
        subscribe(s, "--freq 20 --dist 50 --time 3")
        val entries = waitForLog { l -> l.any { it.type == EventType.REPLACED } }
        val first = entries.single { it.type == EventType.SUBSCRIBED }
        assertEquals(Triple(30, 100, 2), Triple(first.freqMin, first.distM, first.hours))
        val second = entries.single { it.type == EventType.REPLACED }
        assertEquals(Triple(20, 50, 3), Triple(second.freqMin, second.distM, second.hours))
        assertEquals("one sender, one alias", "Contact-A", first.who)
        assertEquals("Contact-A", second.who)
    }

    @Test
    fun anUnsubscribeIsLogged() {
        EventLogStore.setEnabled(context, true)
        val s = Scenario().ready()
        subscribe(s)
        s.ask("phonetrack unsubscribe")
        waitForLog { l -> l.any { it.type == EventType.CANCELLED } }
    }

    @Test
    fun aRejectedSubscribeLogsWhy() {
        EventLogStore.setEnabled(context, true)
        TestState.prefs.edit().putInt("max_subscriptions", 1).commit()
        TestState.seedSubscriptions(TestState.subscription("+15550001111"))
        val s = Scenario().ready()
        s.ask("phonetrack subscribe")
        waitForLog { l -> l.any { it.type == EventType.SUBSCRIBE_REJECTED && it.reason == Reason.LIMIT } }
        s.ask("phonetrack subscribe --dist abc")
        waitForLog { l -> l.any { it.type == EventType.SUBSCRIBE_REJECTED && it.reason == Reason.INVALID_FLAGS } }
    }

    private fun dueSub(number: String, lat: Double, lon: Double): Subscription {
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - 2 * 3_600_000L
        return Subscription(number, 200, 15, 4, twoHoursAgo, now + 2 * 3_600_000L, lat, lon, twoHoursAgo)
    }

    @Test
    fun aTickSummarisesWhoWasSentAndWhoWasSkipped() {
        EventLogStore.setEnabled(context, true)
        TestState.enableListening()
        TestState.seedSubscriptions(
            dueSub("+15550001111", 0.0, 0.0),                // never sent: always due and sent
            dueSub("+15550002222", 37.7749, -122.4194),      // already here: not moved
        )
        SubscriptionManager.ensureServiceRunning(context)
        val entries = waitForLog { l -> l.any { it.type == EventType.TICK && it.outcomes.size == 2 } }
        val tick = entries.first { it.type == EventType.TICK && it.outcomes.size == 2 }
        assertEquals(2, tick.n)
        assertEquals(2, tick.due)
        assertEquals(setOf(OutcomeKind.SENT, OutcomeKind.NOT_MOVED), tick.outcomes.map { it.kind }.toSet())
        val skipped = tick.outcomes.single { it.kind == OutcomeKind.NOT_MOVED }
        assertEquals(Bucket.UNDER_10, skipped.moved)
        assertEquals(200, skipped.needM)
        assertTrue("a fix was taken", tick.fixS != null)
        assertTrue(entries.any { it.type == EventType.SERVICE_START && it.reason == Reason.REQUESTED })
        assertTrue(entries.any { it.type == EventType.ENV })
    }

    @Test
    fun aDueSubscriberIsMarkedWhenTheListenerIsOff() {
        EventLogStore.setEnabled(context, true)
        TestState.seedSubscriptions(dueSub("+15550001111", 0.0, 0.0))
        SubscriptionManager.ensureServiceRunning(context)
        val entries = waitForLog { l ->
            l.any { it.type == EventType.TICK && it.outcomes.any { o -> o.kind == OutcomeKind.LISTENER_OFF } }
        }
        assertTrue(
            "nothing is sent while the listener is off; log was:\n" + EventLogLogic.render(entries, "t", 0, ZoneOffset.UTC),
            entries.none { it.outcomes.any { o -> o.kind == OutcomeKind.SENT } }
        )
    }

    @Test
    fun theStoredLogContainsNoNumberAndNoCoordinates() {
        EventLogStore.setEnabled(context, true)
        val s = Scenario().ready()
        subscribe(s)
        waitForLog { l -> l.any { it.type == EventType.SUBSCRIBED } }
        val raw = TestState.prefs.getString("event_log", "")!!
        val rendered = EventLogLogic.render(log(), "t", 0, ZoneOffset.UTC)
        val ownDigits = PhoneNumber.rateKey(Loopback.ownNumber)
        for (secret in listOf(ownDigits, "37.77", "122.41")) {
            assertFalse("'$secret' in stored log: $raw", raw.contains(secret))
            assertFalse("'$secret' in rendered log: $rendered", rendered.contains(secret))
        }
    }
}
