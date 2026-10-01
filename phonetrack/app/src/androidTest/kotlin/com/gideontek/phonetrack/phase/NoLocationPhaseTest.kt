package com.gideontek.phonetrack.phase

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Precise (and coarse) location revoked: nothing can be located, and the owner is told. */
class NoLocationPhaseTest {
    @Before
    fun phase() {
        Phase.require("no-location")
        KnownIssues.assumeOutboundSmsWorks()
    }

    @get:Rule
    val ui = UiScenario(grant = false)

    private val generic = "[PhoneTrack] Location permission not granted"

    @Test
    fun oneShotAndLastGetTheGenericErrorWithNoOwnerInstructions() {
        val s = Scenario().ready()
        for (command in listOf("phonetrack", "phonetrack last")) {
            val reply = assertOneReply(s.ask(command))
            assertEquals(command, generic, reply)
            assertFalse(reply, reply.contains("all the time", ignoreCase = true))
        }
        assertNotNull("owner alert not posted", Notifications.await(45))
    }

    @Test
    fun mainSaysLocationIsOffAndOffersFix() {
        ui.launch { TestState.enableListening() }
        ui.compose.onNodeWithText("Location permission is off, so replies can't find you.").assertIsDisplayed()
        ui.compose.onNodeWithText("Fix").assertIsDisplayed()
    }
}
