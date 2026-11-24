package com.gideontek.phonetrack.protocol

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.gideontek.phonetrack.FixRequest
import com.gideontek.phonetrack.support.MockLocationRule
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val network = setOf(LocationManager.NETWORK_PROVIDER)
private val bothProviders = setOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

private fun lm() = ApplicationProvider.getApplicationContext<Context>()
    .getSystemService(Context.LOCATION_SERVICE) as LocationManager

/**
 * GPS is on but never answers (indoors, in a pocket) while the network provider does. The service waits
 * for the full 60 s deadline for GPS, then sends the network fix instead of a timeout error. Slow by design.
 */
class ProviderFallbackTest {
    @get:Rule
    val rules: RuleChain = Scenario.rules(mockLocation = false)
        .around(MockLocationRule(accuracy = 30f, reporting = network))

    @Test
    fun theNetworkFixIsSentAtTheDeadlineWhenGpsStaysSilent() {
        val s = Scenario().ready()
        val start = SystemClock.elapsedRealtime()
        s.send("phonetrack")
        val reply = assertOneReply(s.replies(1, timeoutMs = 80_000))
        val waited = SystemClock.elapsedRealtime() - start
        assertTrue(reply, reply.startsWith("[PhoneTrack] Acc: 30m"))
        assertTrue("it must wait for GPS until the deadline, replied after $waited ms", waited >= 55_000)
    }
}

/** The network answers first, GPS a few seconds later: the GPS fix is the one sent. */
class GpsWinsOverNetworkTest {
    @get:Rule
    val rules: RuleChain = Scenario.rules(mockLocation = false).around(
        MockLocationRule(accuracy = 800f, reporting = bothProviders, delayMs = mapOf(LocationManager.GPS_PROVIDER to 3_000L))
    )

    @Test
    fun aLaterGpsFixReplacesTheWaitingNetworkFix() {
        val request = FixRequest(lm(), Handler(Looper.getMainLooper()))
        var delivered: Location? = null
        val start = SystemClock.elapsedRealtime()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { request.start { delivered = it } }
        val end = start + 10_000
        while (delivered == null && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
        assertNotNull("no fix delivered", delivered)
        assertEquals(LocationManager.GPS_PROVIDER, delivered!!.provider)
        assertTrue("the network fix must not be sent early", SystemClock.elapsedRealtime() - start >= 2_500)
    }
}

/** The caller's deadline arrives while a network fix is still waiting for GPS: that fix is used, once. */
class FixAtDeadlineTest {
    @get:Rule
    val rules: RuleChain = Scenario.rules(mockLocation = false)
        .around(MockLocationRule(accuracy = 800f, reporting = network))

    @Test
    fun aHeldFixIsDeliveredAtTheDeadlineAndNothingIsDeliveredTwice() {
        val request = FixRequest(lm(), Handler(Looper.getMainLooper()))
        val main = InstrumentationRegistry.getInstrumentation()
        var delivered: Location? = null
        var count = 0
        main.runOnMainSync { request.start { delivered = it; count++ } }
        SystemClock.sleep(2_500) // the network fix has arrived by now
        assertNull("the network fix must wait for GPS", delivered)
        var used = false
        main.runOnMainSync { used = request.deliverHeld() }
        assertTrue("a held fix must be delivered at the deadline", used)
        assertNotNull(delivered)
        assertEquals(800f, delivered!!.accuracy, 0.5f)
        var again = true
        main.runOnMainSync { again = request.deliverHeld() }
        assertFalse(again)
        SystemClock.sleep(2_000)
        assertEquals(1, count)
    }
}
