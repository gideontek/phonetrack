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
        val list = listOf(entry("+15551234567", ApprovalState.PENDING, 100L, 100L))
        val result = ApprovalLogic.upsertPending(list, "5551234567", 5_000L)
        assertEquals(listOf(entry("+15551234567", ApprovalState.PENDING, 100L, 5_000L)), result)
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
}
