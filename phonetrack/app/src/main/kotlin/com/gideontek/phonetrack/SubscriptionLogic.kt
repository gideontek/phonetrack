package com.gideontek.phonetrack

object SubscriptionLogic {

    const val MIN_TICK_MS = 60_000L
    const val MAX_TICK_MS = 15 * 60_000L

    /** Delay until the soonest subscriber is due, clamped to [MIN_TICK_MS, MAX_TICK_MS]. */
    fun nextTickDelay(subs: List<Subscription>, now: Long): Long {
        if (subs.isEmpty()) return MAX_TICK_MS
        val nextDueAt = subs.minOf { it.lastSentAt + it.freqMinutes * 60_000L }
        return (nextDueAt - now).coerceIn(MIN_TICK_MS, MAX_TICK_MS)
    }

    /** True if a location update should be sent.
     *  Always true on first update (lastLat/Lon == 0.0).
     *  Caller computes distanceMeters via Location.distanceBetween. */
    fun shouldSend(lastLat: Double, lastLon: Double, distanceMeters: Float, threshold: Int): Boolean {
        if (lastLat == 0.0 && lastLon == 0.0) return true
        return distanceMeters >= threshold
    }

    /** Returns subs whose expiresAt <= now. */
    fun expiredSubs(subs: List<Subscription>, now: Long): List<Subscription> =
        subs.filter { it.expiresAt <= now }

    /** Returns subs where (now - lastSentAt) >= freqMinutes * 60_000. */
    fun dueSubs(subs: List<Subscription>, now: Long): List<Subscription> =
        subs.filter { now - it.lastSentAt >= it.freqMinutes * 60_000L }

    /** Snaps a compass bearing (0–360°) to the nearest of 8 arrow glyphs. */
    fun bearingToArrow(bearing: Float): String {
        val b = ((bearing % 360) + 360) % 360
        val index = ((b + 22.5f) / 45f).toInt() % 8
        return arrayOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")[index]
    }
}
