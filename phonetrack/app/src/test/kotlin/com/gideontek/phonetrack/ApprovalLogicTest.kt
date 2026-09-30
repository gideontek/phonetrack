package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class ApprovalLogicTest {

    @Test fun `PENDING to APPROVED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.PENDING, ApprovalState.APPROVED))
    }

    @Test fun `PENDING to BLOCKED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.PENDING, ApprovalState.BLOCKED))
    }

    @Test fun `APPROVED to BLOCKED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.APPROVED, ApprovalState.BLOCKED))
    }

    @Test fun `BLOCKED to APPROVED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.BLOCKED, ApprovalState.APPROVED))
    }

    @Test fun `APPROVED to PENDING not allowed`() {
        assertFalse(ApprovalLogic.canTransition(ApprovalState.APPROVED, ApprovalState.PENDING))
    }

    @Test fun `BLOCKED to PENDING not allowed`() {
        assertFalse(ApprovalLogic.canTransition(ApprovalState.BLOCKED, ApprovalState.PENDING))
    }

    @Test fun `PENDING to PENDING not allowed`() {
        assertFalse(ApprovalLogic.canTransition(ApprovalState.PENDING, ApprovalState.PENDING))
    }

    @Test fun `same state APPROVED to APPROVED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.APPROVED, ApprovalState.APPROVED))
    }

    // -------------------------------------------------------------------------
    // stateFor
    // -------------------------------------------------------------------------

    private fun entry(number: String, state: ApprovalState, first: Long = 0L, last: Long = 0L) =
        ApprovalEntry(number, state, first, last)

    @Test fun `stateFor unknown sender is null`() {
        assertNull(ApprovalLogic.stateFor(emptyList(), "+15551234567"))
        assertNull(ApprovalLogic.stateFor(listOf(entry("+15559999999", ApprovalState.APPROVED)), "+15551234567"))
    }

    @Test fun `stateFor exact match`() {
        val list = listOf(entry("+15551234567", ApprovalState.APPROVED))
        assertEquals(ApprovalState.APPROVED, ApprovalLogic.stateFor(list, "+15551234567"))
    }

    @Test fun `stateFor matches a different spelling of the same number`() {
        val list = listOf(entry("+15551234567", ApprovalState.APPROVED))
        assertEquals(ApprovalState.APPROVED, ApprovalLogic.stateFor(list, "555-123-4567"))
        assertEquals(ApprovalState.APPROVED, ApprovalLogic.stateFor(list, "15551234567"))
    }

    @Test fun `stateFor blocked beats approved when both spellings are stored`() {
        val list = listOf(
            entry("+15551234567", ApprovalState.APPROVED),
            entry("5551234567", ApprovalState.BLOCKED)
        )
        assertEquals(ApprovalState.BLOCKED, ApprovalLogic.stateFor(list, "+15551234567"))
    }

    @Test fun `stateFor approved beats pending`() {
        val list = listOf(
            entry("5551234567", ApprovalState.PENDING),
            entry("+15551234567", ApprovalState.APPROVED)
        )
        assertEquals(ApprovalState.APPROVED, ApprovalLogic.stateFor(list, "5551234567"))
    }

    // -------------------------------------------------------------------------
    // mostRestrictive
    // -------------------------------------------------------------------------

    @Test fun `mostRestrictive ordering`() {
        val (p, a, b) = Triple(ApprovalState.PENDING, ApprovalState.APPROVED, ApprovalState.BLOCKED)
        assertEquals(b, ApprovalLogic.mostRestrictive(a, b))
        assertEquals(b, ApprovalLogic.mostRestrictive(b, p))
        assertEquals(a, ApprovalLogic.mostRestrictive(p, a))
        assertEquals(p, ApprovalLogic.mostRestrictive(p, p))
    }

    // -------------------------------------------------------------------------
    // upsertPending
    // -------------------------------------------------------------------------

    @Test fun `upsertPending adds an unknown sender normalized with timestamps`() {
        val result = ApprovalLogic.upsertPending(emptyList(), "(555) 123-4567", 1_000L)
        assertEquals(listOf(entry("5551234567", ApprovalState.PENDING, 1_000L, 1_000L)), result)
    }

    @Test fun `upsertPending refreshes lastSeen of a matching pending entry`() {
        val hour = SmsLimits.PENDING_TOUCH_INTERVAL_MS
        val list = listOf(entry("+15551234567", ApprovalState.PENDING, 100L, 100L))
        val result = ApprovalLogic.upsertPending(list, "5551234567", 100L + hour)
        assertEquals(listOf(entry("+15551234567", ApprovalState.PENDING, 100L, 100L + hour)), result)
    }

    @Test fun `upsertPending throttles lastSeen refreshes to once per interval`() {
        val hour = SmsLimits.PENDING_TOUCH_INTERVAL_MS
        val list = listOf(entry("+15551234567", ApprovalState.PENDING, 100L, 100L))
        assertEquals(list, ApprovalLogic.upsertPending(list, "5551234567", 100L + hour - 1))
        assertEquals(200L + hour, ApprovalLogic.upsertPending(list, "5551234567", 200L + hour)[0].lastSeen)
    }

    @Test fun `upsertPending leaves approved and blocked entries untouched`() {
        val list = listOf(
            entry("+15551234567", ApprovalState.APPROVED, 1L, 2L),
            entry("+15559999999", ApprovalState.BLOCKED, 3L, 4L)
        )
        assertEquals(list, ApprovalLogic.upsertPending(list, "5551234567", 9_000L))
        assertEquals(list, ApprovalLogic.upsertPending(list, "+15559999999", 9_000L))
    }

    @Test fun `upsertPending does not add a duplicate for a known number`() {
        val list = listOf(entry("+15551234567", ApprovalState.APPROVED))
        assertEquals(1, ApprovalLogic.upsertPending(list, "555-123-4567", 1L).size)
    }

    // -------------------------------------------------------------------------
    // withState
    // -------------------------------------------------------------------------

    @Test fun `withState approves a pending entry`() {
        val list = listOf(entry("+15551234567", ApprovalState.PENDING, 1L, 2L))
        assertEquals(
            listOf(entry("+15551234567", ApprovalState.APPROVED, 1L, 2L)),
            ApprovalLogic.withState(list, "+15551234567", ApprovalState.APPROVED)
        )
    }

    @Test fun `withState can swap approved and blocked`() {
        val list = listOf(entry("+15551234567", ApprovalState.APPROVED))
        assertEquals(
            ApprovalState.BLOCKED,
            ApprovalLogic.withState(list, "+15551234567", ApprovalState.BLOCKED)[0].state
        )
    }

    @Test fun `withState refuses to go back to pending`() {
        val list = listOf(entry("+15551234567", ApprovalState.APPROVED))
        assertEquals(list, ApprovalLogic.withState(list, "+15551234567", ApprovalState.PENDING))
    }

    @Test fun `withState ignores an unknown number and other entries`() {
        val list = listOf(
            entry("+15551234567", ApprovalState.PENDING),
            entry("+15559999999", ApprovalState.PENDING)
        )
        val result = ApprovalLogic.withState(list, "+15559999999", ApprovalState.BLOCKED)
        assertEquals(ApprovalState.PENDING, result[0].state)
        assertEquals(ApprovalState.BLOCKED, result[1].state)
        assertEquals(list, ApprovalLogic.withState(list, "+10000000000", ApprovalState.BLOCKED))
    }

    // -------------------------------------------------------------------------
    // Pending cap and eviction
    // -------------------------------------------------------------------------

    private fun pending(n: Int, last: Long) = entry("+1555000%04d".format(n), ApprovalState.PENDING, last, last)

    @Test fun `a full pending list evicts the oldest pending entry for a new sender`() {
        val list = listOf(pending(1, 300L), pending(2, 100L), pending(3, 200L))
        val result = ApprovalLogic.upsertPending(list, "+15559990000", 1_000L, maxPending = 3)
        assertEquals(3, result.size)
        assertFalse(result.any { it.number == "+15550000002" })       // oldest (lastSeen 100) is gone
        assertTrue(result.any { it.number == "+15559990000" })         // newcomer is in
        assertTrue(result.any { it.number == "+15550000001" })
    }

    @Test fun `pending list below the cap just grows`() {
        val list = listOf(pending(1, 100L), pending(2, 200L))
        assertEquals(3, ApprovalLogic.upsertPending(list, "+15559990000", 1_000L, maxPending = 3).size)
    }

    @Test fun `eviction never removes approved or blocked entries`() {
        val list = listOf(
            entry("+15551110000", ApprovalState.APPROVED, 1L, 1L),   // oldest of all
            entry("+15552220000", ApprovalState.BLOCKED, 2L, 2L),
            pending(1, 300L),
            pending(2, 400L)
        )
        val result = ApprovalLogic.upsertPending(list, "+15559990000", 1_000L, maxPending = 2)
        assertTrue(result.any { it.number == "+15551110000" && it.state == ApprovalState.APPROVED })
        assertTrue(result.any { it.number == "+15552220000" && it.state == ApprovalState.BLOCKED })
        assertFalse(result.any { it.number == "+15550000001" })       // the oldest PENDING went
        assertEquals(2, result.count { it.state == ApprovalState.PENDING })
    }

    @Test fun `many approved entries never block new pending ones`() {
        val approved = (1..80).map { entry("+1555100%04d".format(it), ApprovalState.APPROVED, 1L, 1L) }
        val result = ApprovalLogic.upsertPending(approved, "+15559990000", 1_000L, maxPending = 50)
        assertEquals(81, result.size)
    }

    @Test fun `a flood of unknown senders stays at the cap`() {
        var list = emptyList<ApprovalEntry>()
        for (i in 1..200) list = ApprovalLogic.upsertPending(list, "+1555300%04d".format(i), i * 1_000L, maxPending = 50)
        assertEquals(50, list.size)
        assertTrue(list.any { it.number == "+15553000200" })           // newest kept
        assertFalse(list.any { it.number == "+15553000001" })          // oldest evicted
    }

    @Test fun `a zero cap is treated as one so eviction terminates`() {
        val list = listOf(pending(1, 100L), pending(2, 200L))
        val result = ApprovalLogic.upsertPending(list, "+15559990000", 1_000L, maxPending = 0)
        assertEquals(listOf("+15559990000"), result.map { it.number })
    }

    @Test fun `evictOldestPending takes the first on a tie and ignores non-pending`() {
        val list = listOf(pending(1, 100L), pending(2, 100L))
        assertEquals(listOf("+15550000002"), ApprovalLogic.evictOldestPending(list).map { it.number })
        val none = listOf(entry("+15551110000", ApprovalState.APPROVED, 1L, 1L))
        assertEquals(none, ApprovalLogic.evictOldestPending(none))
    }

    @Test fun `a new sender also prunes stale pending entries`() {
        val now = 100L * SmsLimits.PENDING_MAX_AGE_MS
        val list = listOf(pending(1, now - SmsLimits.PENDING_MAX_AGE_MS - 1), pending(2, now - 1_000L))
        val result = ApprovalLogic.upsertPending(list, "+15559990000", now, maxPending = 50)
        assertEquals(listOf("+15550000002", "+15559990000"), result.map { it.number })
    }

    // -------------------------------------------------------------------------
    // prune
    // -------------------------------------------------------------------------

    @Test fun `prune removes only pending entries older than the max age`() {
        val now = 50L * SmsLimits.PENDING_MAX_AGE_MS
        val list = listOf(
            entry("+15550000001", ApprovalState.PENDING, 1L, now - SmsLimits.PENDING_MAX_AGE_MS - 1),
            entry("+15550000002", ApprovalState.PENDING, 1L, now - SmsLimits.PENDING_MAX_AGE_MS),
            entry("+15550000003", ApprovalState.APPROVED, 1L, 1L),
            entry("+15550000004", ApprovalState.BLOCKED, 1L, 1L)
        )
        assertEquals(
            listOf("+15550000002", "+15550000003", "+15550000004"),
            ApprovalLogic.prune(list, now).map { it.number }
        )
    }

    @Test fun `prune keeps a pending entry with no timestamp`() {
        val list = listOf(entry("+15550000001", ApprovalState.PENDING, 0L, 0L))
        assertEquals(list, ApprovalLogic.prune(list, Long.MAX_VALUE / 2))
    }

    @Test fun `prune of an empty list is empty`() {
        assertEquals(emptyList<ApprovalEntry>(), ApprovalLogic.prune(emptyList(), 1L))
    }
}
