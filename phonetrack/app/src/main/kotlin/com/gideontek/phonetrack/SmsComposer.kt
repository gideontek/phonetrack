package com.gideontek.phonetrack

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/**
 * Pure string-building utilities for all SMS messages sent by PhoneTrack.
 * No Android imports, no Context — every method returns a List<String> so
 * SmsSender can forward each element through a single sendRaw() call.
 */
object SmsComposer {

    /**
     * Formats a coordinate with at most 5 decimals (~1 m), no trailing zeros, always with a
     * '.' separator. Raw Double.toString() can print 17 digits or scientific notation ("1.0E-4").
     * DecimalFormat isn't thread-safe, so a new one is built per call.
     */
    private fun fmt(d: Double): String =
        DecimalFormat("0.#####", DecimalFormatSymbols(Locale.US)).format(d)

    fun composeOneShotLocation(lat: Double, lon: Double, accuracy: Int, battery: Int): List<String> {
        val la = fmt(lat)
        val lo = fmt(lon)
        return listOf(
            "[PhoneTrack] Lat: $la, Lon: $lo\nAcc: ${accuracy}m, Bat: $battery%",
            "geo:$la,$lo",
            "https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=14/$la/$lo"
        )
    }

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
        val la = fmt(lat)
        val lo = fmt(lon)
        return listOf(
            "[PhoneTrack] Lat: $la, Lon: $lo\nAcc: ${accuracy}m$deltaStr",
            "geo:$la,$lo",
            "https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=14/$la/$lo"
        )
    }

    fun composeSubscriptionExpired(): List<String> =
        listOf("[PhoneTrack] Your location subscription has ended.")

    fun composeSubscriptionCancelled(): List<String> =
        listOf("[PhoneTrack] Your location subscription has been cancelled.")

    fun composeNoSubscription(): List<String> =
        listOf("[PhoneTrack] You have no active location subscription.")

    fun composeHelp(keyword: String): List<String> = listOf(
        "[PhoneTrack] Commands: $keyword | $keyword last | " +
            "$keyword subscribe [--dist M] [--freq MIN] [--time H] | " +
            "$keyword unsubscribe | $keyword help"
    )

    fun composeInvalidSubscribe(message: String, keyword: String): List<String> = listOf(
        "[PhoneTrack] $message. Usage: $keyword subscribe [--dist M] [--freq MIN] [--time H]"
    )

    /** Confirmation sent before the first fix of a new subscription. */
    fun composeSubscribeAck(keyword: String, params: SubscribeParams, expiresAtMs: Long): List<String> {
        val ends = SimpleDateFormat("MMM d HH:mm'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(expiresAtMs))
        val movement = if (params.dist == 0) ", regardless of movement" else ", only if moved ${params.dist}m+"
        return listOf(
            "[PhoneTrack] Subscribed: update every ${params.freq} min$movement, " +
                "for ${params.hours}h (ends $ends). Text \"$keyword unsubscribe\" to stop."
        )
    }

    /** Cached fix reply: same shape as a one-shot, headed with how old the fix is. */
    fun composeLastKnown(lat: Double, lon: Double, accuracy: Int, ageMs: Long): List<String> {
        val la = fmt(lat)
        val lo = fmt(lon)
        return listOf(
            "[PhoneTrack] Last known (${formatAge(ageMs)} ago)\nLat: $la, Lon: $lo\nAcc: ${accuracy}m",
            "geo:$la,$lo",
            "https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=14/$la/$lo"
        )
    }

    fun composeNoCachedLocation(keyword: String): List<String> =
        listOf("[PhoneTrack] No recent location saved. Text \"$keyword\" to request a new fix.")

    /** Compact age: 45s, 12m, 3h, 2d. Negative values (clock skew) are treated as 0. */
    fun formatAge(ageMs: Long): String {
        val seconds = (ageMs / 1000).coerceAtLeast(0)
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h"
        return "${hours / 24}d"
    }

    fun composePermissionError(): List<String> =
        listOf("[PhoneTrack] Location permission not granted")

    fun composeBackgroundLocationError(): List<String> = listOf(
        "[PhoneTrack] Location is only allowed while the app is open. " +
            "On the phone, set PhoneTrack's location permission to \"Allow all the time\"."
    )

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
