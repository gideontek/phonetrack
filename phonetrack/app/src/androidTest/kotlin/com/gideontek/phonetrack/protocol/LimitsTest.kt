package com.gideontek.phonetrack.protocol

import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.PhoneNumber
import com.gideontek.phonetrack.SmsLimits
import com.gideontek.phonetrack.support.KnownIssues
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.assertOneReply
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The abuse limits: per-sender commands, new-pending numbers, the pending list and its pruning. */
class LimitsTest {
    @get:Rule
    val rules = Scenario.rules()

    private val hour = 3_600_000L
    private val notice = "[PhoneTrack] Too many requests. Try again in a while."
    private val now get() = System.currentTimeMillis()
    private fun setRateLimit(n: Int) = TestState.prefs.edit().putInt("rate_limit_per_hour", n).commit()

    @Test
    fun afterTheCapOneNoticeIsSentThenSilence() {
        KnownIssues.assumeOutboundSmsWorks()
        val s = Scenario().ready()
        setRateLimit(3)
        repeat(5) { s.send("phonetrack help") }
        val replies = s.replies(expect = 4)
        assertEquals(replies.toString(), 3, replies.count { it.startsWith("[PhoneTrack] Commands:") })
        assertEquals(replies.toString(), 1, replies.count { it == notice })
        s.send("phonetrack help")
        s.assertSilent()
    }

    @Test
    fun helpAndUnknownWordsCountTowardsTheCapToo() {
        KnownIssues.assumeOutboundSmsWorks()
        val s = Scenario().ready()
        setRateLimit(3)
        for (body in listOf("phonetrack help", "phonetrack banana", "phonetrack unsubscribe")) s.send(body)
        assertEquals(3, s.replies(expect = 3).size)
        assertEquals(notice, assertOneReply(s.ask("phonetrack help")))
    }

    @Test
    fun theLimitIsReadFromThePreferencesEachTime() {
        KnownIssues.assumeOutboundSmsWorks()
        val s = Scenario().ready()
        setRateLimit(5)
        assertOneReply(s.ask("phonetrack help"))
        setRateLimit(1)
        assertEquals(notice, assertOneReply(s.ask("phonetrack help")))
    }

    @Test
    fun aNewHourOpensANewWindow() {
        KnownIssues.assumeOutboundSmsWorks()
        val s = Scenario().ready()
        setRateLimit(3)
        TestState.seedRate("in:" + PhoneNumber.rateKey(s.me), start = now - hour - 60_000, count = 3)
        assertTrue(assertOneReply(s.ask("phonetrack help")).startsWith("[PhoneTrack] Commands:"))
    }

    @Test
    fun aWindowThirtyMinutesOldIsStillFull() {
        KnownIssues.assumeOutboundSmsWorks()
        val s = Scenario().ready()
        setRateLimit(3)
        TestState.seedRate("in:" + PhoneNumber.rateKey(s.me), start = now - 30 * 60_000L, count = 3)
        assertEquals(notice, assertOneReply(s.ask("phonetrack help")))
    }

    @Test
    fun eachSenderHasTheirOwnBudget() {
        val other = "+15550002222"
        TestState.enableListening()
        TestState.seedApprovals(Triple(other, "APPROVED", now))
        setRateLimit(1)
        val s = Scenario()
        TestState.seedRate("in:" + PhoneNumber.rateKey(s.me), start = now, count = 1)
        s.send("phonetrack help", from = other)
        val state = JSONObject(TestState.prefs.getString("rate_state", "{}")!!)
        assertEquals(1, state.getJSONObject("in:" + PhoneNumber.rateKey(other)).getInt("count"))
        assertFalse("the other sender's own window was untouched", state.getJSONObject("in:" + PhoneNumber.rateKey(s.me)).getBoolean("noticed"))
    }

    @Test
    fun spellingsOfOneNumberShareOneBudget() {
        val number = "+15550003333"
        TestState.enableListening()
        TestState.seedApprovals(Triple(number, "APPROVED", now))
        setRateLimit(1)
        val s = Scenario()
        s.send("phonetrack help", from = number)
        s.send("phonetrack help", from = "5550003333")
        val state = JSONObject(TestState.prefs.getString("rate_state", "{}")!!)
        assertEquals("one key for both spellings: ${state.keys().asSequence().toList()}", 1, state.length())
        val window = state.getJSONObject("in:5550003333")
        assertEquals(1, window.getInt("count"))
        assertTrue(window.getBoolean("noticed"))
    }

    @Test
    fun onlyTenNewNumbersAreRecordedPerHour() {
        TestState.enableListening()
        val s = Scenario()
        for (i in 1..12) s.send("phonetrack", from = "+1555100%04d".format(i))
        assertEquals(SmsLimits.NEW_PENDING_PER_HOUR, TestState.approvals().size)
        assertEquals("the first ten win", "+15551000010", TestState.approvals().last().number)
    }

    @Test
    fun theFiftyFirstPendingNumberEvictsTheOldest() {
        TestState.enableListening()
        val base = now - 10 * hour
        TestState.seedApprovals(*Array(50) { Triple("+1555200%04d".format(it), "PENDING", base + it * 1_000L) })
        Scenario().send("phonetrack", from = "+15559999999")
        val numbers = TestState.approvals().map { it.number }
        assertEquals(50, numbers.size)
        assertFalse("the oldest was evicted", "+15552000000" in numbers)
        assertTrue("+15559999999" in numbers)
        assertTrue("+15552000001" in numbers)
    }

    @Test
    fun approvedAndBlockedNumbersAreNeverEvictedOrPruned() {
        TestState.enableListening()
        val ancient = now - 40 * 24 * hour
        TestState.seedApprovals(
            Triple("+15553000001", "APPROVED", ancient),
            Triple("+15553000002", "BLOCKED", ancient),
            *Array(50) { Triple("+1555400%04d".format(it), "PENDING", now - 5 * hour + it * 1_000L) }
        )
        Scenario().send("phonetrack", from = "+15559999998")
        val byState = TestState.approvals().groupBy { it.state }
        assertEquals(1, byState[ApprovalState.APPROVED]?.size)
        assertEquals(1, byState[ApprovalState.BLOCKED]?.size)
        assertEquals(50, byState[ApprovalState.PENDING]?.size)
    }

    @Test
    fun pendingNumbersSilentForThirtyDaysArePruned() {
        TestState.enableListening()
        TestState.seedApprovals(
            Triple("+15555000001", "PENDING", now - 31 * 24 * hour),
            Triple("+15555000002", "PENDING", now - 29 * 24 * hour)
        )
        Scenario().send("phonetrack", from = "+15559999997")
        val numbers = TestState.approvals().map { it.number }
        assertFalse("+15555000001" in numbers)
        assertTrue("+15555000002" in numbers)
        assertTrue("+15559999997" in numbers)
    }
}
