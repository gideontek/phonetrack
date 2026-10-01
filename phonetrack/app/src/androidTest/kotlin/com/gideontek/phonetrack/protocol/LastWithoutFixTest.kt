package com.gideontek.phonetrack.protocol

import com.gideontek.phonetrack.LastKnownLocation
import com.gideontek.phonetrack.support.KnownIssues
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** `last` on a device with nothing cached: location is cycled off and on, and the providers are replaced by silent ones. */
class LastWithoutFixTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true, reportFixes = false)

    @Test
    fun saysNoRecentLocationIsSaved() {
        KnownIssues.assumeOutboundSmsWorks()
        val s = Scenario().ready()
        assertNull("the rule should have left no cached fix", LastKnownLocation.get(s.context))
        val reply = assertOneReply(s.ask("phonetrack last"))
        assertEquals("[PhoneTrack] No recent location saved. Text \"phonetrack\" to request a new fix.", reply)
    }
}
