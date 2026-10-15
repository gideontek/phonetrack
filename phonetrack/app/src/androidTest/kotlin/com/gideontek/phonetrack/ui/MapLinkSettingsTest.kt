package com.gideontek.phonetrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
 * The OpenStreetMap link screen, opened from "Customize OpenStreetMap link" in Replies: the options, their
 * hints, the sample built by the real composer, reset and the lock.
 */
class MapLinkSettingsTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    private val prefs get() = TestState.prefs
    private fun toggle(label: String) = compose.onNode(hasText(label) and isToggleable())

    private fun openReplies(seed: () -> Unit = {}) {
        ui.launch(seed = seed)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Reply contents").clickVisible()
    }

    private fun openMapLink(seed: () -> Unit = {}) {
        openReplies(seed)
        compose.onNodeWithText("Customize OpenStreetMap link").clickVisible()
        compose.onNodeWithText("Link style").assertIsDisplayed()
    }

    private fun seedLink(short: Boolean? = null, encoded: Boolean? = null, zoom: Int? = null) {
        val e = prefs.edit()
        short?.let { e.putBoolean("reply_osm_short", it) }
        encoded?.let { e.putBoolean("reply_osm_encoded", it) }
        zoom?.let { e.putInt("reply_osm_zoom", it) }
        e.commit()
    }

    // --- getting there ---------------------------------------------------------------

    @Test
    fun theCustomizeRowSitsUnderTheLinkSwitchAndShowsTheCurrentStyle() {
        openReplies()
        compose.onNodeWithText("Customize OpenStreetMap link").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("openstreetmap.org · coordinates in link · zoom 12").assertIsDisplayed()
    }

    @Test
    fun theSummaryFollowsTheChoices() {
        openReplies { seedLink(short = true, encoded = true, zoom = 13) }
        compose.onNodeWithText("osm.org · compact code · zoom 13").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bothBackActionsReturnToSettingsWithTheRepliesCardStillOpen() {
        openMapLink()
        compose.onNodeWithContentDescription("Back").performClick()
        // Settings comes back as it was left: scrolled to the same place, the Replies card still open.
        compose.onNodeWithText("Customize OpenStreetMap link").assertIsDisplayed()
        compose.onNodeWithText("Customize OpenStreetMap link").performClick()
        compose.onNodeWithText("Link style").assertIsDisplayed()
        ui.back()
        compose.onNodeWithText("Customize OpenStreetMap link").assertIsDisplayed()
        compose.onNodeWithText("General").assertExists()
    }

    // --- the controls -----------------------------------------------------------------

    @Test
    fun everyOptionIsThereWithItsHint() {
        openMapLink()
        for (label in listOf("Use osm.org instead of openstreetmap.org", "Compact code instead of coordinates")) {
            toggle(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Map zoom level").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saves 14 characters", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("about 50 characters shorter", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Higher is zoomed in", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Levels 10, 13 and 16", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theShortAddressSwitchStoresItAndShortensTheSample() {
        openMapLink()
        assertEquals(0, compose.countWithText("https://osm.org/?mlat", substring = true))
        toggle("Use osm.org instead of openstreetmap.org").performScrollTo().performClick()
        ui.waitUntil { prefs.getBoolean("reply_osm_short", false) }
        assertEquals(1, compose.countWithText("https://osm.org/?mlat", substring = true))
        assertEquals(0, compose.countWithText("https://www.openstreetmap.org", substring = true))
    }

    @Test
    fun theCompactCodeSwitchStoresItAndShowsACodeInsteadOfCoordinates() {
        openMapLink()
        toggle("Compact code instead of coordinates").performScrollTo().performClick()
        ui.waitUntil { prefs.getBoolean("reply_osm_encoded", false) }
        assertEquals(1, compose.countWithText("https://www.openstreetmap.org/go/", substring = true))
        assertEquals(0, compose.countWithText("mlat=", substring = true))
    }

    @Test
    fun theCompactCodeHintSaysToKeepCoordinatesForEmergencyServices() {
        openMapLink()
        compose.onNodeWithText("passed on to a dispatcher or rescue team", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("geo: link options under Replies also send the coordinates", substring = true).assertExists()
    }

    @Test
    fun theCompactCodeHintNamesThePrecisionForTheZoom() {
        openMapLink { seedLink(zoom = 15) }
        compose.onNodeWithText("up to ±2.4 m at zoom 15", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theZoomStepperStoresItsValueAndStopsAtTheBounds() {
        openMapLink()
        compose.onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
        ui.waitUntil { prefs.getInt("reply_osm_zoom", 12) == 13 }
        compose.onNodeWithContentDescription("Zoom out").performClick()
        compose.onNodeWithContentDescription("Zoom out").performClick()
        ui.waitUntil { prefs.getInt("reply_osm_zoom", 12) == 11 }
    }

    @Test
    fun theZoomCannotGoPastEighteen() {
        openMapLink { seedLink(zoom = 18) }
        compose.onNodeWithContentDescription("Zoom in").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom out").assertIsEnabled()
    }

    @Test
    fun theZoomCannotGoBelowEight() {
        openMapLink { seedLink(zoom = 8) }
        compose.onNodeWithContentDescription("Zoom out").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom in").assertIsEnabled()
    }

    @Test
    fun theZoomShowsInTheReadableLinkOfTheSample() {
        openMapLink { seedLink(zoom = 16) }
        assertEquals(1, compose.countWithText("#map=16/37.7749/-122.4194", substring = true))
    }

    // --- reset ------------------------------------------------------------------------

    @Test
    fun resetIsOffWhileEverythingIsAtItsDefault() {
        openMapLink()
        compose.onNodeWithText("Reset to defaults").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun resetRestoresTheDefaults() {
        openMapLink { seedLink(short = true, encoded = true, zoom = 15) }
        compose.onNodeWithText("Reset to defaults").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil {
            !prefs.getBoolean("reply_osm_short", true) && !prefs.getBoolean("reply_osm_encoded", true) &&
                prefs.getInt("reply_osm_zoom", 0) == 12
        }
        compose.onNodeWithText("Reset to defaults").assertIsNotEnabled()
        assertEquals(1, compose.countWithText("https://www.openstreetmap.org/?mlat", substring = true))
    }

    @Test
    fun resetLeavesTheMovementChoicesAlone() {
        openMapLink {
            seedLink(encoded = true)
            prefs.edit().putBoolean("reply_move_cardinal", true).commit()
        }
        compose.onNodeWithText("Reset to defaults").performScrollTo().performClick()
        ui.waitUntil { !prefs.getBoolean("reply_osm_encoded", true) }
        assertTrue(prefs.getBoolean("reply_move_cardinal", false))
    }

    // --- the sample -------------------------------------------------------------------

    @Test
    fun theSampleIsTheLocationReplyWithItsEncodingAndLength() {
        openMapLink()
        compose.onNodeWithText("Location reply").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 SMS in total").assertExists()
        compose.onNodeWithText("GSM-7 · 116 of 160 characters").assertExists()
        assertEquals(0, compose.countWithText("Subscription update", substring = true))
    }

    @Test
    fun aCompactShortLinkShrinksTheSample() {
        openMapLink { seedLink(short = true, encoded = true) }
        compose.onNodeWithText("GSM-7 · 63 of 160 characters").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSampleFollowsTheReplyContents() {
        openMapLink {
            val e = prefs.edit()
            for (k in listOf("coords", "accuracy", "battery", "time", "geo", "osm")) e.putBoolean("reply_$k", k == "coords" || k == "osm")
            e.commit()
        }
        compose.onNodeWithText("Lat: 37.7749, Lon: -122.4194", substring = true).performScrollTo().assertIsDisplayed()
    }

    // --- when the link is off --------------------------------------------------------

    @Test
    fun aNoteExplainsWhenTheLinkIsSwitchedOffUnderReplies() {
        openMapLink {
            val e = prefs.edit()
            for (k in listOf("coords", "accuracy", "battery", "time", "geo", "osm")) e.putBoolean("reply_$k", k == "coords")
            e.commit()
        }
        compose.onNodeWithText("The OpenStreetMap link is switched off under Replies", substring = true).assertIsDisplayed()
    }

    @Test
    fun noNoteWhileTheLinkIsOn() {
        openMapLink()
        assertEquals(0, compose.countWithText("switched off under Replies", substring = true))
    }

    // --- the lock --------------------------------------------------------------------

    @Test
    fun lockingFromTheLinkScreenReturnsToSettingsLikeEveryOtherSettingsScreen() {
        ui.launch { TestState.seedPin("1234") }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("1234")
        compose.onNodeWithText("Unlock").performClick()
        compose.onNodeWithText("Reply contents").clickVisible()
        compose.onNodeWithText("Customize OpenStreetMap link").clickVisible()
        compose.onNodeWithText("Link style").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lock settings").performClick()
        compose.onNodeWithText("General").assertExists()
        assertFalse(compose.onAllNodesWithText("Use osm.org instead of openstreetmap.org").fetchSemanticsNodes().isNotEmpty())
    }
}
