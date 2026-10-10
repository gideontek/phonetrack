package com.gideontek.phonetrack

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/**
 * A location fix plus the device state shown in replies. [batteryPct] outside 0..100 means
 * unknown and is never shown.
 */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Int,
    val timeMs: Long,
    val batteryPct: Int = -1,
    val charging: Boolean = false
)

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

    private const val PREFIX = "[PhoneTrack] "

    /** OpenStreetMap zoom for the link: 12 shows the surrounding city/district rather than one street. */
    private const val OSM_ZOOM = 12

    /**
     * One-shot or periodic location reply, containing what [options] select. [prevLat] / [prevLon]
     * (0.0/0.0 = none) add the movement arrow and distance since the previous update.
     */
    fun composeLocation(
        fix: LocationFix,
        options: ReplyOptions,
        prevLat: Double = 0.0,
        prevLon: Double = 0.0
    ): List<String> {
        val movement = if (prevLat != 0.0 || prevLon != 0.0) {
            val distM = haversineMeters(prevLat, prevLon, fix.lat, fix.lon)
            val bearing = initialBearing(prevLat, prevLon, fix.lat, fix.lon)
            "${SubscriptionLogic.bearingToArrow(bearing.toFloat())}${DistanceFormat.compact(distM)}"
        } else null
        val opts = options.normalized()
        val lines = textLines(fix, opts, header = null, movement = movement)
        // Selected parts that have nothing to show (battery only, but no battery reading) must
        // not leave the requester with an empty reply: fall back to the map link.
        return assemble(lines, fix, opts).ifEmpty { assemble(lines, fix, opts.copy(osm = true)) }
    }

    /**
     * Cached fix reply: the same parts as [composeLocation], headed with how old the fix is. The
     * age replaces the time-of-fix option, and battery is left out (it isn't about the fix).
     */
    fun composeLastKnown(fix: LocationFix, options: ReplyOptions, ageMs: Long): List<String> {
        // Dropping battery/time can leave nothing locating the fix (battery-only, say): normalize again.
        val opts = options.normalized().copy(battery = false, time = false).normalized()
        val header = "Last known (${formatAge(ageMs)} ago)"
        return assemble(textLines(fix, opts, header = header, movement = null), fix, opts)
    }

    /** The message text lines for the selected fields, in display order. */
    private fun textLines(fix: LocationFix, o: ReplyOptions, header: String?, movement: String?): List<String> {
        val lines = mutableListOf<String>()
        header?.let { lines += it }
        if (o.coords) lines += "Lat: ${fmt(fix.lat)}, Lon: ${fmt(fix.lon)}"
        val details = mutableListOf<String>()
        if (o.accuracy) details += "Acc: ${fix.accuracyM}m"
        if (o.battery && fix.batteryPct in 0..100) {
            details += "Bat: ${fix.batteryPct}%" + if (fix.charging) " (charging)" else ""
        }
        if (o.time) details += "Time: ${utcClock(fix.timeMs)}"
        if (details.isNotEmpty()) lines += details.joinToString(", ")
        movement?.let { lines += it }
        return lines
    }

    /**
     * Lays the parts out as SMS. The text (if any) carries the "[PhoneTrack]" prefix; the map link
     * rides in the same message when that still fits one SMS, otherwise it is its own message. The
     * `geo:` URI is always its own bare message. Order: text, geo, link (the link leads when merged).
     */
    private fun assemble(lines: List<String>, fix: LocationFix, o: ReplyOptions): List<String> {
        val la = fmt(fix.lat)
        val lo = fmt(fix.lon)
        val geo = if (o.geo) "geo:$la,$lo" else null
        val osm = if (o.osm) "https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=$OSM_ZOOM/$la/$lo" else null
        val body = lines.joinToString("\n")

        val out = mutableListOf<String>()
        if (osm != null) {
            val merged = if (body.isEmpty()) "$PREFIX$osm" else "$PREFIX$body\n$osm"
            if (SmsLength.fitsOneSms(merged)) {
                out += merged
                geo?.let { out += it }
            } else {
                if (body.isNotEmpty()) out += "$PREFIX$body"
                geo?.let { out += it }
                out += osm
            }
        } else {
            if (body.isNotEmpty()) out += "$PREFIX$body"
            geo?.let { out += it }
        }
        return out
    }

    /** "14:32Z": the UTC clock time, so the receiver's time zone doesn't matter. */
    private fun utcClock(timeMs: Long): String =
        SimpleDateFormat("HH:mm'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timeMs))

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
        val movement = if (params.dist == 0) ", regardless of movement" else ", only if moved ${DistanceFormat.compact(params.dist)}+"
        return listOf(
            "[PhoneTrack] Subscribed: update every ${params.freq} min$movement, " +
                "for ${params.hours}h (ends $ends). Text \"$keyword unsubscribe\" to stop."
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

    fun composeRateLimited(): List<String> =
        listOf("[PhoneTrack] Too many requests. Try again in a while.")

    fun composeSubscriptionLimit(): List<String> =
        listOf("[PhoneTrack] Too many active subscriptions on this phone. Try again later.")

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
