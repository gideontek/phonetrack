package com.gideontek.phonetrack.phase

import com.gideontek.phonetrack.support.Notifications
import com.gideontek.phonetrack.support.Phase
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Notifications revoked (API 33+) and background location revoked: the owner cannot be alerted, and nothing crashes. */
class NoNotificationsPhaseTest {
    @Before
    fun phase() {
        Phase.require("no-notifications")
    }

    @Test
    fun theRequesterStillGetsTheErrorAndNoAlertIsPosted() {
        val s = Scenario().ready()
        assertEquals("[PhoneTrack] Location permission not granted", assertOneReply(s.ask("phonetrack")))
        android.os.SystemClock.sleep(1_500)
        assertTrue("no owner alert without the notification permission", Notifications.withId(45).isEmpty())
    }
}
