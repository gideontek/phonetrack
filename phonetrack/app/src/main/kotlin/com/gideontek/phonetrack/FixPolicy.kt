package com.gideontek.phonetrack

enum class FixSource { GPS, NETWORK }

/**
 * Pure rules for a fresh location fix. We ask every enabled provider at once, because GPS alone can
 * stay silent indoors or in a pocket while Wi-Fi and cell positioning answer in seconds. GPS is the
 * more accurate source, so a network fix is only held and sent if GPS has not answered by the
 * caller's deadline. The exception is a phone with GPS switched off: nothing better can arrive, so
 * the network fix is sent at once.
 */
object FixPolicy {
    fun sources(gpsOn: Boolean, networkOn: Boolean): List<FixSource> =
        listOfNotNull(FixSource.GPS.takeIf { gpsOn }, FixSource.NETWORK.takeIf { networkOn })

    /** A GPS fix is sent at once; so is a network fix when GPS was not asked, as no better fix can come. */
    fun sendNow(source: FixSource, gpsAsked: Boolean): Boolean = source == FixSource.GPS || !gpsAsked

    /** True if a fix of [candidateM] metres beats [currentM]; no accuracy (0 or less) ranks last. */
    fun isBetter(candidateM: Float, currentM: Float?): Boolean {
        if (currentM == null) return true
        return rank(candidateM) < rank(currentM)
    }

    private fun rank(accuracyM: Float) = if (accuracyM > 0f) accuracyM else Float.MAX_VALUE
}
