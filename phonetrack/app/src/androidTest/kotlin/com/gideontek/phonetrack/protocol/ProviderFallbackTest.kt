package com.gideontek.phonetrack.protocol

import android.location.LocationManager
import com.gideontek.phonetrack.support.MockLocationRule
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.assertOneReply
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
