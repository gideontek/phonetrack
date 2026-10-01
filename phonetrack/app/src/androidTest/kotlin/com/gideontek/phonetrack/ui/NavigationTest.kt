package com.gideontek.phonetrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.clickVisible
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Two screens, state-based navigation, system Back. */
class NavigationTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    @Test
    fun theGearOpensSettingsAndBackReturnsToMain() {
        ui.launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("General").assertIsDisplayed()
        ui.back()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        assertEquals(0, compose.countWithText("General"))
    }

    @Test
    fun theBackArrowInSettingsAlsoReturnsToMain() {
        ui.launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun backOnMainLeavesTheScreen() {
        ui.launch()
        ui.back()
        compose.waitUntil(5_000) { ui.leftForeground }
        assertTrue(ui.leftForeground)
    }

    @Test
    fun rotationOnSettingsStaysOnSettingsWithRepliesStillOpen() {
        ui.launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Reply contents").clickVisible()
        compose.onNodeWithText("Coordinates").assertExists()
        ui.recreate()
        compose.onNodeWithText("General").assertIsDisplayed()
        compose.onNodeWithText("Coordinates").assertExists()
    }

    @Test
    fun theTitleAndGearAreOnMain() {
        ui.launch()
        compose.onNodeWithText("PhoneTrack").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
