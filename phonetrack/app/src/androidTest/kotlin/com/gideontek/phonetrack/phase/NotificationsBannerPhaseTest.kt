package com.gideontek.phonetrack.phase

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gideontek.phonetrack.support.Phase
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Only the notifications permission revoked (API 33+, set up by scripts/e2e/perm-notifications-banner.sh).
 * The Main banner nudges once, Skip hides it for good, and Restore default settings brings it back.
 */
class NotificationsBannerPhaseTest {
    @Before
    fun phase() {
        Phase.require("notifications-banner")
    }

    @get:Rule
    val ui = UiScenario(grant = false)
    private val compose get() = ui.compose

    private val nudge = "Notifications are off, so you won't be told when a new number is waiting for approval."
    private val key = "notif_prompt_skipped"

    @Test
    fun bannerOffersFixAndSkipAndSkipHidesItAcrossARestart() {
        ui.launch { TestState.enableListening() }
        compose.onNodeWithText(nudge).assertIsDisplayed()
        compose.onNodeWithText("Fix").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        ui.waitUntil { TestState.prefs.getBoolean(key, false) }
        compose.waitUntil(5_000) { compose.countWithText(nudge) == 0 }
        ui.recreate()
        compose.onNodeWithText("Listening").assertIsDisplayed()
        assertTrue(compose.countWithText(nudge) == 0)
    }

    @Test
    fun aSkippedBannerStaysHiddenButRestoringDefaultsBringsItBack() {
        ui.launch {
            TestState.enableListening()
            TestState.prefs.edit().putBoolean(key, true).commit()
        }
        assertTrue(compose.countWithText(nudge) == 0)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Restore default settings").performScrollTo().performClick()
        compose.onAllNodesWithText("Restore").onLast().performClick()
        ui.waitUntil { !TestState.prefs.contains(key) }
        ui.back()
        compose.waitUntil(5_000) { compose.countWithText(nudge) > 0 }
        assertFalse(TestState.prefs.getBoolean(key, false))
    }

    @Test
    fun aSkippedBannerLeavesNoFixButtonBehind() {
        ui.launch {
            TestState.enableListening()
            TestState.prefs.edit().putBoolean(key, true).commit()
        }
        compose.onNodeWithText("Listening").assertIsDisplayed()
        assertTrue(compose.countWithText("Fix") == 0)
    }
}
