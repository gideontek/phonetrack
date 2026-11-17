package com.gideontek.phonetrack.notifications

import android.app.Notification
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gideontek.phonetrack.MainActivity
import com.gideontek.phonetrack.support.Notifications
import com.gideontek.phonetrack.support.Scenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The persistent subscription notice: right text, tappable, quiet on re-post. */
class ForegroundNotificationsTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true)

    private val subscriptionNoticeId = 44

    @After
    fun closeTheApp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).forEach { it.finishAndRemoveTask() }
        }
    }

    /** A real subscribe from the approved own number: acknowledged, then an immediate fix. */
    private fun startOneSubscription() {
        Scenario().ready().ask("phonetrack subscribe", expect = 2)
    }

    private fun mainActivityResumed(): Boolean {
        var found = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
        }
        return found
    }

    @Test
    fun theSubscriptionNoticeKeepsItsTextAndIsOnlyAlertedOnce() {
        startOneSubscription()
        val n = Notifications.await(subscriptionNoticeId) !!
        assertEquals("PhoneTrack — 1 active subscription", n.title)
        assertEquals("Sending periodic location updates", n.text)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test
    fun tappingTheSubscriptionNoticeOpensTheApp() {
        startOneSubscription()
        assertNotNull(Notifications.await(subscriptionNoticeId))
        Notifications.tap(subscriptionNoticeId)
        val end = android.os.SystemClock.elapsedRealtime() + 8_000
        while (!mainActivityResumed() && android.os.SystemClock.elapsedRealtime() < end) {
            android.os.SystemClock.sleep(250)
        }
        assertTrue("MainActivity did not open", mainActivityResumed())
    }
}
