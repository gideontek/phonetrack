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

/**
 * GPS is on but never answers (indoors, in a pocket) while the network provider does: the reply
 * must still come, instead of a timeout after a minute.
 */
class ProviderFallbackTest {
    private val network = setOf(LocationManager.NETWORK_PROVIDER)

    @get:Rule
    val rules: RuleChain = Scenario.rules(mockLocation = false)
        .around(MockLocationRule(accuracy = 30f, reporting = network))

    @Test
    fun anAccurateNetworkFixIsSentWhileGpsIsSilent() {
        val s = Scenario().ready()
        s.send("phonetrack")
        val reply = assertOneReply(s.replies(1, timeoutMs = 20_000))
        assertTrue(reply, reply.startsWith("[PhoneTrack] Acc: 30m"))
    }
}

/** A coarse network fix is used after a short wait for GPS, not dropped. */
class CoarseNetworkFixTest {
    private val network = setOf(LocationManager.NETWORK_PROVIDER)

    @get:Rule
    val rules: RuleChain = Scenario.rules(mockLocation = false)
        .around(MockLocationRule(accuracy = 800f, reporting = network))

    @Test
    fun aCoarseNetworkFixIsSentAfterTheGracePeriod() {
        val s = Scenario().ready()
        s.send("phonetrack")
        val reply = assertOneReply(s.replies(1, timeoutMs = 25_000))
        assertTrue(reply, reply.startsWith("[PhoneTrack] Acc: 800m"))
    }
}

/** The caller's deadline arrives while a coarse network fix is still waiting for GPS: that fix is used. */
class FixAtDeadlineTest {
    private val network = setOf(LocationManager.NETWORK_PROVIDER)

    @get:Rule
    val rules: RuleChain = Scenario.rules(mockLocation = false)
        .around(MockLocationRule(accuracy = 800f, reporting = network))

    private val main get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun aHeldFixIsDeliveredAtTheDeadlineAndNothingIsDeliveredTwice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val request = FixRequest(lm, Handler(Looper.getMainLooper()))
        var delivered: Location? = null
        var count = 0
        main.runOnMainSync { request.start { delivered = it; count++ } }
        // Give the coarse network fix time to arrive, well inside the 8 s wait for GPS.
        SystemClock.sleep(2_500)
        assertNull("the coarse fix must still be waiting", delivered)
        var used = false
        main.runOnMainSync { used = request.deliverHeld() }
        assertTrue("a held fix must be delivered at the deadline", used)
        assertNotNull(delivered)
        assertEquals(800f, delivered!!.accuracy, 0.5f)
        var again = true
        main.runOnMainSync { again = request.deliverHeld() }
        assertFalse(again)
        SystemClock.sleep(FixPolicyWait)
        assertEquals("the 8 s wait must not deliver it a second time", 1, count)
    }

    private companion object {
        const val FixPolicyWait = 9_000L
    }
}
