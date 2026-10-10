package com.gideontek.phonetrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.gideontek.phonetrack.support.Shell
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Main status card: headline, switch, chips and the Fix banner. */
class MainStatusTest {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    @Test
    fun listeningOffSaysSoAndShowsNoProblem() {
        ui.launch()
        compose.onNodeWithText("Listening is off").assertIsDisplayed()
        compose.onNodeWithText("Fix").assertDoesNotExist()
    }

    @Test
    fun listeningOnWithEverythingGrantedShowsNoFixBanner() {
        ui.launch { TestState.enableListening() }
        compose.onNodeWithText("Listening").assertIsDisplayed()
        compose.onNodeWithText("Idle").assertIsDisplayed()
        compose.onNodeWithText("Location services on").assertIsDisplayed()
        compose.onNodeWithText("Fix").assertDoesNotExist()
    }

    @Test
    fun theSwitchTurnsListeningOnAndOff() {
        ui.launch()
        compose.onNodeWithContentDescription("SMS listening").performClick()
        ui.waitUntil { TestState.prefs.getBoolean("sms_enabled", false) }
        compose.onNodeWithText("Listening").assertIsDisplayed()
        compose.onNodeWithContentDescription("SMS listening").performClick()
        ui.waitUntil { !TestState.prefs.getBoolean("sms_enabled", true) }
        compose.onNodeWithText("Listening is off").assertIsDisplayed()
    }

    @Test
    fun locationServicesOffShowsTheBannerAndFixLeavesForSystemSettings() {
        ui.launch(locationServices = false) { TestState.enableListening() }
        compose.onNodeWithText("Location services are off on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Location services off").assertIsDisplayed()
        compose.onNodeWithText("Fix").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Fix should open a system screen, still in ${device.currentPackageName}",
            device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.pkg("com.gideontek.phonetrack")), 8_000))
        device.pressBack()
    }

    @Test
    fun theBannerFollowsLocationServicesWhileTheScreenIsOpen() {
        ui.launch { TestState.enableListening() }
        ui.setLocationServices(false)
        compose.waitUntil(8_000) { compose.countWithText("Location services are off on this phone.") > 0 }
        ui.setLocationServices(true)
        compose.waitUntil(8_000) { compose.countWithText("Location services are off on this phone.") == 0 }
    }

    @Test
    fun lastRequestChipShowsHowLongAgo() {
        ui.launch {
            TestState.enableListening()
            TestState.prefs.edit().putLong("last_receive_at", System.currentTimeMillis() - 5 * 60_000L - 20_000L).commit()
        }
        compose.onNodeWithText("Last request 5min ago").assertIsDisplayed()
    }

    @Test
    fun noLastRequestChipBeforeAnyRequest() {
        ui.launch { TestState.enableListening() }
        assertEquals(0, compose.countWithText("Last request", substring = true))
    }

    @Test
    fun subscriptionsAreCountedInTheStatusText() {
        ui.launch {
            TestState.enableListening()
            TestState.seedSubscriptions(TestState.subscription("+15550000001"), TestState.subscription("+15550000002"))
        }
        compose.onNodeWithText("Sending · 2 subscriptions running").assertIsDisplayed()
    }

    @Test
    fun anEmptyMainScreenExplainsWhereRequestsAppear() {
        ui.launch()
        compose.onNodeWithText("No requests yet. Anyone who texts your keyword shows up here.").assertIsDisplayed()
    }
}
