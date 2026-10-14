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

/** The Advanced screen: the map link options, their hints, and the samples built by the real composer. */
class AdvancedSettingsTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    private val prefs get() = TestState.prefs
    private fun toggle(label: String) = compose.onNode(hasText(label) and isToggleable())
    private val nbsp = "\u00A0"

    private fun openAdvanced(seed: () -> Unit = {}) {
        ui.launch(seed = seed)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Advanced settings").clickVisible()
        compose.onNodeWithText("Map link").assertIsDisplayed()
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
    fun settingsShowsTheLinkSummaryOnTheAdvancedRow() {
        ui.launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Advanced settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Map link: openstreetmap.org · coordinates in link · zoom 12").assertIsDisplayed()
    }

    @Test
    fun theRowOpensTheScreenAndBothBackActionsReturnToSettings() {
        openAdvanced()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("General").assertIsDisplayed()
        compose.onNodeWithText("Advanced settings").clickVisible()
        compose.onNodeWithText("Map link").assertIsDisplayed()
        ui.back()
        compose.onNodeWithText("General").assertIsDisplayed()
    }

    @Test
    fun theSummaryOnTheAdvancedRowFollowsTheChoices() {
        openAdvanced { seedLink(short = true, encoded = true, zoom = 13) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Map link: osm.org · compact code · zoom 13").performScrollTo().assertIsDisplayed()
    }

    // --- the controls -----------------------------------------------------------------

    @Test
    fun everyOptionIsThereWithItsHint() {
        openAdvanced()
        for (label in listOf("Short address (osm.org)", "Compact code instead of coordinates")) {
            toggle(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Map zoom").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saves 14 characters", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("about 50 characters shorter", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Higher is closer", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theShortAddressSwitchStoresItAndShortensTheSamples() {
        openAdvanced()
        assertEquals(0, compose.countWithText("https://osm.org/?mlat", substring = true))
        toggle("Short address (osm.org)").performScrollTo().performClick()
        ui.waitUntil { prefs.getBoolean("reply_osm_short", false) }
        assertTrue(compose.countWithText("https://osm.org/?mlat", substring = true) >= 2)
        assertEquals(0, compose.countWithText("https://www.openstreetmap.org", substring = true))
    }

    @Test
    fun theCompactCodeSwitchStoresItAndShowsACodeInsteadOfCoordinates() {
        openAdvanced()
        toggle("Compact code instead of coordinates").performScrollTo().performClick()
        ui.waitUntil { prefs.getBoolean("reply_osm_encoded", false) }
        assertTrue(compose.countWithText("/go/", substring = true) >= 2)
        assertEquals(0, compose.countWithText("mlat=", substring = true))
    }

    @Test
    fun theCompactCodeHintNamesThePrecisionForTheZoom() {
        openAdvanced { seedLink(zoom = 15) }
        compose.onNodeWithText("up to ±2.4 m at zoom 15", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theZoomStepperStoresItsValueAndStopsAtTheBounds() {
        openAdvanced()
        compose.onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
        ui.waitUntil { prefs.getInt("reply_osm_zoom", 12) == 13 }
        compose.onNodeWithContentDescription("Zoom out").performClick()
        compose.onNodeWithContentDescription("Zoom out").performClick()
        ui.waitUntil { prefs.getInt("reply_osm_zoom", 12) == 11 }
    }

    @Test
    fun theZoomCannotGoPastEighteen() {
        openAdvanced { seedLink(zoom = 18) }
        compose.onNodeWithContentDescription("Zoom in").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom out").assertIsEnabled()
    }

    @Test
    fun theZoomCannotGoBelowEight() {
        openAdvanced { seedLink(zoom = 8) }
        compose.onNodeWithContentDescription("Zoom out").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom in").assertIsEnabled()
    }

    @Test
    fun theZoomShowsInTheReadableLinkOfTheSamples() {
        openAdvanced { seedLink(zoom = 16) }
        assertTrue(compose.countWithText("#map=16/37.7749/-122.4194", substring = true) >= 2)
    }

    // --- reset ------------------------------------------------------------------------

    @Test
    fun resetIsOffWhileEverythingIsAtItsDefault() {
        openAdvanced()
        compose.onNodeWithText("Reset to defaults").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun resetRestoresTheDefaults() {
        openAdvanced { seedLink(short = true, encoded = true, zoom = 15) }
        compose.onNodeWithText("Reset to defaults").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil {
            !prefs.getBoolean("reply_osm_short", true) && !prefs.getBoolean("reply_osm_encoded", true) &&
                prefs.getInt("reply_osm_zoom", 0) == 12
        }
        compose.onNodeWithText("Reset to defaults").assertIsNotEnabled()
        assertTrue(compose.countWithText("https://www.openstreetmap.org/?mlat", substring = true) >= 2)
    }

    // --- samples ----------------------------------------------------------------------

    @Test
    fun theDefaultSamplesShowALocationReplyAndASubscriptionUpdate() {
        openAdvanced()
        compose.onNodeWithText("Location reply").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Subscription update (moved 395 m north-east)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1${nbsp}SMS in total").assertExists()
        compose.onNodeWithText("2${nbsp}SMS in total").assertExists()
    }

    @Test
    fun theSubscriptionSampleShowsTheArrowAndAFollowUpMessage() {
        openAdvanced()
        compose.onNodeWithText("⇗395m", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SMS 2 of 2").assertExists()
    }

    @Test
    fun eachMessageShowsItsEncodingAndLength() {
        openAdvanced()
        compose.onNodeWithText("GSM-7 · 116 of 160 characters").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("UCS-2 · 36 of 70 characters").assertExists()
        compose.onNodeWithText("GSM-7 · 83 of 160 characters").assertExists()
    }

    @Test
    fun aCompactShortLinkMakesBothSamplesOneMessage() {
        openAdvanced { seedLink(short = true, encoded = true) }
        assertEquals(2, compose.countWithText("1${nbsp}SMS in total"))
        assertEquals(0, compose.countWithText("2${nbsp}SMS in total"))
        compose.onNodeWithText("UCS-2 · 67 of 70 characters").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aCompactLinkOnTheFullAddressStillSplitsTheSubscriptionUpdate() {
        openAdvanced { seedLink(encoded = true) }
        compose.onNodeWithText("2${nbsp}SMS in total").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSamplesFollowTheReplyContents() {
        openAdvanced {
            val e = prefs.edit()
            for (k in listOf("coords", "accuracy", "battery", "time", "geo", "osm")) e.putBoolean("reply_$k", k == "coords" || k == "osm")
            e.commit()
        }
        // Both samples contain the coordinates line.
        compose.onAllNodesWithText("Lat: 37.7749, Lon: -122.4194", substring = true)[0].performScrollTo().assertIsDisplayed()
        assertEquals(2, compose.countWithText("Lat: 37.7749, Lon: -122.4194", substring = true))
    }

    // --- when the link is off --------------------------------------------------------

    @Test
    fun aNoteExplainsWhenTheMapLinkIsSwitchedOffUnderReplies() {
        openAdvanced {
            val e = prefs.edit()
            for (k in listOf("coords", "accuracy", "battery", "time", "geo", "osm")) e.putBoolean("reply_$k", k == "coords")
            e.commit()
        }
        compose.onNodeWithText("The map link is switched off under Replies", substring = true).assertIsDisplayed()
    }

    @Test
    fun noNoteWhileTheMapLinkIsOn() {
        openAdvanced()
        assertEquals(0, compose.countWithText("switched off under Replies", substring = true))
    }

    // --- the lock --------------------------------------------------------------------

    @Test
    fun lockingFromAdvancedReturnsToSettingsLikeEveryOtherSettingsScreen() {
        ui.launch { TestState.seedPin("1234") }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("1234")
        compose.onNodeWithText("Unlock").performClick()
        compose.onNodeWithText("Advanced settings").clickVisible()
        compose.onNodeWithText("Map link").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lock settings").performClick()
        compose.onNodeWithText("General").assertIsDisplayed()
        assertFalse(compose.onAllNodesWithText("Short address (osm.org)").fetchSemanticsNodes().isNotEmpty())
    }
}
