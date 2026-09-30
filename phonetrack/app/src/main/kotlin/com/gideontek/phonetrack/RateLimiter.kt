package com.gideontek.phonetrack

/** One key's counter: the window started at [start] (epoch ms) and has seen [count] allowed hits. */
data class RateWindow(val start: Long, val count: Int, val noticed: Boolean)

data class RateDecision(
    val allowed: Boolean,
    /** True exactly once per window, on the first denied hit: tell the sender, then stay quiet. */
    val sendNotice: Boolean,
    val state: Map<String, RateWindow>
)

/**
 * Pure fixed-window rate limiter. A key's window opens on its first hit and lasts [windowMs];
 * up to `limit` hits are allowed in it. Fixed windows permit up to twice the limit across a
 * boundary, which is fine for abuse protection and needs far less stored state than a sliding
 * window. Persisted by [RateStore].
 */
object RateLimiter {

    fun check(
        state: Map<String, RateWindow>,
        key: String,
        now: Long,
        limit: Int,
        windowMs: Long = SmsLimits.RATE_WINDOW_MS
    ): RateDecision {
        // Forget keys untouched for two windows so the stored state can't grow without bound.
        val live = state.filterValues { now - it.start < 2 * windowMs }
        // A window is current if it started within the last windowMs; a start in the future
        // (the clock moved backwards) is treated as expired rather than locking the key out.
        val current = live[key]?.takeIf { now - it.start in 0 until windowMs }
            ?: RateWindow(start = now, count = 0, noticed = false)

        return if (current.count < limit) {
            RateDecision(true, false, live + (key to current.copy(count = current.count + 1)))
        } else {
            RateDecision(false, !current.noticed, live + (key to current.copy(noticed = true)))
        }
    }
}
