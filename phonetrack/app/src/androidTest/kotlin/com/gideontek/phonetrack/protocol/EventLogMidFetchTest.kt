package com.gideontek.phonetrack.protocol

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.EventLogStore
import com.gideontek.phonetrack.EventType
import com.gideontek.phonetrack.OutcomeKind
import com.gideontek.phonetrack.Subscription
import com.gideontek.phonetrack.SubscriptionManager
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A start request while the service waits for a fix leaves that fetch alone. */
class EventLogMidFetchTest {
    // Providers are on but report nothing until the test pushes a fix.
    @get:Rule
    val rules = Scenario.rules(mockLocation = true, reportFixes = false)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun stopService() {
        SubscriptionManager.remove(context, "+15550001111")
    }

    private fun waitFor(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (predicate()) return true
            SystemClock.sleep(200)
        }
        return predicate()
    }

    @Test
    fun aStartRequestMidFetchCausesNoFalseFailureAndNoSecondTick() {
        EventLogStore.setEnabled(context, true)
        TestState.enableListening()
        val now = System.currentTimeMillis()
        val old = now - 2 * 3_600_000L
        TestState.seedSubscriptions(Subscription("+15550001111", 200, 15, 4, old, now + 3_600_000L, 0.0, 0.0, old))
        SubscriptionManager.ensureServiceRunning(context)
        assertTrue("service did not start", waitFor(15_000) {
            EventLogStore.read(context).any { it.type == EventType.SERVICE_START }
        })
        SystemClock.sleep(1_500) // the first tick has run and waits for a fix

        SubscriptionManager.ensureServiceRunning(context) // a second start request mid-fetch
        SystemClock.sleep(2_500)
        val mid = EventLogStore.read(context)
        assertTrue("a TICK was written mid-fetch: $mid", mid.none { it.type == EventType.TICK })
        assertTrue("false FIX_FAIL: $mid", mid.none { it.type == EventType.FIX_FAIL })

        // The fix arrives: the original fetch completes and sends.
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fix = Location(LocationManager.GPS_PROVIDER).also {
            it.latitude = 37.7749; it.longitude = -122.4194; it.accuracy = 5f
            it.time = System.currentTimeMillis(); it.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        assertTrue("no TICK after the fix; log was ${EventLogStore.read(context)}", waitFor(15_000) {
            fix.time = System.currentTimeMillis(); fix.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(fix))
            EventLogStore.read(context).any { it.type == EventType.TICK }
        })
        val log = EventLogStore.read(context)
        val ticks = log.filter { it.type == EventType.TICK }
        assertEquals("exactly one TICK: $log", 1, ticks.size)
        assertTrue("the update was sent: $ticks", ticks.single().outcomes.any { it.kind == OutcomeKind.SENT })
        assertTrue("no FIX_FAIL: $log", log.none { it.type == EventType.FIX_FAIL })
        assertEquals("one service start: $log", 1, log.count { it.type == EventType.SERVICE_START })
    }
}
