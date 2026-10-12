package com.gideontek.phonetrack.protocol

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.EventLogStore
import com.gideontek.phonetrack.EventType
import com.gideontek.phonetrack.Subscription
import com.gideontek.phonetrack.SubscriptionManager
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A service destroyed while it waits for a fix still logs the tick it was in. */
class EventLogDestroyTest {
    // Providers are on but never report a fix, so the fetch stays pending (it times out after 60 s).
    @get:Rule
    val rules = Scenario.rules(mockLocation = true, reportFixes = false)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun waitFor(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (predicate()) return true
            SystemClock.sleep(200)
        }
        return predicate()
    }

    @Test
    fun cancellingTheLastSubscriptionMidFetchStillLogsTheTick() {
        EventLogStore.setEnabled(context, true)
        TestState.enableListening()
        val now = System.currentTimeMillis()
        val old = now - 2 * 3_600_000L
        TestState.seedSubscriptions(Subscription("+15550001111", 200, 15, 4, old, now + 3_600_000L, 0.0, 0.0, old))
        SubscriptionManager.ensureServiceRunning(context)
        assertTrue("service did not start", waitFor(15_000) {
            EventLogStore.read(context).any { it.type == EventType.SERVICE_START }
        })
        SystemClock.sleep(1_500) // the first tick has run and is now waiting for a fix
        assertTrue(EventLogStore.read(context).none { it.type == EventType.TICK })

        SubscriptionManager.remove(context, "+15550001111") // last one: stops the service

        val log = { EventLogStore.read(context) }
        assertTrue("no TICK for the interrupted fetch; log was ${log()}", waitFor(15_000) {
            log().any { it.type == EventType.TICK && it.due == 1 && it.outcomes.isEmpty() }
        })
        assertTrue("no false timeout: ${log()}", log().none { it.type == EventType.FIX_FAIL })
    }
}
