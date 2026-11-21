package com.gideontek.phonetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixPolicyTest {
    @Test fun `every enabled provider is asked`() {
        assertEquals(listOf(FixSource.GPS, FixSource.NETWORK), FixPolicy.sources(gpsOn = true, networkOn = true))
        assertEquals(listOf(FixSource.GPS), FixPolicy.sources(gpsOn = true, networkOn = false))
        assertEquals(listOf(FixSource.NETWORK), FixPolicy.sources(gpsOn = false, networkOn = true))
        assertTrue(FixPolicy.sources(gpsOn = false, networkOn = false).isEmpty())
    }

    @Test fun `a GPS fix is always sent at once`() {
        assertTrue(FixPolicy.sendNow(FixSource.GPS, gpsAsked = true))
    }

    @Test fun `a network fix waits for GPS when GPS was asked`() {
        assertFalse(FixPolicy.sendNow(FixSource.NETWORK, gpsAsked = true))
    }

    @Test fun `a network fix is sent at once when GPS is off`() {
        assertTrue(FixPolicy.sendNow(FixSource.NETWORK, gpsAsked = false))
    }

    @Test fun `a smaller accuracy number is better and unknown ranks last`() {
        assertTrue(FixPolicy.isBetter(20f, null))
        assertTrue(FixPolicy.isBetter(20f, 80f))
        assertFalse(FixPolicy.isBetter(80f, 20f))
        assertFalse(FixPolicy.isBetter(20f, 20f))
        assertTrue(FixPolicy.isBetter(500f, 0f))
        assertFalse(FixPolicy.isBetter(0f, 500f))
    }
}
