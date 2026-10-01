package com.gideontek.phonetrack.ui

import android.os.Build
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.gideontek.phonetrack.PinResult
import com.gideontek.phonetrack.PinStore
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.clickVisible
import com.gideontek.phonetrack.support.activityContext
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Settings screen, section by section. */
class SettingsTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    private fun openSettings(seed: () -> Unit = {}) {
        ui.launch(seed = seed)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("General").assertIsDisplayed()
    }

    private fun toggle(label: String) = compose.onNode(hasText(label) and isToggleable())
    private val keywordField get() = compose.onNode(hasSetTextAction() and hasText("Keyword (first word of an incoming SMS)"))

    // --- structure ---------------------------------------------------------------------

    @Test
    fun everySectionIsThere() {
        openSettings()
        for (title in listOf("General", "Replies", "Limits", "Permissions", "Security", "About")) {
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun aboutShowsTheVersionLicenceAndRepositoryLink() {
        openSettings()
        compose.onNodeWithText("PhoneTrack SMS", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GPL-3.0", substring = true).assertExists()
        compose.onNodeWithText("github.com/gideontek/phonetrack").performScrollTo().assertIsDisplayed()
    }

    // --- general -----------------------------------------------------------------------

    @Test
    fun theListeningSwitchTogglesThePrefAndMainFollows() {
        openSettings()
        toggle("SMS listening").assertIsOff()
        toggle("SMS listening").performClick()
        ui.waitUntil { TestState.prefs.getBoolean("sms_enabled", false) }
        toggle("SMS listening").assertIsOn()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Listening").assertIsDisplayed()
    }

    @Test
    fun theKeywordIsCleanedBeforeItIsStored() {
        openSettings()
        keywordField.performTextReplacement("[my word")
        ui.waitUntil { TestState.prefs.getString("sms_keyword", "") == "myword" }
        keywordField.assert(hasText("myword"))
    }

    // --- replies -----------------------------------------------------------------------

    @Test
    fun replyContentsSummarisesTheDefault() {
        openSettings()
        compose.onNodeWithText("Coordinates · Accuracy · Battery · Map link · 1\u00A0SMS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun expandingRepliesShowsEachPartAndAPreview() {
        openSettings()
        compose.onNodeWithText("Reply contents").clickVisible()
        for (part in listOf("Coordinates", "Accuracy", "Battery", "Time of fix (UTC)", "geo: link", "OpenStreetMap link")) {
            compose.onNode(hasText(part) and isToggleable()).performScrollTo().assertExists()
        }
        compose.onNodeWithText("SMS 1 of 1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun switchingAPartUpdatesTheSummaryAndStoresIt() {
        openSettings()
        compose.onNodeWithText("Reply contents").clickVisible()
        compose.onNode(hasText("Coordinates") and isToggleable()).performScrollTo().performClick()
        ui.waitUntil { !TestState.prefs.getBoolean("reply_coords", true) }
        compose.onNodeWithText("Accuracy · Battery · Map link · 1\u00A0SMS").assertExists()
        compose.onNode(hasText("Time of fix (UTC)") and isToggleable()).performScrollTo().performClick()
        ui.waitUntil { TestState.prefs.getBoolean("reply_time", false) }
        compose.onNodeWithText("Accuracy · Battery · Time of fix · Map link · 1\u00A0SMS").assertExists()
    }

    @Test
    fun theLastRemainingPartCannotBeSwitchedOff() {
        openSettings {
            val e = TestState.prefs.edit()
            for (k in listOf("coords", "accuracy", "battery", "time", "geo", "osm")) e.putBoolean("reply_$k", k == "osm")
            e.commit()
        }
        compose.onNodeWithText("Reply contents").clickVisible()
        compose.onNode(hasText("OpenStreetMap link") and isToggleable()).performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.onNode(hasText("Coordinates") and isToggleable()).assertIsEnabled()
    }

    @Test
    fun everyPartOnSplitsThePreviewIntoSeveralMessages() {
        openSettings {
            val e = TestState.prefs.edit()
            for (k in listOf("coords", "accuracy", "battery", "time", "geo", "osm")) e.putBoolean("reply_$k", true)
            e.commit()
        }
        compose.onNodeWithText("Reply contents").clickVisible()
        compose.onNodeWithText("SMS 1 of", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SMS 2 of", substring = true).assertExists()
    }

    // --- limits ------------------------------------------------------------------------

    private fun rateUp() = compose.onNodeWithContentDescription("More commands per hour").performScrollTo().performClick()
    private fun rateDown() = compose.onNodeWithContentDescription("Fewer commands per hour").performScrollTo().performClick()
    private fun rate() = TestState.prefs.getInt("rate_limit_per_hour", -1)

    @Test
    fun limitsShowTheDefaults() {
        openSettings()
        compose.onNodeWithText("Commands per number").performScrollTo()
        compose.onNodeWithText("20").assertIsDisplayed()
        compose.onNodeWithText("10").assertIsDisplayed()
    }

    @Test
    fun theRateStepperMovesByOneBelowTenAndByFiveFromTen() {
        openSettings { TestState.prefs.edit().putInt("rate_limit_per_hour", 10).commit() }
        rateDown(); ui.waitUntil { rate() == 9 }
        rateUp(); ui.waitUntil { rate() == 10 }
        rateUp(); ui.waitUntil { rate() == 15 }
        rateDown(); ui.waitUntil { rate() == 10 }
    }

    @Test
    fun theRateStepperStopsAtOneAndOneHundred() {
        openSettings { TestState.prefs.edit().putInt("rate_limit_per_hour", 1).commit() }
        compose.onNodeWithContentDescription("Fewer commands per hour").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("More commands per hour").assertIsEnabled()
    }

    @Test
    fun theRateStepperStopsAtTheTop() {
        openSettings { TestState.prefs.edit().putInt("rate_limit_per_hour", 100).commit() }
        compose.onNodeWithContentDescription("More commands per hour").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Fewer commands per hour").assertIsEnabled()
    }

    @Test
    fun theSubscriptionStepperMovesByOneAndStopsAtItsBounds() {
        val subs = { TestState.prefs.getInt("max_subscriptions", -1) }
        openSettings { TestState.prefs.edit().putInt("max_subscriptions", 2).commit() }
        compose.onNodeWithContentDescription("Fewer active subscriptions").performScrollTo().performClick()
        ui.waitUntil { subs() == 1 }
        compose.onNodeWithContentDescription("Fewer active subscriptions").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More active subscriptions").performClick()
        ui.waitUntil { subs() == 2 }
    }

    @Test
    fun theSubscriptionStepperStopsAtTwenty() {
        openSettings { TestState.prefs.edit().putInt("max_subscriptions", 20).commit() }
        compose.onNodeWithContentDescription("More active subscriptions").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun changedLimitsSurviveRotation() {
        openSettings { TestState.prefs.edit().putInt("rate_limit_per_hour", 10).commit() }
        rateUp(); ui.waitUntil { rate() == 15 }
        ui.recreate()
        compose.onNodeWithText("Commands per number").performScrollTo()
        compose.onNodeWithText("15").assertIsDisplayed()
    }

    // --- permissions -------------------------------------------------------------------

    @Test
    fun grantedPermissionsShowAPillAndTheRightRowsAppearForThisAndroidVersion() {
        openSettings()
        compose.onNodeWithText("Permissions").performScrollTo()
        var expected = 2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) expected++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) expected++
        assertEquals(expected, compose.countWithText("Granted"))
        assertEquals(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q, compose.countWithText("Background location") == 1)
        assertEquals(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU, compose.countWithText("Notifications") == 1)
        assertEquals(0, compose.countWithText("Grant"))
    }

    // --- security ----------------------------------------------------------------------

    @Test
    fun noPinOffersSetPin() {
        openSettings()
        compose.onNodeWithText("Not set. Anyone holding the phone can change settings.").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.countWithText("Remove PIN"))
    }

    @Test
    fun settingAPinValidatesAndStoresIt() {
        openSettings()
        compose.onNodeWithText("Set PIN").performScrollTo().performClick()
        compose.onNodeWithText("New PIN").performTextInput("12")
        compose.onNodeWithText("Confirm PIN").performTextInput("12")
        compose.onAllNodesWithText("Set PIN").onLast().performClick()
        compose.onNodeWithText("PIN must be at least 4 digits").assertIsDisplayed()
        compose.onNodeWithText("New PIN").performTextInput("34")
        compose.onNodeWithText("Confirm PIN").performTextInput("99")
        compose.onAllNodesWithText("Set PIN").onLast().performClick()
        compose.onNodeWithText("PINs do not match").assertIsDisplayed()
        compose.onNodeWithText("Confirm PIN").performTextReplacement("1234")
        compose.onAllNodesWithText("Set PIN").onLast().performClick()
        ui.waitUntil { PinStore.isSet(compose.activityContext()) }
        assertEquals(PinResult.Success, PinStore.verify(compose.activityContext(), "1234"))
        compose.onNodeWithText("Set. Asked when you approve, block or change settings.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun changeAndRemovePinWorkOnceUnlocked() {
        ui.launch { TestState.seedPin("1234") }
        compose.onNodeWithContentDescription("Unlock settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("1234")
        compose.onNodeWithText("Unlock").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()

        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onAllNodesWithText("Change PIN").assertCountEquals(2)
        compose.onNodeWithText("New PIN").performTextInput("5678")
        compose.onNodeWithText("Confirm PIN").performTextInput("5678")
        compose.onAllNodesWithText("Change PIN").onLast().performClick()
        ui.waitUntil { PinStore.verify(compose.activityContext(), "5678") == PinResult.Success }

        compose.onNodeWithText("Remove PIN").performScrollTo().performClick()
        ui.waitUntil { !PinStore.isSet(compose.activityContext()) }
        compose.onNodeWithText("Not set. Anyone holding the phone can change settings.").assertExists()
    }

    @Test
    fun theLockoutScheduleIsExplained() {
        openSettings()
        compose.onNodeWithText("5 wrong PINs in a row lock unlocking for 1 minute, then 5, 15 and 60 minutes.")
            .performScrollTo().assertIsDisplayed()
    }
}
