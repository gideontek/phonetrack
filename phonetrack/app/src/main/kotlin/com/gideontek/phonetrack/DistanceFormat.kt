package com.gideontek.phonetrack

/**
 * Short distances for messages and the app: meters under a kilometer, then kilometers with one
 * decimal below 10 km and whole kilometers from there ("850m", "1.2km", "12km", "950km"). Always a
 * dot, never a locale comma: a reply is read on someone else's phone.
 */
object DistanceFormat {
    /** 9.95 km is the first distance that rounds to a whole 10 km. */
    private const val WHOLE_KM_FROM_M = 9_950L

    fun compact(meters: Int): String = compact(meters.toDouble())

    fun compact(meters: Double): String {
        // Round first, then pick the unit, so 999.6 m is "1km" and never "1000m".
        val m = Math.round(meters.coerceAtLeast(0.0))
        return when {
            m < 1_000L -> "${m}m"
            m >= WHOLE_KM_FROM_M -> "${(m + 500L) / 1_000L}km"
            else -> {
                val tenths = (m + 50L) / 100L
                if (tenths % 10L == 0L) "${tenths / 10L}km" else "${tenths / 10L}.${tenths % 10L}km"
            }
        }
    }
}
