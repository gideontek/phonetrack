package com.gideontek.phonetrack

/** Bounds and step rules for the two abuse-limit steppers on the Settings screen. */
object LimitSteps {
    const val RATE_MIN = 1
    const val RATE_MAX = 100
    const val SUBS_MIN = 1
    const val SUBS_MAX = SmsLimits.MAX_SUBSCRIPTIONS_CEILING

    /** Below this the rate-limit stepper moves by 1, from here up by [RATE_COARSE_STEP]. */
    private const val RATE_COARSE_FROM = 10
    private const val RATE_COARSE_STEP = 5

    fun clampRate(value: Int): Int = value.coerceIn(RATE_MIN, RATE_MAX)
    fun clampSubscriptions(value: Int): Int = value.coerceIn(SUBS_MIN, SUBS_MAX)

    /** Next rate limit after a tap on +: 1 step below 10, then 5. Never skips the boundary at 10. */
    fun rateUp(value: Int): Int {
        val v = clampRate(value)
        return clampRate(if (v < RATE_COARSE_FROM) v + 1 else v + RATE_COARSE_STEP)
    }

    /** Next rate limit after a tap on −; mirrors [rateUp] so the two are inverses on 1..9, 10, 15, 20... */
    fun rateDown(value: Int): Int {
        val v = clampRate(value)
        return clampRate(if (v > RATE_COARSE_FROM) maxOf(RATE_COARSE_FROM, v - RATE_COARSE_STEP) else v - 1)
    }

    fun subscriptionsUp(value: Int): Int = clampSubscriptions(value + 1)
    fun subscriptionsDown(value: Int): Int = clampSubscriptions(value - 1)
}

/** A made-up fix used for the live preview and for counting how many SMS a reply takes. */
internal val SAMPLE_FIX = LocationFix(
    lat = 37.7749,
    lon = -122.4194,
    accuracyM = 5,
    timeMs = 1_760_000_000_000L,
    batteryPct = 85,
    charging = false
)

object ReplySummary {
    /** "Coordinates · Accuracy · Battery · Map link · 1 SMS" for the options as they will be used. */
    fun text(options: ReplyOptions): String {
        val o = options.normalized()
        val parts = buildList {
            if (o.coords) add("Coordinates")
            if (o.accuracy) add("Accuracy")
            if (o.battery) add("Battery")
            if (o.time) add("Time of fix")
            if (o.geo) add("geo: link")
            if (o.osm) add("Map link")
        }
        val count = SmsComposer.composeLocation(SAMPLE_FIX, o).size
        // Non-breaking space so "1 SMS" never splits across lines.
        return (parts + "$count\u00A0SMS").joinToString(" · ")
    }
}
