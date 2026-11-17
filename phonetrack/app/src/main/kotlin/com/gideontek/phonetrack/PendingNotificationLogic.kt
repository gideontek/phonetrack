package com.gideontek.phonetrack

/** Buttons the pending-request notification can carry, in display order. */
enum class PendingButton { BLOCK, APPROVE, APPROVE_SEND }

/** Pure rules for the pending-request notification; see [PendingAlerts] for the posting. */
object PendingNotificationLogic {

    /** The latest attempt is recent enough to answer with a location (inclusive at the window's edge). */
    fun isFresh(requestedAt: Long, now: Long): Boolean =
        now - requestedAt <= SmsLimits.PENDING_ACTION_WINDOW_MS

    /**
     * Buttons for the notification. Only a single pending number gets any (with several the
     * buttons would be ambiguous), and none when a settings PIN is set (the in-app approval
     * guard must not be bypassed). A fresh request offers Block and Approve & send; a stale one
     * Block and Approve, because a location for an old request would surprise its sender.
     */
    fun buttons(pendingCount: Int, pinSet: Boolean, requestedAt: Long, now: Long): List<PendingButton> = when {
        pendingCount != 1 || pinSet -> emptyList()
        isFresh(requestedAt, now) -> listOf(PendingButton.BLOCK, PendingButton.APPROVE_SEND)
        else -> listOf(PendingButton.BLOCK, PendingButton.APPROVE)
    }

    /** A repeat attempt may re-post the notification if the last post is at least the interval old. */
    fun shouldRepost(lastPostedAt: Long, now: Long): Boolean =
        now - lastPostedAt >= SmsLimits.PENDING_REPOST_INTERVAL_MS
}
