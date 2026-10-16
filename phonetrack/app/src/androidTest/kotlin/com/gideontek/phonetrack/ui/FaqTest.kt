package com.gideontek.phonetrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import org.junit.Rule
import org.junit.Test

/** The FAQ screen, linked from Settings. */
class FaqTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    private fun openFaq(seed: () -> Unit = {}) {
        ui.launch(seed = seed)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("FAQ").clickVisible()
        compose.onNodeWithText("How long can a text be?", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsLinksToTheFaqUnderHelp() {
        ui.launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Help").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("FAQ").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theAnswerExplainsGsm7AndUcs2WithTheLimits() {
        openFaq()
        compose.onNodeWithText("GSM-7 is the standard SMS alphabet", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("holds 160 characters", substring = true).assertExists()
        compose.onNodeWithText("UCS-2 is used for the whole text", substring = true).assertExists()
        compose.onNodeWithText("holds only 70 characters", substring = true).assertExists()
        compose.onNodeWithText("153 (GSM-7) or 67 (UCS-2)", substring = true).assertExists()
    }

    @Test
    fun backReturnsToSettingsOneLevelAtATime() {
        openFaq()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("General").assertExists()
        compose.onNodeWithText("FAQ").clickVisible()
        ui.back()
        compose.onNodeWithText("General").assertExists()
        ui.back()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        assertEquals(0, compose.countWithText("General"))
    }

    @Test
    fun theFaqIsReadableWhileSettingsAreLockedAndHasNoLockButton() {
        ui.launch { TestState.seedPin("1234") }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("1234")
        compose.onNodeWithText("Unlock").performClick()
        compose.onNodeWithText("FAQ").clickVisible()
        compose.onNodeWithText("How long can a text be?", substring = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithContentDescription("Lock settings").fetchSemanticsNodes().size)
    }
}
