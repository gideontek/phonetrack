package com.gideontek.phonetrack

/**
 * One line of the approvals list. [firstSeen] / [lastSeen] are epoch ms of the first and
 * most recent time this number contacted us while still PENDING (0 = unknown).
 */
data class ApprovalEntry(
    val number: String,
    val state: ApprovalState,
    val firstSeen: Long = 0L,
    val lastSeen: Long = 0L
)

object ApprovalLogic {

    /**
     * The state that applies to [sender], or null if we've never heard from them. Numbers are
     * compared with [PhoneNumber.matches]. If several entries match (the same person stored
     * under two spellings) the most restrictive wins, so a block can't be dodged by formatting.
     */
    fun stateFor(entries: List<ApprovalEntry>, sender: String): ApprovalState? =
        entries.filter { PhoneNumber.matches(it.number, sender) }
            .map { it.state }
            .reduceOrNull(::mostRestrictive)

    /** BLOCKED beats APPROVED beats PENDING. */
    fun mostRestrictive(a: ApprovalState, b: ApprovalState): ApprovalState = when {
        a == ApprovalState.BLOCKED || b == ApprovalState.BLOCKED -> ApprovalState.BLOCKED
        a == ApprovalState.APPROVED || b == ApprovalState.APPROVED -> ApprovalState.APPROVED
        else -> ApprovalState.PENDING
    }

    /**
     * Records contact from [sender].
     *
     * An unknown number is appended as PENDING (stored normalized). First, stale pending
     * entries are pruned, and if the list still holds [maxPending] pending entries the oldest
     * (by lastSeen) is evicted to make room; an evicted real contact just texts again.
     * APPROVED and BLOCKED entries are never evicted.
     *
     * A matching PENDING entry has its [ApprovalEntry.lastSeen] refreshed, but at most once per
     * [SmsLimits.PENDING_TOUCH_INTERVAL_MS], so a number hammering us doesn't rewrite the whole
     * list on every message. APPROVED and BLOCKED entries are left untouched.
     */
    fun upsertPending(
        entries: List<ApprovalEntry>,
        sender: String,
        now: Long,
        maxPending: Int = SmsLimits.MAX_PENDING
    ): List<ApprovalEntry> {
        if (entries.none { PhoneNumber.matches(it.number, sender) }) {
            var kept = prune(entries, now)
            val cap = maxPending.coerceAtLeast(1)
            while (kept.count { it.state == ApprovalState.PENDING } >= cap) {
                kept = evictOldestPending(kept)
            }
            return kept + ApprovalEntry(PhoneNumber.normalize(sender), ApprovalState.PENDING, now, now)
        }
        return entries.map {
            if (it.state == ApprovalState.PENDING &&
                PhoneNumber.matches(it.number, sender) &&
                now - it.lastSeen >= SmsLimits.PENDING_TOUCH_INTERVAL_MS
            ) it.copy(lastSeen = now) else it
        }
    }

    /**
     * Drops PENDING entries not heard from for more than [maxAgeMs]. APPROVED and BLOCKED
     * entries are never pruned, and a PENDING entry with no timestamp (lastSeen 0) is kept.
     */
    fun prune(
        entries: List<ApprovalEntry>,
        now: Long,
        maxAgeMs: Long = SmsLimits.PENDING_MAX_AGE_MS
    ): List<ApprovalEntry> = entries.filterNot {
        it.state == ApprovalState.PENDING && it.lastSeen != 0L && now - it.lastSeen > maxAgeMs
    }

    /** Removes the PENDING entry with the smallest lastSeen (the first one on a tie); no-op if none. */
    fun evictOldestPending(entries: List<ApprovalEntry>): List<ApprovalEntry> {
        val oldest = entries.filter { it.state == ApprovalState.PENDING }.minByOrNull { it.lastSeen }
            ?: return entries
        val index = entries.indexOfFirst { it === oldest }
        return entries.filterIndexed { i, _ -> i != index }
    }

    /**
     * Sets the state of the entry stored under exactly [number] (the UI passes the stored
     * value), if that transition is allowed. Unknown numbers and disallowed transitions
     * return the list unchanged.
     */
    fun withState(entries: List<ApprovalEntry>, number: String, state: ApprovalState): List<ApprovalEntry> =
        entries.map {
            if (it.number == number && canTransition(it.state, state)) it.copy(state = state) else it
        }

    /** Returns true if a transition from [from] to [to] is permitted.
     *  PENDING is a one-way door — nothing transitions back to it. */
    @Suppress("UNUSED_PARAMETER")
    fun canTransition(from: ApprovalState, to: ApprovalState): Boolean =
        to != ApprovalState.PENDING

    /** Sort order: PENDING first, APPROVED second, BLOCKED last. */
    fun sortKey(state: ApprovalState): Int = when (state) {
        ApprovalState.PENDING  -> 0
        ApprovalState.APPROVED -> 1
        ApprovalState.BLOCKED  -> 2
    }
}
