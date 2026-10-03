package com.gideontek.phonetrack.protocol

import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import com.gideontek.phonetrack.support.assertOneReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Who gets an answer. Silence is checked on the own number (replies observable) wherever a
 * reply could have been sent; foreign senders are checked through stored state and `last_send_at`.
 */
class ApprovalGateTest {
    @get:Rule
    val rules = Scenario.rules()

    private val stranger = "+15550001111"
    private val now get() = System.currentTimeMillis()

    @Test
    fun anUnknownNumberBecomesPendingAndGetsNoReply() {
        val s = Scenario()
        TestState.enableListening()
        s.send("phonetrack help", from = stranger)
        val entry = TestState.approvals().single()
        assertEquals(stranger, entry.number)
        assertEquals(ApprovalState.PENDING, entry.state)
        assertEquals("nothing may be texted to a stranger", 0L, TestState.lastSendAt())
    }

    @Test
    fun theOwnNumberUnknownToTheAppIsAlsoSilent() {
        val s = Scenario()
        TestState.enableListening()
        s.send("phonetrack help")
        s.assertSilent()
        assertEquals(ApprovalState.PENDING, TestState.approvals().single().state)
    }

    @Test
    fun aPendingNumberStaysSilentAndIsNotDuplicated() {
        val s = Scenario().ready("PENDING")
        repeat(3) { s.send("phonetrack help") }
        s.assertSilent()
        assertEquals(1, TestState.approvals().size)
    }

    @Test
    fun aPendingNumbersLastSeenIsRefreshedOnlyOncePerHour() {
        val old = now - 30 * 60_000L
        TestState.enableListening()
        TestState.seedApprovals(Triple(stranger, "PENDING", old))
        val s = Scenario()
        s.send("phonetrack", from = stranger)
        assertEquals("a touch inside the hour leaves lastSeen alone", old, TestState.approvals().single().lastSeen)
        val stale = now - 2 * 3_600_000L
        TestState.seedApprovals(Triple(stranger, "PENDING", stale))
        s.send("phonetrack", from = stranger)
        assertTrue("after the hour lastSeen moves forward", TestState.approvals().single().lastSeen > stale + 3_600_000L)
    }

    @Test
    fun aBlockedNumberIsIgnored() {
        val s = Scenario().ready("BLOCKED")
        s.send("phonetrack help")
        s.assertSilent()
        assertEquals(ApprovalState.BLOCKED, TestState.approvals().single().state)
    }

    @Test
    fun anApprovedNumberIsAnswered() {
        val reply = assertOneReply(Scenario().ready("APPROVED").ask("phonetrack help"))
        assertTrue(reply, reply.startsWith("[PhoneTrack] Commands:"))
    }

    @Test
    fun approvingTakesEffectOnTheNextMessage() {
        val s = Scenario().ready("PENDING")
        s.send("phonetrack help")
        s.assertSilent()
        TestState.seedApprovals(Triple(s.me, "APPROVED", now))
        assertOneReply(s.ask("phonetrack help"))
    }

    @Test
    fun blockingTakesEffectOnTheNextMessage() {
        val s = Scenario().ready("APPROVED")
        assertOneReply(s.ask("phonetrack help"))
        TestState.seedApprovals(Triple(s.me, "BLOCKED", now))
        s.send("phonetrack help")
        s.assertSilent()
    }

    @Test
    fun otherSpellingsOfOneNumberAreOnePerson() {
        // Stored as a national number; the sender arrives in international form.
        val s = Scenario()
        TestState.enableListening()
        TestState.seedApprovals(Triple("5550001111", "BLOCKED", now))
        s.send("phonetrack help", from = "+15550001111")
        assertEquals(1, TestState.approvals().size)
        assertEquals(0L, TestState.lastSendAt())
    }

    @Test
    fun aBlockCannotBeDodgedWithAnotherSpellingWhenBothAreStored() {
        val s = Scenario()
        TestState.enableListening()
        val own = s.me
        val national = own.removePrefix("+1")
        TestState.seedApprovals(Triple(own, "APPROVED", now), Triple(national, "BLOCKED", now))
        s.send("phonetrack help")
        s.assertSilent()
    }

    @Test
    fun alphanumericSendersAndShortCodesAreNeverRecorded() {
        val s = Scenario()
        TestState.enableListening()
        for (from in listOf("PhoneTrk", "12345", "AMAZON", "+4412")) {
            s.send("phonetrack", from = from)
        }
        assertTrue(TestState.approvals().toString(), TestState.approvals().isEmpty())
        assertEquals(0L, TestState.lastSendAt())
    }
}
