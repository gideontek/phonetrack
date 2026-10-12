package com.gideontek.phonetrack.protocol

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.EventLogStore
import com.gideontek.phonetrack.EventType
import com.gideontek.phonetrack.Reason
import com.gideontek.phonetrack.Subscription
import com.gideontek.phonetrack.SubscriptionManager
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.Shell
import com.gideontek.phonetrack.support.TestState
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A tick that cannot get a fix says why: here the system location switch is off. */
class EventLogFailureTest {
    @get:Rule
    val rules = Scenario.rules()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun restoreLocation() {
        Shell.run("cmd location set-location-enabled true")
        Shell.run("settings put secure location_mode 3")
    }

    @Test
    fun aTickWithLocationSwitchedOffLogsProviderOffAndAnEmptyTick() {
        EventLogStore.setEnabled(context, true)
        TestState.enableListening()
        val now = System.currentTimeMillis()
        val old = now - 2 * 3_600_000L
        TestState.seedSubscriptions(Subscription("+15550001111", 200, 15, 4, old, now + 3_600_000L, 0.0, 0.0, old))
        Shell.run("cmd location set-location-enabled false")
        Shell.run("settings put secure location_mode 0")
        SystemClock.sleep(500)
        SubscriptionManager.ensureServiceRunning(context)

        val end = SystemClock.elapsedRealtime() + 25_000
        var failed = false
        while (SystemClock.elapsedRealtime() < end && !failed) {
            failed = EventLogStore.read(context).any { it.type == EventType.FIX_FAIL && it.reason == Reason.PROVIDER_OFF }
            if (!failed) SystemClock.sleep(300)
        }
        assertTrue("no FIX_FAIL provider-off in ${EventLogStore.read(context)}", failed)
        assertTrue(
            "the failed tick is still summarised, with no update sent",
            EventLogStore.read(context).any { it.type == EventType.TICK && it.due == 1 && it.outcomes.isEmpty() }
        )
    }
}
