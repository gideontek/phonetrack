package com.gideontek.phonetrack.protocol

import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Each command, keyword spelling and ignore rule, through the real receiver. */
class CommandMatrixTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true)

    private val helpStart = "[PhoneTrack] Commands: phonetrack | phonetrack last | "

    @Test
    fun bareKeywordRepliesWithTheCurrentLocation() {
        val reply = assertOneReply(Scenario().ready().ask("phonetrack"))
        assertTrue(reply, reply.startsWith("[PhoneTrack] Lat: 37.7749, Lon: -122.4194"))
        assertTrue(reply, reply.contains("Acc: 5m"))
        assertTrue(reply, reply.contains("https://www.openstreetmap.org/?mlat=37.7749&mlon=-122.4194"))
    }

    @Test
    fun lastRepliesFromTheCachedFixWithItsAge() {
        val reply = assertOneReply(Scenario().ready().ask("phonetrack last"))
        assertTrue(reply, Regex("""\[PhoneTrack] Last known \(\d+[smhd] ago\)""").containsMatchIn(reply))
        assertTrue(reply, reply.contains("Lat: 37.7749, Lon: -122.4194"))
        assertTrue("battery is not part of a cached fix: $reply", !reply.contains("Bat:"))
    }

    @Test
    fun helpListsTheCommands() {
        val reply = assertOneReply(Scenario().ready().ask("phonetrack help"))
        assertTrue(reply, reply.startsWith(helpStart))
        assertTrue(reply, reply.contains("phonetrack unsubscribe"))
    }

    @Test
    fun anUnknownWordGetsTheHelpReplyNotALocation() {
        val reply = assertOneReply(Scenario().ready().ask("phonetrack banana"))
        assertTrue(reply, reply.startsWith(helpStart))
    }

    @Test
    fun unsubscribeWithoutASubscriptionSaysSo() {
        val reply = assertOneReply(Scenario().ready().ask("phonetrack unsubscribe"))
        assertEquals("[PhoneTrack] You have no active location subscription.", reply)
    }

    @Test
    fun unsubscribeCancelsTheStoredSubscription() {
        val s = Scenario().ready()
        TestState.seedSubscriptions(TestState.subscription(s.me))
        val reply = assertOneReply(s.ask("phonetrack unsubscribe"))
        assertEquals("[PhoneTrack] Your location subscription has been cancelled.", reply)
        assertTrue(TestState.subscriptions().isEmpty())
    }

    @Test
    fun keywordIsCaseAndWhitespaceInsensitive() {
        val s = Scenario().ready()
        for (body in listOf("PHONETRACK help", "PhoneTrack   help", "  phonetrack help  ", "phonetrack\thelp", "phonetrack\nhelp", "phonetrack HELP")) {
            val reply = assertOneReply(s.ask(body))
            assertTrue("'$body' -> $reply", reply.startsWith(helpStart))
        }
    }

    @Test
    fun subcommandsAreCaseInsensitive() {
        val reply = assertOneReply(Scenario().ready().ask("phonetrack UnSubscribe"))
        assertTrue(reply, reply.contains("no active location subscription"))
    }

    @Test
    fun aCustomKeywordIsUsedInTheReplyAndTheDefaultStopsWorking() {
        val s = Scenario().ready(keyword = "where")
        val reply = assertOneReply(s.ask("where help"))
        assertTrue(reply, reply.startsWith("[PhoneTrack] Commands: where | where last | "))
        s.send("phonetrack help")
        s.assertSilent()
    }

    @Test
    fun theKeywordMustBeTheWholeFirstWord() {
        val s = Scenario().ready()
        for (body in listOf("phonetrackhelp", "xphonetrack help", "hello phonetrack help", "hello", "")) {
            s.send(body)
        }
        s.assertSilent()
    }

    @Test
    fun aKeywordStartingWithABracketIsIgnoredSoPhonesCannotEchoEachOther() {
        val s = Scenario().ready(keyword = "[phonetrack]")
        s.send("[phonetrack] help")
        s.assertSilent()
    }

    @Test
    fun nothingIsAnsweredWhileListeningIsOff() {
        val s = Scenario().ready()
        TestState.prefs.edit().putBoolean("sms_enabled", false).commit()
        s.send("phonetrack help")
        s.assertSilent()
    }

    @Test
    fun aBodyOfExactlyThreeHundredTwentyCharactersIsAnswered() {
        val prefix = "phonetrack help "
        val reply = assertOneReply(Scenario().ready().ask(prefix + "a".repeat(320 - prefix.length)))
        assertTrue(reply, reply.startsWith(helpStart))
    }

    @Test
    fun aBodyOfThreeHundredTwentyOneCharactersIsIgnored() {
        val prefix = "phonetrack help "
        val s = Scenario().ready()
        s.send(prefix + "a".repeat(321 - prefix.length))
        s.assertSilent()
    }
}
