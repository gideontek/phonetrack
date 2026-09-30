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
    fun `composeHelp lists every command with the keyword`() {
        val msgs = SmsComposer.composeHelp("mytrack")
        assertEquals(1, msgs.size)
        val text = msgs[0]
        assertTrue(text.contains("mytrack last"))
        assertTrue(text.contains("mytrack subscribe [--dist M] [--freq MIN] [--time H]"))
        assertTrue(text.contains("mytrack unsubscribe"))
        assertTrue(text.contains("mytrack help"))
    }

    @Test
    fun `composeHelp is plain ASCII so it stays GSM-7 friendly`() {
        assertTrue(SmsComposer.composeHelp("phonetrack")[0].all { it.code < 128 })
    }

    @Test
    fun `composeInvalidSubscribe names the problem and the usage`() {
        val msgs = SmsComposer.composeInvalidSubscribe("--freq must be 1-1440 (minutes)", "mytrack")
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("--freq must be 1-1440 (minutes)"))
        assertTrue(msgs[0].contains("mytrack subscribe"))
    }

    // -------------------------------------------------------------------------
    // composeSubscribeAck
    // -------------------------------------------------------------------------

    // 2026-09-30 18:32:00 UTC
    private val endsAt = 1_790_793_120_000L

    @Test
    fun `composeSubscribeAck states frequency movement duration and end time in UTC`() {
        val msgs = SmsComposer.composeSubscribeAck("mytrack", SubscribeParams(200, 15, 4), endsAt)
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("update every 15 min"))
        assertTrue(msgs[0].contains("only if moved 200m+"))
        assertTrue(msgs[0].contains("for 4h"))
        assertTrue(msgs[0].contains("ends Sep 30 18:32Z"))
        assertTrue(msgs[0].contains("\"mytrack unsubscribe\""))
    }

    @Test
    fun `composeSubscribeAck with dist 0 says regardless of movement`() {
        val msgs = SmsComposer.composeSubscribeAck("mytrack", SubscribeParams(0, 5, 1), endsAt)
        assertTrue(msgs[0].contains("regardless of movement"))
        assertFalse(msgs[0].contains("only if moved"))
    }

    // -------------------------------------------------------------------------
    // last known location
    // -------------------------------------------------------------------------

    @Test
    fun `composeLastKnown returns 3 messages headed with the fix age`() {
        val msgs = SmsComposer.composeLastKnown(51.5074, -0.1278, 12, 12 * 60_000L)
        assertEquals(3, msgs.size)
        assertEquals(
            "[PhoneTrack] Last known (12m ago)\nLat: 51.5074, Lon: -0.1278\nAcc: 12m",
            msgs[0]
        )
        assertEquals("geo:51.5074,-0.1278", msgs[1])
        assertTrue(msgs[2].contains("openstreetmap.org"))
    }

    @Test
    fun `composeNoCachedLocation suggests the one-shot command`() {
        val msgs = SmsComposer.composeNoCachedLocation("mytrack")
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("\"mytrack\""))
    }

    @Test fun `formatAge seconds`() { assertEquals("45s", SmsComposer.formatAge(45_000L)) }
    @Test fun `formatAge minutes`() { assertEquals("12m", SmsComposer.formatAge(12 * 60_000L + 30_000L)) }
    @Test fun `formatAge hours`() { assertEquals("3h", SmsComposer.formatAge(3 * 3_600_000L + 59_000L)) }
    @Test fun `formatAge days`() { assertEquals("2d", SmsComposer.formatAge(49 * 3_600_000L)) }
    @Test fun `formatAge zero`() { assertEquals("0s", SmsComposer.formatAge(0L)) }
    @Test fun `formatAge negative clamps to zero`() { assertEquals("0s", SmsComposer.formatAge(-5_000L)) }
    @Test fun `formatAge boundary 59s`() { assertEquals("59s", SmsComposer.formatAge(59_999L)) }
    @Test fun `formatAge boundary 60s becomes 1m`() { assertEquals("1m", SmsComposer.formatAge(60_000L)) }

    @Test
    fun `composePermissionError returns correct text`() {
        val msgs = SmsComposer.composePermissionError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("permission"))
    }

    @Test
    fun `composeBackgroundLocationError explains how to fix the permission`() {
        val msgs = SmsComposer.composeBackgroundLocationError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("Allow all the time"))
    }

    @Test
    fun `composeBackgroundLocationError fits one plain-ASCII SMS`() {
        val text = SmsComposer.composeBackgroundLocationError()[0]
        assertTrue(text.length <= 160)
        assertTrue(text.all { it.code < 128 })
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
