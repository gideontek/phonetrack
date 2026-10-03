package com.gideontek.phonetrack.phase

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.gideontek.phonetrack.support.Phase
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** SMS permissions revoked (phase "no-sms") or only SEND_SMS revoked (phase "no-send-sms"). */
class NoSmsPermissionPhaseTest {
    @get:Rule
    val ui = UiScenario(grant = false)

    @Test
    fun mainSaysRequestsCannotBeReceived() {
        Phase.require("no-sms")
        ui.launch { TestState.enableListening() }
        ui.compose.onNodeWithText("SMS permission is off, so requests can't be received.").assertIsDisplayed()
        ui.compose.onNodeWithText("Fix").assertIsDisplayed()
    }

    @Test
    fun aReplyThatCannotBeSentIsSwallowedWithoutACrash() {
        Phase.require("no-send-sms")
        val s = Scenario().ready()
        s.send("phonetrack help")
        s.assertSilent()
        assertEquals("nothing was sent", 0L, TestState.lastSendAt())
        // The request itself was still handled: a stranger is still recorded.
        s.send("phonetrack", from = "+15550001234")
        assertEquals(2, TestState.approvals().size)
    }
}
