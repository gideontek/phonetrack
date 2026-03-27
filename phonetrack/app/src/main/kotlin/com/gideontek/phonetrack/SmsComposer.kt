package com.gideontek.phonetrack

import kotlin.math.*

/**
 * Pure string-building utilities for all SMS messages sent by PhoneTrack.
 * No Android imports, no Context — every method returns a List<String> so
 * SmsSender can forward each element through a single sendRaw() call.
 */
object SmsComposer {

    fun composeOneShotLocation(lat: Double, lon: Double, accuracy: Int, battery: Int): List<String> = listOf(
        "[PhoneTrack] Lat: $lat, Lon: $lon\nAcc: ${accuracy}m, Bat: $battery%",
        "geo:$lat,$lon",
        "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=14/$lat/$lon"
    )

    fun composeSubscriptionLocation(
        lat: Double,
        lon: Double,
        accuracy: Int,
        prevLat: Double,
        prevLon: Double
    ): List<String> {
        val deltaStr = if (prevLat != 0.0 || prevLon != 0.0) {
            val distM = haversineMeters(prevLat, prevLon, lat, lon)
            val bearing = initialBearing(prevLat, prevLon, lat, lon)
            "\n${SubscriptionLogic.bearingToArrow(bearing.toFloat())}${distM.toInt()}m"
        } else ""
        return listOf(
            "[PhoneTrack] Lat: $lat, Lon: $lon\nAcc: ${accuracy}m$deltaStr",
            "geo:$lat,$lon",
            "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=14/$lat/$lon"
        )
    }

    fun composeSubscriptionExpired(): List<String> =
        listOf("[PhoneTrack] Your location subscription has ended.")

    fun composeSubscriptionCancelled(): List<String> =
        listOf("[PhoneTrack] Your location subscription has been cancelled.")

    fun composeUsageHint(keyword: String): List<String> =
        listOf("[PhoneTrack] Usage: $keyword subscribe [--dist N] [--freq N] [--time N]")

    fun composePermissionError(): List<String> =
        listOf("[PhoneTrack] Location permission not granted")

    fun composeServicesDisabledError(): List<String> =
        listOf("[PhoneTrack] Location unavailable (services disabled)")

    fun composeTimeoutError(): List<String> =
        listOf("[PhoneTrack] Location unavailable (timeout)")

    fun composeNoProviderError(): List<String> =
        listOf("[PhoneTrack] No location provider available")

    // -------------------------------------------------------------------------
    // Pure math helpers (no android.location.Location dependency)
    // -------------------------------------------------------------------------

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}
