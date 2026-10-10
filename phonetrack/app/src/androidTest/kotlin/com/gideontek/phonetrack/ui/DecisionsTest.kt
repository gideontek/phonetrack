package com.gideontek.phonetrack.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Needs your decision": tap a number to reveal Approve / Block. */
class DecisionsTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    private val a = "+15550001001"
    private val b = "+15550001002"

    private fun row(number: String): SemanticsNodeInteraction = compose.onNode(hasClickAction() and hasText(number))

    private fun stateOf(node: SemanticsNodeInteraction) =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun customAction(node: SemanticsNodeInteraction, label: String) {
        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnUiThread { actions.first { it.label == label }.action() }
    }

    @Test
    fun theSectionIsHiddenWhenNothingIsPending() {
        ui.launch { TestState.enableListening() }
        assertEquals(0, compose.countWithText("Needs your decision"))
    }

    @Test
    fun rowsShowTheNumberHowLongAgoAndACount() {
        ui.launch { TestState.seedPending(a, b, askedMinutesAgo = 12) }
        compose.onNodeWithText("Needs your decision").assertIsDisplayed()
        compose.onNodeWithText("2 waiting").assertIsDisplayed()
        compose.onNodeWithText("Tap a number to approve or block it.").assertIsDisplayed()
        row(a).assertIsDisplayed()
        assertEquals(2, compose.countWithText("asked 12min ago"))
    }

    @Test
    fun aLonePendingRowStartsExpanded() {
        ui.launch { TestState.seedPending(a) }
        assertEquals("Expanded", stateOf(row(a)))
        compose.onNodeWithText("Approve").assertIsDisplayed()
        compose.onNodeWithText("Block").assertIsDisplayed()
    }

    @Test
    fun severalPendingRowsStartCollapsedAndTapExpandsOneAtATime() {
        ui.launch { TestState.seedPending(a, b) }
        assertEquals("Collapsed", stateOf(row(a)))
        assertEquals(0, compose.countWithText("Approve"))
        row(a).performClick()
        assertEquals("Expanded", stateOf(row(a)))
        assertEquals("Collapsed", stateOf(row(b)))
        assertEquals(1, compose.countWithText("Approve"))
        row(a).performClick()
        assertEquals("Collapsed", stateOf(row(a)))
        assertEquals(0, compose.countWithText("Approve"))
    }

    @Test
    fun approveStoresApprovedAndRemovesTheRow() {
        ui.launch { TestState.seedPending(a, b) }
        row(a).performClick()
        compose.onNodeWithText("Approve").performClick()
        ui.waitUntil { TestState.approvalState(a) == ApprovalState.APPROVED }
        assertEquals(ApprovalState.PENDING, TestState.approvalState(b))
        compose.waitUntil(3_000) { compose.countWithText("2 waiting") == 0 }
        compose.onNodeWithText("1 waiting").assertIsDisplayed()
    }

    @Test
    fun blockStoresBlockedAndRemovesTheRow() {
        ui.launch { TestState.seedPending(a) }
        compose.onNodeWithText("Block").performClick()
        ui.waitUntil { TestState.approvalState(a) == ApprovalState.BLOCKED }
        compose.waitUntil(3_000) { compose.countWithText("Needs your decision") == 0 }
    }

    @Test
    fun theRowIsAButtonWithAnApproveAndBlockActionForAccessibilityServices() {
        ui.launch { TestState.seedPending(a, b) }
        val node = row(a)
        assertEquals(Role.Button, node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        val labels = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Approve", "Block"), labels)
        node.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theApproveActionOnACollapsedRowApproves() {
        ui.launch { TestState.seedPending(a, b) }
        customAction(row(a), "Approve")
        ui.waitUntil { TestState.approvalState(a) == ApprovalState.APPROVED }
    }

    @Test
    fun theBlockActionOnACollapsedRowBlocks() {
        ui.launch { TestState.seedPending(a, b) }
        customAction(row(b), "Block")
        ui.waitUntil { TestState.approvalState(b) == ApprovalState.BLOCKED }
    }

    @Test
    fun expansionSurvivesRotation() {
        ui.launch { TestState.seedPending(a, b) }
        row(b).performClick()
        ui.recreate()
        assertEquals("Expanded", stateOf(row(b)))
        assertEquals("Collapsed", stateOf(row(a)))
    }

    @Test
    fun theNewestRequestIsListedFirst() {
        ui.launch {
            val now = System.currentTimeMillis()
            TestState.seedApprovals(
                Triple(a, "PENDING", now - 3_600_000L),
                Triple(b, "PENDING", now - 60_000L)
            )
        }
        val top = { n: String -> row(n).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top(b) < top(a))
    }
}
