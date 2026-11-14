package com.gideontek.phonetrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
 * Movement in subscription updates: the row in Replies, and the screen it opens with the switch
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
        compose.onNodeWithText("Movement in subscription updates").clickVisible()
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

    // --- the row in Replies and the switch on the screen ---------------------------------

    @Test
    fun theMovementRowShowsTheCurrentChoiceAndTheSwitchIsOnByDefault() {
        openReplies()
        compose.onNodeWithText("Movement in subscription updates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("On · \u21D7395m").assertIsDisplayed()
        assertTrue(prefs.getBoolean("reply_move_show", true))
        compose.onNodeWithText("Movement in subscription updates").performClick()
        toggle("Movement in subscription updates").assertIsOn().assertIsEnabled()
    }

    @Test
    fun theSwitchStoresItAndTheRowReadsOff() {
        openMovement()
        toggle("Movement in subscription updates").performClick()
        ui.waitUntil { !prefs.getBoolean("reply_move_show", true) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Off").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theRowPreviewsCompassLetters() {
        openReplies { seedMovement(cardinal = true) }
        compose.onNodeWithText("On · NE395m").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSwitchIsNotAReplyPartSoTheSummaryLineIsUnchanged() {
        openReplies()
        val before = compose.countWithText("Accuracy · Battery · Map link", substring = true)
        compose.onNodeWithText("Movement in subscription updates").performScrollTo().performClick()
        toggle("Movement in subscription updates").performClick()
        ui.waitUntil { !prefs.getBoolean("reply_move_show", true) }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(before, compose.countWithText("Accuracy · Battery · Map link", substring = true))
    }

    // --- the screen -------------------------------------------------------------------

    @Test
    fun bothBackActionsReturnToSettingsWithTheRepliesCardStillOpen() {
        openMovement()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Movement in subscription updates").assertIsDisplayed()
        compose.onNodeWithText("Movement in subscription updates").performClick()
        compose.onNodeWithText("Direction").assertIsDisplayed()
        ui.back()
        compose.onNodeWithText("Movement in subscription updates").assertIsDisplayed()
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
        compose.onNodeWithText("Subscription update (moved 395m north-east)").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("GSM-7 · 98 of 160 characters").assertExists()
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
    fun hiddenMovementShowsTheSwitchOffAndTheSampleHasNoDirection() {
        openMovement { seedMovement(show = false) }
        toggle("Movement in subscription updates").assertIsOff()
        compose.onNodeWithText("Subscription update (movement hidden)").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.countWithText("395m", substring = true))
        compose.onNodeWithText("1${nbsp}SMS in total").assertExists()
    }
}
