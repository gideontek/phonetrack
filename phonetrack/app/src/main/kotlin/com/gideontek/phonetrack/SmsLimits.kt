package com.gideontek.phonetrack

/** Bounds for inbound SMS commands, shared by the parser and the settings UI. */
object SmsLimits {
    const val MIN_DIST = 0          // 0 = update every interval regardless of movement
    const val MAX_DIST = 50_000     // metres
    const val MIN_FREQ = 1          // minutes
    const val MAX_FREQ = 1_440
    const val MIN_TIME = 1          // hours
    const val MAX_TIME = 168

    /** Inbound messages longer than this are ignored (cheap guard against abuse). */
    const val MAX_BODY = 320

    // --- Abuse limits -------------------------------------------------------

    /** Commands one approved sender may send per [RATE_WINDOW_MS]; pref `rate_limit_per_hour`. */
    const val DEFAULT_RATE_LIMIT_PER_HOUR = 20

    /** Concurrent (non-expired) subscriptions; pref `max_subscriptions`, coerced to 1..[MAX_SUBSCRIPTIONS_CEILING]. */
    const val DEFAULT_MAX_SUBSCRIPTIONS = 10
    const val MAX_SUBSCRIPTIONS_CEILING = 20

    /** Fixed rate-limit window. */
    const val RATE_WINDOW_MS = 3_600_000L

    /** The pending list never grows past this; the oldest pending entry is evicted for a new one. */
    const val MAX_PENDING = 50

    /** Pending entries not heard from for this long are pruned. */
    const val PENDING_MAX_AGE_MS = 30L * 24 * 3_600_000L

    /** New unknown numbers recorded as PENDING per [RATE_WINDOW_MS]; extras are dropped silently. */
    const val NEW_PENDING_PER_HOUR = 10

    /** A pending number's lastSeen is refreshed at most this often (avoids rewriting the list per message). */
    const val PENDING_TOUCH_INTERVAL_MS = 3_600_000L

    fun coerceRateLimit(raw: Int): Int = raw.coerceAtLeast(1)

    fun coerceMaxSubscriptions(raw: Int): Int = raw.coerceIn(1, MAX_SUBSCRIPTIONS_CEILING)

    /**
     * A keyword must be a single token, and must not start with '[': every reply starts with
     * "[PhoneTrack]", so a keyword of "[phonetrack]" would let two phones answer each other forever.
     */
    fun sanitizeKeyword(raw: String): String =
        raw.filterNot { it.isWhitespace() }.trimStart('[')
}
