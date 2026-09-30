package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SmsCommandParserTest {

    @Test
    fun `no flags returns defaults`() {
        val result = SmsCommandParser.parseSubscribe(emptyList())
        assertNotNull(result)
        assertEquals(200, result!!.dist)
        assertEquals(15, result.freq)
        assertEquals(4, result.hours)
    }

    @Test
    fun `all flags explicit`() {
        val result = SmsCommandParser.parseSubscribe(
            listOf("--dist", "500", "--freq", "10", "--time", "8")
        )
        assertNotNull(result)
        assertEquals(500, result!!.dist)
        assertEquals(10, result.freq)
        assertEquals(8, result.hours)
    }

    @Test
    fun `any order`() {
        val result = SmsCommandParser.parseSubscribe(
            listOf("--time", "2", "--dist", "300", "--freq", "5")
        )
        assertNotNull(result)
        assertEquals(300, result!!.dist)
        assertEquals(5, result.freq)
        assertEquals(2, result.hours)
    }

    @Test
    fun `freq zero returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq", "0")))
    }

    @Test
    fun `freq negative returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq", "-1")))
    }

    @Test
    fun `missing value after dist flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--dist")))
    }

    @Test
    fun `missing value after freq flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq")))
    }

    @Test
    fun `missing value after time flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--time")))
    }

    @Test
    fun `non-integer dist value returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--dist", "abc")))
    }

    @Test
    fun `non-integer freq value returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq", "xyz")))
    }

    @Test
    fun `unknown flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--unknown", "5")))
    }

    @Test
    fun `only dist flag uses other defaults`() {
        val result = SmsCommandParser.parseSubscribe(listOf("--dist", "100"))
        assertNotNull(result)
        assertEquals(100, result!!.dist)
        assertEquals(15, result.freq)
        assertEquals(4, result.hours)
    }

    @Test
    fun `only freq flag uses other defaults`() {
        val result = SmsCommandParser.parseSubscribe(listOf("--freq", "30"))
        assertNotNull(result)
        assertEquals(200, result!!.dist)
        assertEquals(30, result.freq)
        assertEquals(4, result.hours)
    }

    @Test
    fun `freq equals 1 is valid`() {
        val result = SmsCommandParser.parseSubscribe(listOf("--freq", "1"))
        assertNotNull(result)
        assertEquals(1, result!!.freq)
    }

    // -------------------------------------------------------------------------
    // Bounds
    // -------------------------------------------------------------------------

    private fun error(vararg tokens: String): String {
        val result = SmsCommandParser.parseSubscribeDetailed(tokens.toList())
        assertTrue("expected an error for ${tokens.toList()}", result is SubscribeResult.Error)
        return (result as SubscribeResult.Error).message
    }

    private fun ok(vararg tokens: String): SubscribeParams {
        val result = SmsCommandParser.parseSubscribeDetailed(tokens.toList())
        assertTrue("expected success for ${tokens.toList()}", result is SubscribeResult.Ok)
        return (result as SubscribeResult.Ok).params
    }

    @Test fun `dist 0 is valid and means regardless of movement`() { assertEquals(0, ok("--dist", "0").dist) }
    @Test fun `dist at max is valid`() { assertEquals(50_000, ok("--dist", "50000").dist) }
    @Test fun `dist above max is rejected with the range`() { assertEquals("--dist must be 0-50000 (metres)", error("--dist", "50001")) }
    @Test fun `dist negative is rejected`() { assertEquals("--dist must be 0-50000 (metres)", error("--dist", "-5")) }

    @Test fun `freq at max is valid`() { assertEquals(1_440, ok("--freq", "1440").freq) }
    @Test fun `freq above max is rejected with the range`() { assertEquals("--freq must be 1-1440 (minutes)", error("--freq", "1441")) }
    @Test fun `freq zero is rejected with the range`() { assertEquals("--freq must be 1-1440 (minutes)", error("--freq", "0")) }

    @Test fun `time 1 is valid`() { assertEquals(1, ok("--time", "1").hours) }
    @Test fun `time at max is valid`() { assertEquals(168, ok("--time", "168").hours) }
    @Test fun `time zero is rejected`() { assertEquals("--time must be 1-168 (hours)", error("--time", "0")) }
    @Test fun `time negative is rejected`() { assertEquals("--time must be 1-168 (hours)", error("--time", "-3")) }
    @Test fun `time above max is rejected`() { assertEquals("--time must be 1-168 (hours)", error("--time", "169")) }

    @Test fun `values are rejected not clamped`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--time", "999999")))
    }

    // -------------------------------------------------------------------------
    // Error messages
    // -------------------------------------------------------------------------

    @Test fun `missing value names the flag`() { assertEquals("--freq needs a whole number", error("--freq")) }
    @Test fun `non-integer value names the flag`() { assertEquals("--dist needs a whole number", error("--dist", "5m")) }
    @Test fun `a flag as the value is not a number`() { assertEquals("--dist needs a whole number", error("--dist", "--freq")) }
    @Test fun `unknown option is named`() { assertEquals("Unknown option --speed", error("--speed", "5")) }
    @Test fun `unknown option echo is truncated`() {
        assertEquals("Unknown option " + "x".repeat(20), error("x".repeat(200)))
    }
    @Test fun `duplicate flag is rejected`() { assertEquals("--freq given twice", error("--freq", "5", "--freq", "10")) }
    @Test fun `duplicate flag differing only in case is rejected`() {
        assertEquals("--freq given twice", error("--freq", "5", "--FREQ", "10"))
    }

    // -------------------------------------------------------------------------
    // Flag normalization
    // -------------------------------------------------------------------------

    @Test fun `flags are case-insensitive`() {
        val p = ok("--DIST", "300", "--Freq", "5", "--TIME", "2")
        assertEquals(SubscribeParams(300, 5, 2), p)
    }

    @Test fun `em dash from a phone keyboard is accepted`() {
        assertEquals(SubscribeParams(200, 5, 4), ok("—freq", "5"))
    }

    @Test fun `en dash from a phone keyboard is accepted`() {
        assertEquals(SubscribeParams(200, 15, 2), ok("–time", "2"))
    }

    // -------------------------------------------------------------------------
    // parse(): command dispatch
    // -------------------------------------------------------------------------

    @Test fun `bare keyword is a one-shot`() { assertEquals(SmsCommand.OneShot, SmsCommandParser.parse(emptyList())) }
    @Test fun `last`() { assertEquals(SmsCommand.Last, SmsCommandParser.parse(listOf("last"))) }
    @Test fun `help`() { assertEquals(SmsCommand.Help, SmsCommandParser.parse(listOf("help"))) }
    @Test fun `unsubscribe`() { assertEquals(SmsCommand.Unsubscribe, SmsCommandParser.parse(listOf("unsubscribe"))) }
    @Test fun `command words are case-insensitive`() {
        assertEquals(SmsCommand.Last, SmsCommandParser.parse(listOf("LAST")))
        assertEquals(SmsCommand.Unsubscribe, SmsCommandParser.parse(listOf("UnSubscribe")))
    }

    @Test fun `unknown word gets help instead of a location`() {
        assertEquals(SmsCommand.Help, SmsCommandParser.parse(listOf("helpp")))
        assertEquals(SmsCommand.Help, SmsCommandParser.parse(listOf("where")))
    }

    @Test fun `subscribe with no flags uses defaults`() {
        assertEquals(
            SmsCommand.Subscribe(SubscribeParams(200, 15, 4)),
            SmsCommandParser.parse(listOf("subscribe"))
        )
    }

    @Test fun `subscribe with flags`() {
        assertEquals(
            SmsCommand.Subscribe(SubscribeParams(0, 10, 8)),
            SmsCommandParser.parse(listOf("Subscribe", "--dist", "0", "--freq", "10", "--time", "8"))
        )
    }

    @Test fun `subscribe with bad flags is invalid with a message`() {
        assertEquals(
            SmsCommand.InvalidSubscribe("--time must be 1-168 (hours)"),
            SmsCommandParser.parse(listOf("subscribe", "--time", "0"))
        )
    }

    @Test fun `extra tokens after last are ignored`() {
        assertEquals(SmsCommand.Last, SmsCommandParser.parse(listOf("last", "please")))
    }
}
