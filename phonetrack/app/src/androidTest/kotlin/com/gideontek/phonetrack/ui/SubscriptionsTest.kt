package com.gideontek.phonetrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gideontek.phonetrack.support.Loopback
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.countWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Active subscriptions": what each card says, Send now and Cancel. */
class SubscriptionsTest {
    @get:Rule
    val ui = UiScenario(mockLocation = true)
    private val compose get() = ui.compose

    private val other = "+15550002001"
    private val hour = 3_600_000L

    @Test
    fun theSectionIsHiddenWithoutSubscriptions() {
        ui.launch { TestState.enableListening() }
        assertEquals(0, compose.countWithText("Active subscriptions"))
    }

    @Test
    fun aCardShowsNumberCadenceTimeLeftAndProgress() {
        ui.launch { TestState.seedSubscriptions(TestState.subscription(other, expiresInMs = 2 * hour + 10 * 60_000L + 30_000L)) }
        compose.onNodeWithText("Active subscriptions").assertIsDisplayed()
        compose.onNodeWithText(other).assertIsDisplayed()
        compose.onNodeWithText("every 15 min · moves of 200 m+").assertIsDisplayed()
        compose.onNodeWithText("2 h 10 min left of 4 h").assertIsDisplayed()
        compose.onNodeWithContentDescription("Time left: 2 h 10 min left").assertExists()
    }

    @Test
    fun noMovementThresholdReadsAnyMovement() {
        ui.launch { TestState.seedSubscriptions(TestState.subscription(other, dist = 0, freq = 5, hours = 1, expiresInMs = 14 * 60_000L + 30_000L)) }
        compose.onNodeWithText("every 5 min · any movement").assertIsDisplayed()
        compose.onNodeWithText("14 min left of 1 h").assertIsDisplayed()
    }

    @Test
    fun theSoonestToEndIsListedFirst() {
        val a = "+15550002002"
        ui.launch {
            TestState.seedSubscriptions(
                TestState.subscription(other, expiresInMs = 3 * hour),
                TestState.subscription(a, expiresInMs = 1 * hour)
            )
        }
        val top = { n: String -> compose.onNodeWithText(n).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top(a) < top(other))
        compose.onNodeWithText("2").assertExists()   // the section's count
    }

    @Test
    fun anExpiredSubscriptionIsNotShown() {
        ui.launch {
            TestState.seedSubscriptions(
                TestState.subscription(other, expiresInMs = 2 * hour),
                TestState.subscription("+15550002003", expiresInMs = -60_000L)
            )
        }
        compose.onNodeWithText(other).assertIsDisplayed()
        assertEquals(0, compose.countWithText("+15550002003"))
    }

    @Test
    fun cancelRemovesTheSubscriptionAndTheCard() {
        ui.launch { TestState.seedSubscriptions(TestState.subscription(other)) }
        compose.onNodeWithText("Cancel").performClick()
        ui.waitUntil { TestState.subscriptions().isEmpty() }
        compose.waitUntil(3_000) { compose.countWithText("Active subscriptions") == 0 }
    }

    @Test
    fun cancelTextsTheSubscriberThatItWasCancelled() {
        val me = Loopback.ownNumber
        ui.launch { TestState.seedSubscriptions(TestState.subscription(me)) }
        val before = Loopback.lastId()
        compose.onNodeWithText("Cancel").performClick()
        val reply = Loopback.await(before) { it.startsWith("[PhoneTrack]") }
        assertEquals("[PhoneTrack] Your location subscription has been cancelled.", reply)
    }

    @Test
    fun sendNowTextsTheCurrentLocation() {
        val me = Loopback.ownNumber
        ui.launch { TestState.seedSubscriptions(TestState.subscription(me)) }
        val before = Loopback.lastId()
        compose.onNodeWithText("Send now").performClick()
        val reply = Loopback.await(before) { it.startsWith("[PhoneTrack]") }
        assertNotNull("no location reply arrived", reply)
        assertTrue(reply!!, reply.contains("Lat: 37.7749, Lon: -122.4194"))
    }

    @Test
    fun sendNowIsDisabledWhileLocationServicesAreOff() {
        ui.launch(locationServices = false) { TestState.seedSubscriptions(TestState.subscription(other)) }
        compose.onNodeWithText("Send now").assertIsNotEnabled()
    }
}
