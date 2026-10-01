package com.gideontek.phonetrack.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.support.KnownIssues
import com.gideontek.phonetrack.support.Loopback
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Approved and blocked numbers": collapsed by default, one action per row. */
class KnownNumbersTest {
    @get:Rule
    val ui = UiScenario(mockLocation = true)
    private val compose get() = ui.compose

    private val a = "+15550003001"
    private val b = "+15550003002"
    private val c = "+15550003003"
    private val now get() = System.currentTimeMillis()

    private fun seed() = TestState.seedApprovals(
        Triple(a, "APPROVED", now), Triple(b, "APPROVED", now), Triple(c, "BLOCKED", now)
    )

    private fun expand() = compose.onNodeWithText("Approved and blocked numbers").performClick()

    @Test
    fun hiddenWhenThereAreNoApprovedOrBlockedNumbers() {
        ui.launch { TestState.seedPending(a) }
        assertEquals(0, compose.countWithText("Approved and blocked numbers"))
    }

    @Test
    fun startsCollapsedWithACountSummary() {
        ui.launch { seed() }
        compose.onNodeWithText("Approved and blocked numbers").assertIsDisplayed()
        compose.onNodeWithText("2 approved · 1 blocked").assertIsDisplayed()
        assertEquals(0, compose.countWithText(a))
    }

    @Test
    fun expandingListsEachNumberWithItsState() {
        ui.launch { seed() }
        expand()
        compose.onNodeWithText(a).assertIsDisplayed()
        compose.onNodeWithText(c).assertIsDisplayed()
        assertEquals(2, compose.countWithText("Approved"))
        assertEquals(1, compose.countWithText("Blocked"))
        assertEquals(2, compose.countWithText("Send location"))
        assertEquals(2, compose.countWithText("Block"))
        assertEquals(1, compose.countWithText("Approve"))
    }

    @Test
    fun theHeaderReportsExpandedAndCollapsed() {
        ui.launch { seed() }
        val header = { compose.onNode(androidx.compose.ui.test.hasText("Approved and blocked numbers") and androidx.compose.ui.test.hasClickAction()) }
        val state = { header().fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) }
        assertEquals("Collapsed", state())
        expand()
        assertEquals("Expanded", state())
    }

    @Test
    fun blockMovesAnApprovedNumberToBlocked() {
        ui.launch { seed() }
        expand()
        compose.onAllNodesWithText("Block")[0].performClick()
        ui.waitUntil { TestState.approvals().count { it.state == ApprovalState.BLOCKED } == 2 }
        compose.onNodeWithText("1 approved · 2 blocked").assertExists()
    }

    @Test
    fun approveMovesABlockedNumberToApproved() {
        ui.launch { seed() }
        expand()
        compose.onNodeWithText("Approve").performClick()
        ui.waitUntil { TestState.approvalState(c) == ApprovalState.APPROVED }
        compose.onNodeWithText("3 approved · 0 blocked").assertExists()
    }

    @Test
    fun sendLocationTextsTheCurrentLocationToThatNumber() {
        KnownIssues.assumeOutboundSmsWorks()
        val me = Loopback.ownNumber
        ui.launch { TestState.seedApprovals(Triple(me, "APPROVED", now)) }
        expand()
        val before = Loopback.lastId()
        compose.onNodeWithText("Send location").performClick()
        val reply = Loopback.await(before) { it.startsWith("[PhoneTrack]") }
        assertNotNull("no location reply arrived", reply)
        assertTrue(reply!!, reply.contains("Lat: 37.7749"))
    }

    @Test
    fun pendingAndKnownNumbersShowTogether() {
        ui.launch {
            val t = now
            TestState.seedApprovals(Triple(a, "PENDING", t - 60_000L), Triple(b, "APPROVED", t))
        }
        compose.onNodeWithText("Needs your decision").assertIsDisplayed()
        compose.onNodeWithText("1 approved · 0 blocked").assertIsDisplayed()
    }
}
