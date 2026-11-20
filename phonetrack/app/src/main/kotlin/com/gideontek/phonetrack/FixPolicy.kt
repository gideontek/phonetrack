package com.gideontek.phonetrack

enum class FixSource { GPS, NETWORK }

/**
 * Pure rules for a fresh location fix. We ask every enabled provider at once, because GPS alone can
 * time out indoors or in a pocket while Wi-Fi and cell positioning would have answered in seconds.
 * A GPS or an accurate fix is sent at once; a coarse network fix waits a short while for a better one.
 */
object FixPolicy {
    /** How long a coarse fix waits for a better one before it is used. */
    const val GRACE_MS = 8_000L

    /** A network fix at least this accurate (metres) is used without waiting. */
    const val ACCURATE_ENOUGH_M = 50f

    fun sources(gpsOn: Boolean, networkOn: Boolean): List<FixSource> =
        listOfNotNull(FixSource.GPS.takeIf { gpsOn }, FixSource.NETWORK.takeIf { networkOn })

    /** A GPS fix, or one accurate enough, is sent at once. A fix without an accuracy is never "accurate enough". */
    fun sendNow(source: FixSource, accuracyM: Float): Boolean =
        source == FixSource.GPS || accuracyM in 0.001f..ACCURATE_ENOUGH_M

    /** True if a fix of [candidateM] metres beats [currentM]; no accuracy (0 or less) ranks last. */
    fun isBetter(candidateM: Float, currentM: Float?): Boolean {
        if (currentM == null) return true
        return rank(candidateM) < rank(currentM)
    }

    private fun rank(accuracyM: Float) = if (accuracyM > 0f) accuracyM else Float.MAX_VALUE
}
