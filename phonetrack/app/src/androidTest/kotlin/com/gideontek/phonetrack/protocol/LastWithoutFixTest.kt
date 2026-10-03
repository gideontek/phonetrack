package com.gideontek.phonetrack.protocol

import android.os.Build
import com.gideontek.phonetrack.LastKnownLocation
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** `last` on a device with nothing cached: location is cycled off and on, and the providers are replaced by silent ones. */
class LastWithoutFixTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true, reportFixes = false)

    @Test
    fun saysNoRecentLocationIsSaved() {
        // Before API 29 the system keeps the GPS cache of an earlier test (mock providers, location off/on and
        // disabled providers do not clear it), so "nothing saved" cannot be arranged after other tests have run.
        assumeTrue("cannot empty the system's cached GPS fix before API 29", Build.VERSION.SDK_INT >= 29)
        val s = Scenario().ready()
        assertNull("the rule should have left no cached fix", LastKnownLocation.get(s.context))
        val reply = assertOneReply(s.ask("phonetrack last"))
        assertEquals("[PhoneTrack] No recent location saved. Text \"phonetrack\" to request a new fix.", reply)
    }
}
