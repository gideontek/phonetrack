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

    /**
     * A keyword must be a single token, and must not start with '[': every reply starts with
     * "[PhoneTrack]", so a keyword of "[phonetrack]" would let two phones answer each other forever.
     */
    fun sanitizeKeyword(raw: String): String =
        raw.filterNot { it.isWhitespace() }.trimStart('[')
}
