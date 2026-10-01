package com.gideontek.phonetrack.phase

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.gideontek.phonetrack.SubscriptionService
import com.gideontek.phonetrack.support.KnownIssues
import com.gideontek.phonetrack.support.Notifications
import com.gideontek.phonetrack.support.Phase
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Background location revoked, everything else granted (set up by scripts/e2e/perm-no-background-location.sh).
 * A request cannot start a location reply: the requester gets only the generic error, and the
 * owner gets a notification that says what to change.
 */
class NoBackgroundLocationPhaseTest {
    @Before
    fun phase() {
        Phase.require("no-bg-location")
        KnownIssues.assumeOutboundSmsWorks()
    }

    @get:Rule
    val ui = UiScenario(grant = false)

    private val ownerAlertId = 45
    private val generic = "[PhoneTrack] Location permission not granted"

    private fun assertRequesterTextHasNoOwnerInstructions(reply: String) {
        assertEquals(generic, reply)
        assertFalse(reply, reply.contains("all the time", ignoreCase = true))
    }

    @Test
    fun aOneShotRequestGetsTheGenericErrorAndTheOwnerGetsAnAlert() {
        val s = Scenario().ready()
        assertRequesterTextHasNoOwnerInstructions(assertOneReply(s.ask("phonetrack")))
        val alert = Notifications.await(ownerAlertId)
        assertNotNull("owner alert not posted", alert)
        assertEquals("PhoneTrack can't send location", alert!!.title)
        assertTrue(alert.text, alert.text.contains("A location request from ${s.me}"))
        assertTrue(alert.text, alert.text.contains("all the time"))
    }

    @Test
    fun repeatedRequestsReplaceTheAlertInsteadOfStackingIt() {
        val s = Scenario().ready()
        repeat(3) { assertOneReply(s.ask("phonetrack")) }
        assertEquals(1, Notifications.withId(ownerAlertId).size)
    }

    @Test
    fun lastNeedsNoBackgroundLocationBecauseItStartsNoService() {
        val s = Scenario().ready()
        val reply = assertOneReply(s.ask("phonetrack last"))
        assertTrue(reply, reply.startsWith("[PhoneTrack] No recent location saved") || reply.startsWith("[PhoneTrack] Last known ("))
        assertTrue("no owner alert for a request that was served", Notifications.withId(ownerAlertId).isEmpty())
    }

    @Test
    fun aSubscriptionIsAcceptedAndStoredButNothingRunsUntilTheOwnerFixesIt() {
        val s = Scenario().ready()
        val replies = s.ask("phonetrack subscribe --freq 5", expect = 2)
        assertTrue(replies.toString(), replies[0].startsWith("[PhoneTrack] Subscribed: update every 5 min"))
        assertEquals(generic, replies[1])
        assertEquals(1, TestState.subscriptions().size)
        assertFalse("the periodic service must not run without background location",
            Notifications.serviceRunning(SubscriptionService::class.java.name))
    }

    @Test
    fun mainExplainsTheProblemAndOffersFix() {
        ui.launch { TestState.enableListening() }
        ui.compose.onNodeWithText("Background location is off, so replies can't start.").assertIsDisplayed()
        ui.compose.onNodeWithText("Fix").assertIsDisplayed()
    }
}
