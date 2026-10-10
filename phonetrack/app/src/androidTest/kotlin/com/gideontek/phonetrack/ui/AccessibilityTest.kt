package com.gideontek.phonetrack.ui

import android.content.res.Resources
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gideontek.phonetrack.support.RichData
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.clickVisible
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What assistive technology sees: labels, roles, live regions and touch-target size, in every theme and text size. */
class AccessibilityTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose
    private val density get() = Resources.getSystem().displayMetrics.density

    /** Every clickable or toggleable node on screen must name itself and be at least 48 dp each way. */
    private fun assertTargetsAreLabelledAndLargeEnough(screen: String) {
        val all = compose.onAllNodes(hasClickAction())
        val count = all.fetchSemanticsNodes().size
        assertTrue("$screen: no clickable nodes found", count > 0)
        val problems = mutableListOf<String>()
        for (i in 0 until count) {
            // Scroll it fully into view first: the touch area is clipped to what is visible.
            val node = all[i].performScrollTo().fetchSemanticsNode()
            val cfg = node.config
            val text = cfg.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
            val desc = cfg.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
            val name = text.ifEmpty { desc }
            if (name.isEmpty()) problems += "unlabelled clickable at ${node.boundsInRoot}"
            // The touch area (Material expands small controls to 48 dp), not the drawn size.
            val w = node.touchBoundsInRoot.width / density
            val h = node.touchBoundsInRoot.height / density
            if (w < 47.9f || h < 47.9f) problems += "'$name' is ${w.toInt()}x${h.toInt()} dp"
        }
        assertTrue("$screen: ${problems.joinToString("; ")}", problems.isEmpty())
    }

    private fun mainThenSettings(label: String) {
        compose.onNodeWithText("Needs your decision").assertIsDisplayed()
        compose.onNodeWithText("Approved and blocked numbers").clickVisible()
        assertTargetsAreLabelledAndLargeEnough("Main ($label)")
        compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Reply contents").clickVisible()
        assertTargetsAreLabelledAndLargeEnough("Settings ($label)")
    }

    @Test
    fun targetsAreLabelledAndLargeEnoughInTheLightTheme() {
        ui.launch { RichData.seed() }
        mainThenSettings("light")
    }

    @Test
    fun targetsAreLabelledAndLargeEnoughInTheDarkTheme() {
        ui.setDarkMode(true)
        ui.launch { RichData.seed() }
        mainThenSettings("dark")
    }

    @Test
    fun targetsAreLabelledAndLargeEnoughAtLargeTextSize() {
        ui.setFontScale(1.3f)
        ui.launch { RichData.seed() }
        mainThenSettings("1.3x text")
    }

    @Test
    fun keyContentStaysOnScreenAtLargeTextSize() {
        ui.setFontScale(1.3f)
        ui.launch { RichData.seed() }
        compose.onNodeWithText("Listening").assertIsDisplayed()
        compose.onNodeWithText("Needs your decision").assertIsDisplayed()
        compose.onNodeWithText("14min left of 1h").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Approved and blocked numbers").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theStreamIndicatorAddsNoNodeOfItsOwn() {
        ui.launch { TestState.enableListening() }
        assertEquals("the dot in the indicator is decorative", 0,
            compose.onAllNodesWithText("·").fetchSemanticsNodes().size)
    }

    @Test
    fun stepperValuesAreLiveRegions() {
        ui.launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        val value = compose.onNodeWithText("20").performScrollTo().fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, value.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun everyIconButtonHasAContentDescription() {
        ui.launch()
        for (description in listOf("Settings", "Lock settings", "SMS listening")) {
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        for (description in listOf("Back", "Lock settings", "Fewer commands per hour", "More commands per hour",
            "Fewer active subscriptions", "More active subscriptions")) {
            compose.onNodeWithContentDescription(description).performScrollTo().assertExists()
        }
    }
}
