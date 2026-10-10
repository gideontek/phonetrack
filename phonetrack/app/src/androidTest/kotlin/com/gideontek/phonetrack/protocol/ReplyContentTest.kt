package com.gideontek.phonetrack.protocol

import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What a location reply contains for each "Reply contents" choice, with the mocked fix 37.7749, -122.4194 +-5 m. */
class ReplyContentTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true)

    private val link = "https://www.openstreetmap.org/?mlat=37.7749&mlon=-122.4194#map=12/37.7749/-122.4194"

    /** Stores exactly these reply parts (everything else off). */
    private fun parts(vararg on: String) {
        val edit = TestState.prefs.edit()
        for (k in listOf("coords", "accuracy", "battery", "time", "geo", "osm")) edit.putBoolean("reply_$k", k in on)
        edit.commit()
    }

    private fun oneShot(): List<String> = Scenario().ready().ask("phonetrack")

    @Test
    fun theDefaultIsAccuracyBatteryAndTheMapLink() {
        val reply = assertOneReply(oneShot())
        assertTrue(reply, reply.startsWith("[PhoneTrack] Acc: 5m, Bat: "))
        assertTrue(reply, !reply.contains("Lat:"))
        assertTrue(reply, Regex("""Bat: \d+%""").containsMatchIn(reply))
        assertTrue(reply, reply.endsWith("\n$link"))
        assertTrue(reply, !reply.contains("Time:"))
    }

    @Test
    fun coordinatesOnly() {
        parts("coords")
        assertEquals("[PhoneTrack] Lat: 37.7749, Lon: -122.4194", assertOneReply(oneShot()))
    }

    @Test
    fun accuracyAloneGetsTheMapLinkBecauseAReplyMustSayWhereThePhoneIs() {
        parts("accuracy")
        assertEquals("[PhoneTrack] Acc: 5m\n$link", assertOneReply(oneShot()))
    }

    @Test
    fun batteryAloneGetsTheMapLink() {
        parts("battery")
        val reply = assertOneReply(oneShot())
        assertTrue(reply, Regex("""\[PhoneTrack] Bat: \d+%( \(charging\))?\n""" + Regex.escape(link)).matches(reply))
    }

    @Test
    fun timeOfFixIsShownAsUtc() {
        parts("time", "osm")
        val reply = assertOneReply(oneShot())
        assertTrue(reply, Regex("""\[PhoneTrack] Time: \d\d:\d\dZ\n""" + Regex.escape(link)).matches(reply))
    }

    @Test
    fun geoLinkIsAMessageOfItsOwn() {
        parts("geo")
        assertEquals("geo:37.7749,-122.4194", assertOneReply(oneShot()))
    }

    @Test
    fun mapLinkOnly() {
        parts("osm")
        assertEquals("[PhoneTrack] $link", assertOneReply(oneShot()))
    }

    @Test
    fun selectingNothingFallsBackToTheMapLink() {
        parts()
        assertEquals("[PhoneTrack] $link", assertOneReply(oneShot()))
    }

    @Test
    fun everythingOnSplitsIntoTextGeoAndLinkWithEveryPartPresent() {
        parts("coords", "accuracy", "battery", "time", "geo", "osm")
        val s = Scenario().ready()
        val replies = s.ask("phonetrack", expect = 2)
        val all = replies.joinToString("\n")
        for (part in listOf("Lat: 37.7749, Lon: -122.4194", "Acc: 5m", "Bat: ", "Time: ", link)) {
            assertTrue("missing '$part' in $replies", all.contains(part))
        }
        assertEquals("the geo URI is a bare message", 1, replies.count { it == "geo:37.7749,-122.4194" })
        assertTrue(replies.toString(), replies.size in 2..3)
    }

    @Test
    fun lastKnownHasAnAgeHeaderAndShowsBattery() {
        parts("accuracy", "battery", "osm")
        val reply = assertOneReply(Scenario().ready().ask("phonetrack last"))
        assertTrue(reply, Regex("""\[PhoneTrack] Last known \(\d+[smhd] ago\)\nAcc: 5m, Bat: \d+%( \(charging\))?\n""").containsMatchIn(reply))
        assertTrue(reply, reply.endsWith(link))
    }

    @Test
    fun lastKnownWithOnlyBatteryChosenGivesTheAgeTheBatteryAndTheLink() {
        parts("battery")
        val reply = assertOneReply(Scenario().ready().ask("phonetrack last"))
        assertTrue(reply, Regex("""\[PhoneTrack] Last known \(\d+[smhd] ago\)\nBat: \d+%( \(charging\))?\n""" + Regex.escape(link)).matches(reply))
    }
}
