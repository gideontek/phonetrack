package com.gideontek.phonetrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.clickVisible
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Movement in subscription updates: the switch in Replies, and the screen opened from "Customize movement"
 * (compass letters or arrows, with the sample built by the real composer).
 */
class MovementSettingsTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    private val prefs get() = TestState.prefs
    private val nbsp = " "
    private fun toggle(label: String) = compose.onNode(hasText(label) and isToggleable())

    private fun openReplies(seed: () -> Unit = {}) {
        ui.launch(seed = seed)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Reply contents").clickVisible()
    }

    private fun openMovement(seed: () -> Unit = {}) {
        openReplies(seed)
        compose.onNodeWithText("Customize movement").clickVisible()
        compose.onNodeWithText("Direction").assertIsDisplayed()
    }

    private fun seedMovement(show: Boolean? = null, cardinal: Boolean? = null) {
        val e = prefs.edit()
        show?.let { e.putBoolean("reply_move_show", it) }
        cardinal?.let { e.putBoolean("reply_move_cardinal", it) }
        e.commit()
    }

    private fun seedLink(short: Boolean? = null, encoded: Boolean? = null) {
        val e = prefs.edit()
        short?.let { e.putBoolean("reply_osm_short", it) }
        encoded?.let { e.putBoolean("reply_osm_encoded", it) }
        e.commit()
    }

    // --- the switch in Replies -------------------------------------------------------

    @Test
    fun theMovementSwitchIsOnByDefaultWithItsCustomizeRow() {
        openReplies()
        toggle("Movement in subscription updates").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Customize movement").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Arrows").assertIsDisplayed()
        assertTrue(prefs.getBoolean("reply_move_show", true))
    }

    @Test
    fun theSwitchStoresItAndTheSummaryReadsOff() {
        openReplies()
        toggle("Movement in subscription updates").performScrollTo().performClick()
        ui.waitUntil { !prefs.getBoolean("reply_move_show", true) }
        compose.onNodeWithText("Off").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSummaryNamesCompassLetters() {
        openReplies { seedMovement(cardinal = true) }
        compose.onNodeWithText("Compass letters").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSwitchIsNotAReplyPartSoTheSummaryLineIsUnchanged() {
        openReplies()
        val before = compose.countWithText("Accuracy · Battery · OpenStreetMap link", substring = true)
        toggle("Movement in subscription updates").performScrollTo().performClick()
        ui.waitUntil { !prefs.getBoolean("reply_move_show", true) }
        assertEquals(before, compose.countWithText("Accuracy · Battery · OpenStreetMap link", substring = true))
    }

    // --- the screen -------------------------------------------------------------------

    @Test
    fun bothBackActionsReturnToSettingsWithTheRepliesCardStillOpen() {
        openMovement()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Customize movement").assertIsDisplayed()
        compose.onNodeWithText("Customize movement").performClick()
        compose.onNodeWithText("Direction").assertIsDisplayed()
        ui.back()
        compose.onNodeWithText("Customize movement").assertIsDisplayed()
        compose.onNodeWithText("General").assertExists()
    }

    @Test
    fun theCompassOptionIsThereWithItsHint() {
        openMovement()
        toggle("Compass letters instead of arrows").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("160-character limit instead of 70", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theDefaultSampleShowsTheArrowAndAFollowUpMessage() {
        openMovement()
        compose.onNodeWithText("Subscription update (moved 395 m north-east)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("⇗395m", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2${nbsp}SMS in total").assertExists()
        compose.onNodeWithText("SMS 2 of 2").assertExists()
        compose.onNodeWithText("UCS-2 · 36 of 70 characters").assertExists()
    }

    @Test
    fun compassLettersReplaceTheArrowAndKeepTheUpdateToOneMessage() {
        openMovement()
        toggle("Compass letters instead of arrows").performScrollTo().performClick()
        ui.waitUntil { prefs.getBoolean("reply_move_cardinal", false) }
        compose.onNodeWithText("NE395m", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.countWithText("⇗395m", substring = true))
        compose.onNodeWithText("1${nbsp}SMS in total").assertExists()
        compose.onNodeWithText("GSM-7 · 123 of 160 characters").assertExists()
    }

    @Test
    fun aCompactShortLinkMakesTheArrowUpdateOneMessage() {
        openMovement { seedLink(short = true, encoded = true) }
        compose.onNodeWithText("1${nbsp}SMS in total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("UCS-2 · 67 of 70 characters").assertExists()
    }

    @Test
    fun aCompactLinkOnTheFullAddressStillSplitsTheArrowUpdate() {
        openMovement { seedLink(encoded = true) }
        compose.onNodeWithText("2${nbsp}SMS in total").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theStoredChoiceIsShownWhenTheScreenOpens() {
        openMovement { seedMovement(cardinal = true) }
        compose.onNodeWithText("NE395m", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hiddenMovementShowsANoteAndTheSampleHasNoDirection() {
        openMovement { seedMovement(show = false) }
        compose.onNodeWithText("Movement is switched off under Replies", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Subscription update (movement hidden)").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.countWithText("395m", substring = true))
        compose.onNodeWithText("1${nbsp}SMS in total").assertExists()
    }

    @Test
    fun noNoteWhileMovementIsOn() {
        openMovement()
        assertEquals(0, compose.countWithText("switched off under Replies", substring = true))
    }

    // --- reset ------------------------------------------------------------------------

    @Test
    fun resetIsOffAtTheDefault() {
        openMovement()
        compose.onNodeWithText("Reset to defaults").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun resetRestoresArrowsAndLeavesTheLinkAlone() {
        openMovement { seedMovement(cardinal = true); seedLink(encoded = true) }
        compose.onNodeWithText("Reset to defaults").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil { !prefs.getBoolean("reply_move_cardinal", true) }
        assertTrue(prefs.getBoolean("reply_osm_encoded", false))
        assertFalse(prefs.getBoolean("reply_move_cardinal", true))
        compose.onNodeWithText("Reset to defaults").assertIsNotEnabled()
    }
}
