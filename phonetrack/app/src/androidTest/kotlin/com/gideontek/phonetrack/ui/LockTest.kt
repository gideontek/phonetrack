package com.gideontek.phonetrack.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.support.KnownIssues
import com.gideontek.phonetrack.support.Loopback
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.clickVisible
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * The PIN lock: it guards Settings and changes to approvals, nothing else. Locked controls stay
 * visible; tapping one asks for the PIN and then carries on with what was tapped.
 */
class LockTest {
    @get:Rule
    val ui = UiScenario(mockLocation = true)
    private val compose get() = ui.compose

    private val pin = "1234"
    private val a = "+15550004001"
    private val b = "+15550004002"
    private val now get() = System.currentTimeMillis()

    private fun lockedWithPending(vararg numbers: String = arrayOf(a)) = ui.launch {
        TestState.enableListening()
        TestState.seedPending(*numbers)
        TestState.seedPin(pin)
    }

    private fun enterPin(value: String) {
        compose.onNode(hasSetTextAction()).performTextInput(value)
        compose.onNodeWithText("Unlock").performClick()
    }

    private fun assertPinPrompt() = compose.onNodeWithText("Enter PIN").assertIsDisplayed()
    private fun assertNoPinPrompt() = assertEquals(0, compose.countWithText("Enter PIN"))

    @Test
    fun aStoredPinStartsLocked() {
        lockedWithPending()
        compose.onNodeWithContentDescription("Unlock settings").assertIsDisplayed()
    }

    @Test
    fun withoutAPinTheLockIconOffersToSetOne() {
        ui.launch()
        compose.onNodeWithContentDescription("Lock settings").performClick()
        // the dialog's title and its confirm button
        assertEquals(2, compose.countWithText("Set PIN"))
        compose.onNodeWithText("New PIN").assertIsDisplayed()
    }

    @Test
    fun approveAsksForThePinThenApproves() {
        lockedWithPending()
        compose.onNodeWithText("Approve").performClick()
        assertPinPrompt()
        assertEquals("nothing happens before the PIN", ApprovalState.PENDING, TestState.approvalState(a))
        enterPin(pin)
        ui.waitUntil { TestState.approvalState(a) == ApprovalState.APPROVED }
        compose.onNodeWithContentDescription("Lock settings").assertIsDisplayed()
    }

    @Test
    fun blockAsksForThePinThenBlocks() {
        lockedWithPending()
        compose.onNodeWithText("Block").performClick()
        assertPinPrompt()
        enterPin(pin)
        ui.waitUntil { TestState.approvalState(a) == ApprovalState.BLOCKED }
    }

    @Test
    fun changingAKnownNumberAsksForThePinToo() {
        ui.launch {
            TestState.seedApprovals(Triple(a, "APPROVED", now), Triple(b, "BLOCKED", now))
            TestState.seedPin(pin)
        }
        compose.onNodeWithText("Approved and blocked numbers").performClick()
        compose.onNodeWithText("Approve").performClick()
        assertPinPrompt()
        enterPin(pin)
        ui.waitUntil { TestState.approvalState(b) == ApprovalState.APPROVED }
    }

    @Test
    fun theGearAsksForThePinThenOpensSettings() {
        ui.launch { TestState.seedPin(pin) }
        compose.onNodeWithContentDescription("Settings").performClick()
        assertPinPrompt()
        enterPin(pin)
        compose.onNodeWithText("General").assertIsDisplayed()
    }

    @Test
    fun theListeningSwitchAsksForThePinThenToggles() {
        ui.launch { TestState.seedPin(pin) }
        compose.onNodeWithContentDescription("SMS listening").performClick()
        assertPinPrompt()
        assertEquals(false, TestState.prefs.getBoolean("sms_enabled", false))
        enterPin(pin)
        ui.waitUntil { TestState.prefs.getBoolean("sms_enabled", false) }
    }

    @Test
    fun aWrongPinCountsDownAndDoesNothing() {
        lockedWithPending()
        compose.onNodeWithText("Approve").performClick()
        enterPin("0000")
        compose.onNodeWithText("Incorrect PIN. 4 attempts left before a lockout.").assertIsDisplayed()
        assertEquals(ApprovalState.PENDING, TestState.approvalState(a))
        enterPin(pin)
        ui.waitUntil { TestState.approvalState(a) == ApprovalState.APPROVED }
    }

    @Test
    fun fiveWrongPinsLockUnlockingForAMinute() {
        lockedWithPending()
        compose.onNodeWithText("Approve").performClick()
        repeat(5) { enterPin("0000") }
        compose.onNodeWithText("Too many incorrect attempts. Try again in 1 minute.").assertIsDisplayed()
        enterPin(pin)
        compose.onNodeWithText("Too many incorrect attempts. Try again in 1 minute.").assertIsDisplayed()
        assertEquals(ApprovalState.PENDING, TestState.approvalState(a))
    }

    @Test
    fun cancellingDropsTheActionForGood() {
        lockedWithPending()
        compose.onNodeWithText("Approve").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNoPinPrompt()
        // Unlocking later by another route must not replay the cancelled Approve.
        compose.onNodeWithContentDescription("Unlock settings").performClick()
        enterPin(pin)
        compose.waitForIdle()
        assertEquals(ApprovalState.PENDING, TestState.approvalState(a))
    }

    @Test
    fun sendNowAndCancelNeverAskForThePin() {
        KnownIssues.assumeOutboundSmsWorks()
        val me = Loopback.ownNumber
        ui.launch {
            TestState.seedSubscriptions(TestState.subscription(me))
            TestState.seedPin(pin)
        }
        val before = Loopback.lastId()
        compose.onNodeWithText("Send now").performClick()
        assertNotNull(Loopback.await(before) { it.contains("Lat: 37.7749") })
        assertNoPinPrompt()
        compose.onNodeWithText("Cancel").performClick()
        ui.waitUntil { TestState.subscriptions().isEmpty() }
        assertNoPinPrompt()
    }

    @Test
    fun sendLocationNeverAsksForThePin() {
        KnownIssues.assumeOutboundSmsWorks()
        val me = Loopback.ownNumber
        ui.launch {
            TestState.seedApprovals(Triple(me, "APPROVED", now))
            TestState.seedPin(pin)
        }
        compose.onNodeWithText("Approved and blocked numbers").performClick()
        val before = Loopback.lastId()
        compose.onNodeWithText("Send location").performClick()
        assertNotNull(Loopback.await(before) { it.contains("Lat: 37.7749") })
        assertNoPinPrompt()
    }

    @Test
    fun lockingFromSettingsReturnsToMain() {
        ui.launch { TestState.seedPin(pin) }
        compose.onNodeWithContentDescription("Unlock settings").performClick()
        enterPin(pin)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("General").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lock settings").performClick()
        compose.waitUntil(3_000) { compose.countWithText("General") == 0 }
        compose.onNodeWithContentDescription("Unlock settings").assertIsDisplayed()
    }

    @Test
    fun theUnlockDialogSurvivesRotationButNotTheTypedDigits() {
        ui.launch { TestState.seedPin(pin) }
        compose.onNodeWithContentDescription("Unlock settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("12")
        ui.recreate()
        assertPinPrompt()
        val typed = compose.onNode(hasSetTextAction()).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals("", typed)
    }
}
