package com.gideontek.phonetrack.notifications

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.HostAlerts
import com.gideontek.phonetrack.support.Notifications
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The owner's "can't send location" alert: private on the lock screen, and gone once resolved. */
class HostAlertsTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true)

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val alertId = 45

    @Test
    fun clearRemovesThePermissionAlert() {
        HostAlerts.locationPermissionNeeded(ctx, "+15550001111")
        assertNotNull(Notifications.await(alertId))
        HostAlerts.clearLocationPermissionNeeded(ctx)
        assertTrue(Notifications.awaitGone(alertId))
    }

    @Test
    fun clearIfResolvedClearsItWhenThePermissionsAreGranted() {
        HostAlerts.locationPermissionNeeded(ctx, null)
        assertNotNull(Notifications.await(alertId))
        HostAlerts.clearIfResolved(ctx) // the rules grant every permission
        assertTrue(Notifications.awaitGone(alertId))
    }

    @Test
    fun aLocationRequestThatCanBeServedClearsAnOldAlert() {
        HostAlerts.locationPermissionNeeded(ctx, "+15550001111")
        assertNotNull(Notifications.await(alertId))
        val s = Scenario().ready()
        assertOneReply(s.ask("phonetrack"))
        assertTrue(Notifications.awaitGone(alertId))
    }

    @Test
    fun theLockScreenVersionHidesTheNumber() {
        HostAlerts.locationPermissionNeeded(ctx, "+15550001111")
        Notifications.await(alertId)
        val n = Notifications.raw(alertId)!!.notification
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        val public = n.publicVersion
        assertNotNull(public)
        assertEquals("PhoneTrack can't send location", public.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(null, public.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun theFullTextIsUnchanged() {
        HostAlerts.locationPermissionNeeded(ctx, "+15550001111")
        val alert = Notifications.await(alertId)!!
        assertEquals("PhoneTrack can't send location", alert.title)
        assertEquals(
            "A location request from +15550001111 couldn't be answered. Tap to open PhoneTrack, then " +
                "allow Location \"all the time\". Saved subscriptions start working once you do.",
            alert.text
        )
    }
}
