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
     * Records contact from [sender]: an unknown number is appended as PENDING (stored
     * normalized); a matching PENDING entry just gets its [ApprovalEntry.lastSeen] refreshed.
     * APPROVED and BLOCKED entries are left untouched.
     */
    fun upsertPending(entries: List<ApprovalEntry>, sender: String, now: Long): List<ApprovalEntry> {
        if (entries.none { PhoneNumber.matches(it.number, sender) }) {
            return entries + ApprovalEntry(PhoneNumber.normalize(sender), ApprovalState.PENDING, now, now)
        }
        return entries.map {
            if (it.state == ApprovalState.PENDING && PhoneNumber.matches(it.number, sender)) {
                it.copy(lastSeen = now)
            } else it
        }
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
