package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class NumberMigrationTest {

    private val now = 1_000_000L

    private fun entry(number: String, state: ApprovalState, first: Long = 0L, last: Long = 0L) =
        ApprovalEntry(number, state, first, last)

    private fun sub(number: String, expiresAt: Long) = Subscription(
        number = number, distMeters = 200, freqMinutes = 15, durationHours = 4,
        subscribedAt = 0L, expiresAt = expiresAt, lastLat = 0.0, lastLon = 0.0, lastSentAt = 0L
    )

    // -------------------------------------------------------------------------
    // mergeApprovals
    // -------------------------------------------------------------------------

    @Test fun `numbers are normalized`() {
        val result = NumberMigration.mergeApprovals(
            listOf(entry("(555) 123-4567", ApprovalState.APPROVED, 5L, 6L)), now
        )
        assertEquals(listOf(entry("5551234567", ApprovalState.APPROVED, 5L, 6L)), result)
    }

    @Test fun `legacy entries without timestamps get now`() {
        val result = NumberMigration.mergeApprovals(listOf(entry("+15551234567", ApprovalState.PENDING)), now)
        assertEquals(now, result[0].firstSeen)
        assertEquals(now, result[0].lastSeen)
    }

    @Test fun `existing timestamps are kept`() {
        val result = NumberMigration.mergeApprovals(
            listOf(entry("+15551234567", ApprovalState.PENDING, 11L, 22L)), now
        )
        assertEquals(11L, result[0].firstSeen)
        assertEquals(22L, result[0].lastSeen)
    }

    @Test fun `exact duplicates after normalization merge with the most restrictive state`() {
        val result = NumberMigration.mergeApprovals(
            listOf(
                entry("+1 555 123 4567", ApprovalState.PENDING, 10L, 50L),
                entry("+15551234567", ApprovalState.APPROVED, 20L, 40L),
                entry("+1-555-123-4567", ApprovalState.BLOCKED, 5L, 30L)
            ), now
        )
        assertEquals(listOf(entry("+15551234567", ApprovalState.BLOCKED, 5L, 50L)), result)
    }

    @Test fun `approved beats pending in a merge`() {
        val result = NumberMigration.mergeApprovals(
            listOf(
                entry("5551234567", ApprovalState.PENDING, 1L, 1L),
                entry("555-123-4567", ApprovalState.APPROVED, 2L, 2L)
            ), now
        )
        assertEquals(1, result.size)
        assertEquals(ApprovalState.APPROVED, result[0].state)
    }

    @Test fun `spellings that only match stay separate entries`() {
        val result = NumberMigration.mergeApprovals(
            listOf(
                entry("+15551234567", ApprovalState.APPROVED, 1L, 1L),
                entry("5551234567", ApprovalState.PENDING, 1L, 1L)
            ), now
        )
        assertEquals(2, result.size)
    }

    @Test fun `pending entries we can never reply to are dropped`() {
        val result = NumberMigration.mergeApprovals(
            listOf(
                entry("MyBank", ApprovalState.PENDING),
                entry("12345", ApprovalState.PENDING),
                entry("+15551234567", ApprovalState.PENDING)
            ), now
        )
        assertEquals(listOf("+15551234567"), result.map { it.number })
    }

    @Test fun `blocked and approved non-replyable entries are kept`() {
        val result = NumberMigration.mergeApprovals(
            listOf(
                entry("SpamCo", ApprovalState.BLOCKED),
                entry("12345", ApprovalState.APPROVED)
            ), now
        )
        assertEquals(listOf("SpamCo", "12345"), result.map { it.number })
    }

    @Test fun `blank numbers are dropped`() {
        assertEquals(emptyList<ApprovalEntry>(), NumberMigration.mergeApprovals(listOf(entry("  ", ApprovalState.APPROVED)), now))
    }

    @Test fun `first-appearance order is kept`() {
        val result = NumberMigration.mergeApprovals(
            listOf(
                entry("+15550000002", ApprovalState.APPROVED, 1L, 1L),
                entry("+15550000001", ApprovalState.APPROVED, 1L, 1L),
                entry("+15550000002", ApprovalState.APPROVED, 1L, 1L)
            ), now
        )
        assertEquals(listOf("+15550000002", "+15550000001"), result.map { it.number })
    }

    @Test fun `mergeApprovals is idempotent`() {
        val input = listOf(
            entry("(555) 123-4567", ApprovalState.PENDING),
            entry("+1 555 999 0000", ApprovalState.APPROVED, 3L, 4L),
            entry("5551234567", ApprovalState.BLOCKED, 1L, 9L),
            entry("MyBank", ApprovalState.PENDING)
        )
        val once = NumberMigration.mergeApprovals(input, now)
        assertEquals(once, NumberMigration.mergeApprovals(once, now + 999))
    }

    @Test fun `empty list stays empty`() {
        assertEquals(emptyList<ApprovalEntry>(), NumberMigration.mergeApprovals(emptyList(), now))
    }

    // -------------------------------------------------------------------------
    // mergeSubscriptions
    // -------------------------------------------------------------------------

    @Test fun `subscription numbers are normalized`() {
        val result = NumberMigration.mergeSubscriptions(listOf(sub("(555) 123-4567", 10L)))
        assertEquals("5551234567", result[0].number)
    }

    @Test fun `duplicate subscriptions keep the one that expires last`() {
        val result = NumberMigration.mergeSubscriptions(
            listOf(sub("+1 555 123 4567", 100L), sub("+15551234567", 300L), sub("+1-555-123-4567", 200L))
        )
        assertEquals(1, result.size)
        assertEquals(300L, result[0].expiresAt)
    }

    @Test fun `spellings that only match stay separate subscriptions`() {
        val result = NumberMigration.mergeSubscriptions(listOf(sub("+15551234567", 1L), sub("5551234567", 2L)))
        assertEquals(2, result.size)
    }

    @Test fun `subscription fields other than the number are preserved`() {
        val original = sub("+15551234567", 77L).copy(distMeters = 500, freqMinutes = 5, lastLat = 1.5)
        assertEquals(listOf(original), NumberMigration.mergeSubscriptions(listOf(original)))
    }

    @Test fun `mergeSubscriptions is idempotent`() {
        val once = NumberMigration.mergeSubscriptions(
            listOf(sub("(555) 123-4567", 1L), sub("555-123-4567", 5L), sub("+15559990000", 2L))
        )
        assertEquals(once, NumberMigration.mergeSubscriptions(once))
    }
}
