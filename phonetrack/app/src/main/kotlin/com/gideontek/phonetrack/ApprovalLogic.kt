package com.gideontek.phonetrack

object ApprovalLogic {

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
