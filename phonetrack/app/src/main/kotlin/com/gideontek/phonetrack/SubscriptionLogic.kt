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

    /** True if the service should fetch a fix and send updates this tick: the SMS listener
     *  must be enabled and at least one subscriber must be due. */
    fun shouldFetch(smsEnabled: Boolean, dueCount: Int): Boolean = smsEnabled && dueCount > 0

    /**
     * True if [number] may subscribe. Replacing its own active subscription is always allowed;
     * a new one needs fewer than [max] active subscriptions. Expired entries linger in prefs
     * until the service next ticks, so they must not count against the cap.
     */
    fun canAdd(subs: List<Subscription>, number: String, now: Long, max: Int): Boolean {
        val active = subs.filter { it.expiresAt > now }
        if (active.any { PhoneNumber.matches(it.number, number) }) return true
        return active.size < max
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
