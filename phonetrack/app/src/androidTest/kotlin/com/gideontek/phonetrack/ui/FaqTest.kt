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
        compose.onNodeWithText("Why did my first text get no reply?").assertIsDisplayed()
    }

    @Test
    fun settingsLinksToTheFaqUnderHelp() {
        ui.launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Help").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("FAQ").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyQuestionIsThereInOrder() {
        openFaq()
        for (question in listOf(
            "Why did my first text get no reply?",
            "I'm approved but still get no reply. Why?",
            "What does \u201CLocation permission not granted\u201D mean?",
            "What does \u201CLocation unavailable\u201D mean?",
            "What is the difference between a request and \u201Clast\u201D?",
            "How do automatic updates work?",
            "How secure is it?"
        )) {
            compose.onNodeWithText(question).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun theTroubleshootingAnswersNameTheRealCauses() {
        openFaq()
        compose.onNodeWithText("RCS chats are on.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("longer than 320 characters", substring = true).assertExists()
        compose.onNodeWithText("Allow all the time", substring = true).assertExists()
        compose.onNodeWithText("60-second countdown", substring = true).assertExists()
    }

    @Test
    fun theSubscribeAnswerQuotesTheRealRanges() {
        openFaq()
        compose.onNodeWithText("--dist M", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("(0\u201350000, default 200;", substring = true).assertExists()
        compose.onNodeWithText("(1\u20131440, default 15)", substring = true).assertExists()
        compose.onNodeWithText("(1\u2013168, default 4)", substring = true).assertExists()
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
        compose.onNodeWithText("Why did my first text get no reply?").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithContentDescription("Lock settings").fetchSemanticsNodes().size)
    }
}
