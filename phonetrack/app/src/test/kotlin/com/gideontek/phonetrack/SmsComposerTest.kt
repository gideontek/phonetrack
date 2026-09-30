package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SmsComposerTest {

    // -------------------------------------------------------------------------
    // composeOneShotLocation
    // -------------------------------------------------------------------------

    @Test
    fun `composeOneShotLocation returns 3 messages`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertEquals(3, msgs.size)
    }

    @Test
    fun `composeOneShotLocation msg0 contains battery percent`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertTrue(msgs[0].contains("85%"))
    }

    @Test
    fun `composeOneShotLocation msg1 is geo URI`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertTrue(msgs[1].startsWith("geo:"))
    }

    @Test
    fun `composeOneShotLocation msg2 is OSM URL`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertTrue(msgs[2].contains("openstreetmap.org"))
    }

    // -------------------------------------------------------------------------
    // composeSubscriptionLocation
    // -------------------------------------------------------------------------

    @Test
    fun `composeSubscriptionLocation with prev fix returns 3 messages`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 51.4, -0.1)
        assertEquals(3, msgs.size)
    }

    @Test
    fun `composeSubscriptionLocation with prev fix has delta arrow in msg0`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 51.4, -0.1)
        val arrows = listOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")
        assertTrue(arrows.any { msgs[0].contains(it) })
    }

    @Test
    fun `composeSubscriptionLocation without prev fix has no delta in msg0`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 0.0, 0.0)
        val arrows = listOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")
        assertFalse(arrows.any { msgs[0].contains(it) })
    }

    @Test
    fun `composeSubscriptionLocation msg1 is geo URI`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 0.0, 0.0)
        assertTrue(msgs[1].startsWith("geo:"))
    }

    @Test
    fun `composeSubscriptionLocation msg2 is OSM URL`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 0.0, 0.0)
        assertTrue(msgs[2].contains("openstreetmap.org"))
    }

    // -------------------------------------------------------------------------
    // Coordinate formatting
    // -------------------------------------------------------------------------

    @Test
    fun `coordinates keep short values without trailing zeros`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5074, -0.1278, 8, 73)
        assertEquals("[PhoneTrack] Lat: 51.5074, Lon: -0.1278\nAcc: 8m, Bat: 73%", msgs[0])
        assertEquals("geo:51.5074,-0.1278", msgs[1])
        assertEquals("https://www.openstreetmap.org/?mlat=51.5074&mlon=-0.1278#map=14/51.5074/-0.1278", msgs[2])
    }

    @Test
    fun `coordinates never use scientific notation`() {
        val msgs = SmsComposer.composeOneShotLocation(0.0001, -0.00002, 5, 50)
        assertTrue(msgs[0].contains("Lat: 0.0001, Lon: -0.00002"))
        assertFalse(msgs.any { it.contains("E-") })
    }

    @Test
    fun `coordinates are trimmed to 5 decimals`() {
        val msgs = SmsComposer.composeOneShotLocation(37.774929123456, -122.419415987654, 5, 50)
        assertEquals("geo:37.77493,-122.41942", msgs[1])
    }

    @Test
    fun `subscription coordinates use the same formatting`() {
        val msgs = SmsComposer.composeSubscriptionLocation(37.774929123456, -122.419415987654, 5, 0.0, 0.0)
        assertEquals("geo:37.77493,-122.41942", msgs[1])
    }

    @Test
    fun `coordinates use dot separator regardless of default locale`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val msgs = SmsComposer.composeOneShotLocation(51.5074, -0.1278, 8, 73)
            assertEquals("geo:51.5074,-0.1278", msgs[1])
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    // -------------------------------------------------------------------------
    // Single-message composers
    // -------------------------------------------------------------------------

    @Test
    fun `composeNoSubscription returns correct text`() {
        val msgs = SmsComposer.composeNoSubscription()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("no active location subscription"))
    }

    @Test
    fun `composeSubscriptionExpired returns correct text`() {
        val msgs = SmsComposer.composeSubscriptionExpired()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("subscription has ended"))
    }

    @Test
    fun `composeSubscriptionCancelled returns correct text`() {
        val msgs = SmsComposer.composeSubscriptionCancelled()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("subscription has been cancelled"))
    }

    @Test
    fun `composeUsageHint includes keyword`() {
        val msgs = SmsComposer.composeUsageHint("mytrack")
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("mytrack"))
    }

    @Test
    fun `composePermissionError returns correct text`() {
        val msgs = SmsComposer.composePermissionError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("permission"))
    }

    @Test
    fun `composeServicesDisabledError returns correct text`() {
        val msgs = SmsComposer.composeServicesDisabledError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("services disabled"))
    }

    @Test
    fun `composeTimeoutError returns correct text`() {
        val msgs = SmsComposer.composeTimeoutError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("timeout"))
    }

    @Test
    fun `composeNoProviderError returns correct text`() {
        val msgs = SmsComposer.composeNoProviderError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("provider"))
    }
}
