package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SmsComposerTest {

    // -------------------------------------------------------------------------
    // composeLocation (one-shot and periodic)
    // -------------------------------------------------------------------------

    private val url = "https://www.openstreetmap.org/?mlat=37.7749&mlon=-122.4194#map=12/37.7749/-122.4194"
    private val all = ReplyOptions(coords = true, accuracy = true, battery = true, time = true, geo = true, osm = true)

    // 2026-09-30 18:32:00 UTC
    private fun fix(charging: Boolean = false, battery: Int = 85) = LocationFix(
        lat = 37.7749, lon = -122.4194, accuracyM = 5, timeMs = 1_790_793_120_000L,
        batteryPct = battery, charging = charging
    )

    private fun only(part: String) = ReplyOptions(
        coords = part == "coords", accuracy = part == "accuracy", battery = part == "battery",
        time = part == "time", geo = part == "geo", osm = part == "osm"
    )

    @Test
    fun `the default reply is one SMS with accuracy, battery and the map link`() {
        assertEquals(
            listOf("[PhoneTrack] Acc: 5m, Bat: 85%\n$url"),
            SmsComposer.composeLocation(fix(), ReplyOptions.DEFAULT)
        )
    }

    @Test
    fun `the link-only reply is a single prefixed map link`() {
        assertEquals(listOf("[PhoneTrack] $url"), SmsComposer.composeLocation(fix(), ReplyOptions.LINK_ONLY))
    }

    @Test
    fun `each location part alone produces just that part`() {
        assertEquals(listOf("[PhoneTrack] Lat: 37.7749, Lon: -122.4194"), SmsComposer.composeLocation(fix(), only("coords")))
        assertEquals(listOf("geo:37.7749,-122.4194"), SmsComposer.composeLocation(fix(), only("geo")))
        assertEquals(listOf("[PhoneTrack] $url"), SmsComposer.composeLocation(fix(), only("osm")))
    }

    private val coordsLine = "Lat: 37.7749, Lon: -122.4194"
    private val arrows = listOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")

    @Test
    fun `coordinates on add the Lat Lon line to the default reply`() {
        assertEquals(
            listOf("[PhoneTrack] $coordsLine\nAcc: 5m, Bat: 85%\n$url"),
            SmsComposer.composeLocation(fix(), ReplyOptions.DEFAULT.copy(coords = true))
        )
    }

    @Test
    fun `coordinates on are in a one-shot, a subscription update and a last-known reply`() {
        val on = ReplyOptions.DEFAULT.copy(coords = true)
        val oneShot = SmsComposer.composeLocation(fix(), on)
        assertTrue(oneShot.toString(), oneShot.any { it.contains(coordsLine) })

        val update = SmsComposer.composeLocation(fix(), on, prevLat = 37.7649, prevLon = -122.4194)
        assertTrue(update.toString(), update.any { it.contains(coordsLine) })
        assertTrue("the update still has its movement arrow: $update", arrows.any { a -> update.any { it.contains(a) } })

        val last = SmsComposer.composeLastKnown(fix(), on, 12 * 60_000L)
        assertTrue(last.toString(), last[0].startsWith("[PhoneTrack] Last known (12m ago)\n$coordsLine\n"))
    }

    @Test
    fun `coordinates off leave the Lat Lon line out of every kind of reply`() {
        val off = ReplyOptions.DEFAULT
        assertFalse(off.coords)
        val replies = SmsComposer.composeLocation(fix(), off) +
            SmsComposer.composeLocation(fix(), off, prevLat = 37.7649, prevLon = -122.4194) +
            SmsComposer.composeLastKnown(fix(), off, 12 * 60_000L)
        assertTrue(replies.toString(), replies.none { it.contains("Lat:") || it.contains("Lon:") })
    }

    @Test
    fun `coordinates stay with the other parts when everything is on`() {
        val msgs = SmsComposer.composeLocation(fix(), all)
        assertTrue(msgs.toString(), msgs[0].contains("$coordsLine\nAcc: 5m, Bat: 85%, Time: "))
    }

    @Test
    fun `a detail part alone gets the map link, because a reply must say where the phone is`() {
        assertEquals(listOf("[PhoneTrack] Acc: 5m\n$url"), SmsComposer.composeLocation(fix(), only("accuracy")))
        assertEquals(listOf("[PhoneTrack] Bat: 85%\n$url"), SmsComposer.composeLocation(fix(), only("battery")))
        assertEquals(listOf("[PhoneTrack] Time: 18:32Z\n$url"), SmsComposer.composeLocation(fix(), only("time")))
    }

    @Test
    fun `detail parts share one line in a fixed order`() {
        val opts = ReplyOptions(accuracy = true, battery = true, time = true, osm = true)
        assertEquals(
            listOf("[PhoneTrack] Acc: 5m, Bat: 85%, Time: 18:32Z\n$url"),
            SmsComposer.composeLocation(fix(), opts)
        )
    }

    @Test
    fun `battery notes when it is charging`() {
        assertEquals(
            listOf("[PhoneTrack] Bat: 85% (charging)\n$url"),
            SmsComposer.composeLocation(fix(charging = true), only("battery"))
        )
    }

    @Test
    fun `an unknown battery level is never shown`() {
        val opts = ReplyOptions(coords = true, battery = true, osm = false)
        assertEquals(
            listOf("[PhoneTrack] Lat: 37.7749, Lon: -122.4194"),
            SmsComposer.composeLocation(fix(battery = -1), opts)
        )
    }

    @Test
    fun `selected parts with nothing to show fall back to the map link`() {
        assertEquals(listOf("[PhoneTrack] $url"), SmsComposer.composeLocation(fix(battery = -1), only("battery")))
    }

    @Test
    fun `an all-off option set falls back to the map link`() {
        val none = ReplyOptions(false, false, false, false, false, false)
        assertEquals(listOf("[PhoneTrack] $url"), SmsComposer.composeLocation(fix(), none))
    }

    @Test
    fun `with everything on and the text plus link fitting, they share one SMS`() {
        val msgs = SmsComposer.composeLocation(fix(), all)
        assertEquals(2, msgs.size)
        assertEquals(
            "[PhoneTrack] Lat: 37.7749, Lon: -122.4194\nAcc: 5m, Bat: 85%, Time: 18:32Z\n$url",
            msgs[0]
        )
        assertEquals("geo:37.7749,-122.4194", msgs[1])
    }

    @Test
    fun `when the text plus link would not fit one SMS the link is its own message`() {
        val msgs = SmsComposer.composeLocation(fix(charging = true), all)
        assertEquals(3, msgs.size)
        assertEquals("[PhoneTrack] Lat: 37.7749, Lon: -122.4194\nAcc: 5m, Bat: 85% (charging), Time: 18:32Z", msgs[0])
        assertEquals("geo:37.7749,-122.4194", msgs[1])
        assertEquals(url, msgs[2])
    }

    @Test
    fun `every message produced fits a single SMS`() {
        listOf(ReplyOptions.DEFAULT, all, only("coords"), only("geo")).forEach { opts ->
            listOf(fix(), fix(charging = true)).forEach { f ->
                SmsComposer.composeLocation(f, opts).forEach {
                    assertTrue("should fit one SMS: $it", SmsLength.fitsOneSms(it))
                }
            }
        }
    }

    @Test
    fun `the geo message is never prefixed`() {
        val msgs = SmsComposer.composeLocation(fix(), ReplyOptions(geo = true, osm = true))
        assertTrue(msgs.contains("geo:37.7749,-122.4194"))
    }

    // --- periodic movement ---

    @Test
    fun `a periodic update with a previous fix adds the movement arrow and distance`() {
        val msgs = SmsComposer.composeLocation(fix(), all, prevLat = 37.7649, prevLon = -122.4194)
        val arrows = listOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")
        assertTrue(arrows.any { msgs[0].contains(it) })
        assertTrue(msgs[0], msgs[0].contains("⇑1.1km"))
    }

    @Test
    fun `the movement distance is meters under a kilometer, then km with one decimal, then whole km`() {
        fun movement(prevLat: Double) =
            SmsComposer.composeLocation(fix(), all, prevLat = prevLat, prevLon = -122.4194)[0]
        assertTrue(movement(37.7739).contains("⇑111m"))
        assertTrue(movement(37.7649).contains("⇑1.1km"))
        assertTrue(movement(36.7749).contains("⇑111km"))
    }

    @Test
    fun `the subscribe confirmation shows long distances in km`() {
        assertTrue(SmsComposer.composeSubscribeAck("pt", SubscribeParams(50_000, 15, 4), endsAt)[0].contains("only if moved 50km+"))
        assertTrue(SmsComposer.composeSubscribeAck("pt", SubscribeParams(1_500, 15, 4), endsAt)[0].contains("only if moved 1.5km+"))
        assertTrue(SmsComposer.composeSubscribeAck("pt", SubscribeParams(200, 15, 4), endsAt)[0].contains("only if moved 200m+"))
    }

    @Test
    fun `no previous fix means no movement line`() {
        val msgs = SmsComposer.composeLocation(fix(), all, prevLat = 0.0, prevLon = 0.0)
        val arrows = listOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")
        assertFalse(msgs.any { m -> arrows.any { m.contains(it) } })
    }

    @Test
    fun `movement shows even with the link-only reply and the arrow keeps it from merging`() {
        val msgs = SmsComposer.composeLocation(fix(), ReplyOptions.LINK_ONLY, prevLat = 37.7649, prevLon = -122.4194)
        assertEquals(2, msgs.size)
        assertTrue(msgs[0].startsWith("[PhoneTrack] "))
        assertTrue(msgs[0].contains("⇑"))
        assertEquals(url, msgs[1])
    }

    // -------------------------------------------------------------------------
    // Coordinate formatting
    // -------------------------------------------------------------------------

    private fun coordsOnly(lat: Double, lon: Double) = SmsComposer.composeLocation(
        LocationFix(lat, lon, 5, 0L), ReplyOptions(coords = true, osm = false)
    )[0]

    @Test
    fun `coordinates keep short values without trailing zeros`() {
        assertEquals("[PhoneTrack] Lat: 51.5074, Lon: -0.1278", coordsOnly(51.5074, -0.1278))
    }

    @Test
    fun `coordinates never use scientific notation`() {
        val text = coordsOnly(0.0001, -0.00002)
        assertTrue(text.contains("Lat: 0.0001, Lon: -0.00002"))
        assertFalse(text.contains("E-"))
    }

    @Test
    fun `coordinates are trimmed to 5 decimals`() {
        assertEquals("[PhoneTrack] Lat: 37.77493, Lon: -122.41942", coordsOnly(37.774929123456, -122.419415987654))
    }

    @Test
    fun `links use the same formatting`() {
        val msgs = SmsComposer.composeLocation(
            LocationFix(37.774929123456, -122.419415987654, 5, 0L), ReplyOptions(geo = true, osm = true)
        )
        assertTrue(msgs.contains("geo:37.77493,-122.41942"))
        assertTrue(msgs.any { it.contains("mlat=37.77493&mlon=-122.41942#map=12/37.77493/-122.41942") })
    }

    @Test
    fun `coordinates use dot separator regardless of default locale`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("[PhoneTrack] Lat: 51.5074, Lon: -0.1278", coordsOnly(51.5074, -0.1278))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun `the time part is UTC whatever the default time zone`() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(listOf("[PhoneTrack] Time: 18:32Z\n$url"), SmsComposer.composeLocation(fix(), only("time")))
        } finally {
            java.util.TimeZone.setDefault(original)
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
    fun `last known with the default options is one message headed with the fix age, with battery`() {
        val msgs = SmsComposer.composeLastKnown(fix(), ReplyOptions.DEFAULT, 12 * 60_000L)
        assertEquals(
            listOf("[PhoneTrack] Last known (12m ago)\nAcc: 5m, Bat: 85%\n$url"),
            msgs
        )
    }

    @Test
    fun `last known with the link-only option is one message with the age and the link`() {
        assertEquals(
            listOf("[PhoneTrack] Last known (12m ago)\n$url"),
            SmsComposer.composeLastKnown(fix(), ReplyOptions.LINK_ONLY, 12 * 60_000L)
        )
    }

    @Test
    fun `last known honours the selected parts`() {
        val opts = ReplyOptions(coords = true, accuracy = true, geo = true, osm = true)
        val msgs = SmsComposer.composeLastKnown(fix(), opts, 12 * 60_000L)
        assertEquals(
            "[PhoneTrack] Last known (12m ago)\nLat: 37.7749, Lon: -122.4194\nAcc: 5m\n$url",
            msgs[0]
        )
        assertEquals("geo:37.7749,-122.4194", msgs[1])
    }

    @Test
    fun `last known keeps battery but replaces the time of fix with the age`() {
        val msgs = SmsComposer.composeLastKnown(fix(), all, 90_000L)
        assertTrue(msgs.any { it.contains("Bat: 85%") })
        assertFalse(msgs.any { it.contains("Time:") })
        assertTrue(msgs[0].startsWith("[PhoneTrack] Last known (1m ago)"))
    }

    @Test
    fun `last known with only battery on gives the age, the battery and the link`() {
        val msgs = SmsComposer.composeLastKnown(fix(), only("battery"), 5_000L)
        assertEquals(listOf("[PhoneTrack] Last known (5s ago)\nBat: 85%\n$url"), msgs)
    }

    @Test
    fun `last known with only battery on but no battery reading gives the age and the link`() {
        val msgs = SmsComposer.composeLastKnown(fix(battery = -1), only("battery"), 5_000L)
        assertEquals(listOf("[PhoneTrack] Last known (5s ago)\n$url"), msgs)
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
    fun `composeRateLimited is a single prefixed message`() {
        val msgs = SmsComposer.composeRateLimited()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].startsWith("[PhoneTrack]"))
        assertTrue(msgs[0].contains("Too many requests"))
    }

    @Test
    fun `composeSubscriptionLimit is a single prefixed message`() {
        val msgs = SmsComposer.composeSubscriptionLimit()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].startsWith("[PhoneTrack]"))
        assertTrue(msgs[0].contains("Too many active subscriptions"))
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
