package com.gideontek.phonetrack.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.gideontek.phonetrack.EventLogStore
import com.gideontek.phonetrack.EventType
import com.gideontek.phonetrack.HomeViewModel
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings > Diagnostics and the event log viewer. */
class EventLogUiTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun openSettings(seed: () -> Unit = {}) {
        ui.launch(seed = seed)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("General").assertIsDisplayed()
    }

    private fun openViewer() {
        compose.onNodeWithText("View event log").performScrollTo().performClick()
        compose.onNodeWithText("PhoneTrack event log", substring = true).assertIsDisplayed()
    }

    /** Locks the session as a restored process would find it: the screen stays, the controls turn off. */
    private fun lockSession() = ui.onActivity { ViewModelProvider(it)[HomeViewModel::class.java].lock() }

    private fun toggle() = compose.onNode(hasText("Event log") and isToggleable())

    /**
     * The clipboard text. A headless emulator never gives the app input focus, and Android 10+ denies
     * clipboard reads to an app without it, so the read is made as the shell, which may read in the background.
     */
    private fun clipboardText(): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.READ_CLIPBOARD_IN_BACKGROUND")
        try {
            var text = ""
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val cm = context.getSystemService(ClipboardManager::class.java)
                text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            }
            return text
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private fun assertTouchTarget(description: String) {
        val node = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode()
        val density = Resources.getSystem().displayMetrics.density
        val w = node.touchBoundsInRoot.width / density
        val h = node.touchBoundsInRoot.height / density
        assertTrue("$description is ${w.toInt()}x${h.toInt()} dp", w >= 47.9f && h >= 47.9f)
    }

    // --- Settings ------------------------------------------------------------------------

    @Test
    fun theDiagnosticsSectionStartsOffWithNoViewRow() {
        openSettings()
        compose.onNodeWithText("Diagnostics").performScrollTo().assertIsDisplayed()
        toggle().performScrollTo().assertIsOff()
        compose.onNodeWithText("View event log").assertDoesNotExist()
    }

    @Test
    fun switchingItOnStoresItStartsWithAnEnvSnapshotAndShowsTheViewRow() {
        openSettings()
        toggle().performScrollTo().performClick()
        ui.waitUntil { EventLogStore.isEnabled(context) }
        ui.waitUntil { EventLogStore.read(context).any { it.type == EventType.ENV } }
        toggle().assertIsOn()
        compose.onNodeWithText("View event log").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun switchingItOffWithEntriesAsksFirstAndThenDeletesThem() {
        openSettings { TestState.seedEventLog() }
        toggle().performScrollTo().performClick()
        compose.onNodeWithText("Turn off event log?").assertIsDisplayed()
        compose.onNodeWithText("Keep on").performClick()
        assertTrue("still on after Keep on", EventLogStore.isEnabled(context))
        toggle().performScrollTo().performClick()
        compose.onNodeWithText("Turn off").performClick()
        ui.waitUntil { !EventLogStore.isEnabled(context) }
        assertTrue(EventLogStore.read(context).isEmpty())
        compose.onNodeWithText("View event log").assertDoesNotExist()
    }

    @Test
    fun theToggleIsDisabledWhileSettingsAreLocked() {
        openSettings { TestState.seedEventLog() }
        toggle().performScrollTo().assertIsEnabled()
        TestState.seedPin("1234")
        lockSession()
        toggle().performScrollTo().assertIsNotEnabled()
    }

    // --- the viewer ----------------------------------------------------------------------

    @Test
    fun theViewerShowsTheHeaderAndTheEventsNewestFirst() {
        openSettings { TestState.seedEventLog() }
        openViewer()
        compose.onNodeWithText("No phone numbers, locations or message text are included", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Contact-A sent", substring = true).assertIsDisplayed()
        compose.onNodeWithText("service started (requested)", substring = true).assertIsDisplayed()
    }

    @Test
    fun anEnabledEmptyLogSaysSo() {
        openSettings { EventLogStore.setEnabled(context, true) }
        compose.onNodeWithText("View event log").performScrollTo().performClick()
        compose.onNodeWithText("No events yet").assertIsDisplayed()
    }

    @Test
    fun copyPutsTheHeaderAndTheEventsOnTheClipboard() {
        openSettings { TestState.seedEventLog() }
        openViewer()
        compose.onNodeWithContentDescription("Copy event log").performClick()
        ui.waitUntil { clipboardText().isNotEmpty() }
        val copied = clipboardText()
        assertTrue(copied, copied.startsWith("PhoneTrack event log"))
        assertTrue(copied, copied.contains("service started (requested)"))
        assertTrue(copied, copied.contains("Contact-A sent"))
    }

    @Test
    fun clearAsksFirstThenEmptiesTheLogButLeavesItOn() {
        openSettings { TestState.seedEventLog() }
        openViewer()
        compose.onNodeWithContentDescription("Clear event log").performClick()
        compose.onNodeWithText("Clear the event log?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(EventLogStore.read(context).isEmpty())
        compose.onNodeWithContentDescription("Clear event log").performClick()
        compose.onNodeWithText("Clear").performClick()
        // Clearing leaves the log on, which starts it again with an environment snapshot.
        ui.waitUntil { EventLogStore.read(context).none { it.type != EventType.ENV } }
        assertTrue(EventLogStore.isEnabled(context))
        compose.onNodeWithText("Contact-A sent", substring = true).assertDoesNotExist()
        compose.onNodeWithText("service started (requested)", substring = true).assertDoesNotExist()
    }

    @Test
    fun backFromTheViewerReturnsToSettings() {
        openSettings { TestState.seedEventLog() }
        openViewer()
        ui.back()
        compose.onNodeWithText("General").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun aLockedSessionOnlyOffersUnlock() {
        openSettings { TestState.seedEventLog() }
        openViewer()
        TestState.seedPin("1234")
        lockSession()
        compose.onNodeWithText("Unlock to view the event log.").assertIsDisplayed()
        compose.onNodeWithText("Contact-A sent", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Copy event log").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear event log").assertDoesNotExist()
        compose.onNodeWithText("Unlock").performClick()
        compose.onNodeWithText("Enter PIN").assertIsDisplayed()
    }

    // --- accessibility ----------------------------------------------------------------------

    @Test
    fun viewerButtonsAreLabelledAndLargeEnough() {
        openSettings { TestState.seedEventLog() }
        openViewer()
        for (description in listOf("Back", "Copy event log", "Clear event log")) assertTouchTarget(description)
    }

    @Test
    fun viewerButtonsStayLargeEnoughAtLargeTextSize() {
        ui.setFontScale(1.3f)
        openSettings { TestState.seedEventLog() }
        openViewer()
        for (description in listOf("Back", "Copy event log", "Clear event log")) assertTouchTarget(description)
        assertEquals(true, EventLogStore.isEnabled(context))
    }
}
