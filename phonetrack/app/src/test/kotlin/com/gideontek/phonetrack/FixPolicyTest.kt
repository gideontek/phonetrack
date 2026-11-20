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

    @Test fun `a GPS fix is sent at once whatever its accuracy`() {
        assertTrue(FixPolicy.sendNow(FixSource.GPS, 5f))
        assertTrue(FixPolicy.sendNow(FixSource.GPS, 0f))
        assertTrue(FixPolicy.sendNow(FixSource.GPS, 900f))
    }

    @Test fun `a network fix is sent at once only if accurate enough`() {
        assertTrue(FixPolicy.sendNow(FixSource.NETWORK, 30f))
        assertTrue(FixPolicy.sendNow(FixSource.NETWORK, FixPolicy.ACCURATE_ENOUGH_M))
        assertFalse(FixPolicy.sendNow(FixSource.NETWORK, 51f))
        assertFalse(FixPolicy.sendNow(FixSource.NETWORK, 1_800f))
    }

    @Test fun `a network fix with no accuracy waits`() {
        assertFalse(FixPolicy.sendNow(FixSource.NETWORK, 0f))
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
