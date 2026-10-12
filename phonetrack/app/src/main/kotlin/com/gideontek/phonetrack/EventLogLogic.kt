package com.gideontek.phonetrack

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure rules for the event log: retention, aliases, buckets and the text a person reads.
 * No JSON here (the JVM unit tests have no org.json); [EventLogStore] does that.
 */
object EventLogLogic {

    const val MAX_ENTRIES = 300
    const val MAX_AGE_MS = 48L * 3_600_000L

    private val lineTime = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    private val fullTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Entries (oldest first) younger than 48 hours at [now], at most [MAX_ENTRIES] of the newest. */
    fun prune(entries: List<EventEntry>, now: Long): List<EventEntry> =
        entries.filter { now - it.t < MAX_AGE_MS }.takeLast(MAX_ENTRIES)

    fun append(entries: List<EventEntry>, entry: EventEntry): List<EventEntry> =
        prune(entries + entry, entry.t)

    /** 0 -> Contact-A, 25 -> Contact-Z, 26 -> Contact-AA. */
    fun aliasName(index: Int): String {
        var n = index
        val letters = StringBuilder()
        do {
            letters.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return "Contact-$letters"
    }

    /** The alias for [key] and the (possibly extended) map. Aliases are never reused for another key. */
    fun assignAlias(map: Map<String, String>, key: String): Pair<String, Map<String, String>> {
        map[key]?.let { return it to map }
        val name = aliasName(map.size)
        return name to (map + (key to name))
    }

    fun bucket(meters: Float): Bucket = when {
        meters < 10f -> Bucket.UNDER_10
        meters < 50f -> Bucket.M10_50
        meters < 100f -> Bucket.M50_100
        meters < 200f -> Bucket.M100_200
        meters < 500f -> Bucket.M200_500
        meters < 1_000f -> Bucket.M500_1K
        meters < 5_000f -> Bucket.K1_5
        else -> Bucket.OVER_5K
    }

    fun lateSeconds(now: Long, expectedAt: Long): Int =
        ((now - expectedAt) / 1000).toInt().coerceAtLeast(0)

    /** Keeps letters, digits and ` ._-$`, trimmed and cut to [max]: for class names and the device model. */
    fun safeText(raw: String, max: Int): String =
        raw.filter { it.isLetterOrDigit() || it in " ._-$" }.trim().take(max)

    // --- rendering -------------------------------------------------------------------------

    fun render(entries: List<EventEntry>, appVersion: String, apiLevel: Int, zone: ZoneId): String {
        val header = mutableListOf(
            "PhoneTrack event log",
            "Recorded on this phone only. No phone numbers, locations or message text are included; " +
                "senders appear as Contact-A, Contact-B, ...",
            "App $appVersion · Android API $apiLevel · keeps the last 48 hours (up to $MAX_ENTRIES events)",
        )
        if (entries.isEmpty()) return (header + "" + "No events recorded.").joinToString("\n")

        val newestFirst = entries.sortedByDescending { it.t }
        val first = newestFirst.last().t
        val last = newestFirst.first().t
        val offset = zone.rules.getOffset(Instant.ofEpochMilli(last))
        val zoneText = if (offset.totalSeconds == 0) "UTC" else "UTC${offset.id}"
        header += "Times: $zoneText · first ${fullTime.withZone(zone).format(Instant.ofEpochMilli(first))}" +
            " · last ${fullTime.withZone(zone).format(Instant.ofEpochMilli(last))}"
        val body = newestFirst.map {
            "${lineTime.withZone(zone).format(Instant.ofEpochMilli(it.t))}  ${line(it)}"
        }
        return (header + "" + body).joinToString("\n")
    }

    private fun reason(e: EventEntry) = e.reason?.let { " (${it.text})" } ?: ""

    private fun settings(e: EventEntry): String {
        val move = if ((e.distM ?: 0) == 0) "regardless of movement" else "only if moved ${e.distM} m or more"
        return "every ${e.freqMin} min, $move, for ${e.hours} h"
    }

    private fun line(e: EventEntry): String = when (e.type) {
        EventType.TICK -> tickLine(e)
        EventType.SERVICE_START -> "service started" + reason(e)
        EventType.SERVICE_STOP -> "service stopped" + reason(e)
        EventType.BOOT_RESUME ->
            if (e.reason != null) "boot: could not resume ${e.n ?: 0} subscription(s)" + reason(e)
            else "boot: resumed ${e.n ?: 0} subscription(s)"
        EventType.SUBSCRIBED -> "${e.who ?: "?"} subscribed: ${settings(e)}"
        EventType.REPLACED -> "${e.who ?: "?"} replaced subscription: ${settings(e)}"
        EventType.EXPIRED -> "${e.who ?: "?"} subscription expired"
        EventType.CANCELLED -> "${e.who ?: "?"} unsubscribed"
        EventType.SUBSCRIBE_REJECTED -> "subscribe rejected" + reason(e)
        EventType.FIX_FAIL -> "no location fix" + reason(e)
        EventType.SEND_FAIL -> "${e.who ?: "?"} send failed (${e.err ?: "error"}, ${e.parts ?: 0} part(s))"
        EventType.ENV -> envLine(e.env)
    }

    private fun tickLine(e: EventEntry): String {
        val head = "tick (late ${e.lateS ?: 0}s) · ${e.n ?: 0} subs, ${e.due ?: 0} due"
        val outcomes = e.outcomes.map {
            when (it.kind) {
                OutcomeKind.SENT -> "${it.who} sent"
                OutcomeKind.NOT_MOVED ->
                    "${it.who} skipped: moved ${it.moved?.text ?: "?"}, needs ${it.needM ?: 0}m"
                OutcomeKind.LISTENER_OFF -> "${it.who} skipped: listener off"
            }
        }
        val fix = if (e.fixS != null) listOf("fix ${e.fixS}s, ${e.acc?.text ?: "?"}") else emptyList()
        return (listOf(head) + outcomes + fix).joinToString(" · ")
    }

    private fun envLine(env: EnvInfo?): String {
        if (env == null) return "env: unknown"
        fun yn(b: Boolean) = if (b) "yes" else "no"
        return "env: fine-location ${yn(env.fineLocation)}, background-location ${yn(env.backgroundLocation)}, " +
            "notifications ${yn(env.notifications)}, location-services ${yn(env.locationServices)}, " +
            "battery-unrestricted ${yn(env.batteryUnrestricted)}, device ${env.device}"
    }
}

/**
 * Collects what one service tick did, so it is logged as a single line however the tick ends.
 * [aliasOf] turns a phone number into its alias; the number itself is never kept.
 */
class TickReport(
    startedAt: Long,
    expectedAt: Long,
    private val aliasOf: (String) -> String,
) {
    private val lateS = EventLogLogic.lateSeconds(startedAt, expectedAt)
    var subs = 0
    var due = 0
    private var fixS: Int? = null
    private var acc: Bucket? = null
    private val outcomes = mutableListOf<Outcome>()
    private var finished = false

    fun fix(seconds: Int, accuracyM: Float) {
        fixS = seconds
        acc = EventLogLogic.bucket(accuracyM)
    }

    fun sent(number: String) {
        outcomes += Outcome(aliasOf(number), OutcomeKind.SENT)
    }

    fun notMoved(number: String, movedMeters: Float, needM: Int) {
        outcomes += Outcome(aliasOf(number), OutcomeKind.NOT_MOVED, EventLogLogic.bucket(movedMeters), needM)
    }

    fun listenerOff(numbers: List<String>) {
        numbers.forEach { outcomes += Outcome(aliasOf(it), OutcomeKind.LISTENER_OFF) }
    }

    /** The TICK entry, stamped [now]; null if it was already taken (a tick is logged once). */
    fun finish(now: Long): EventEntry? {
        if (finished) return null
        finished = true
        return EventEntry(
            t = now, type = EventType.TICK, n = subs, due = due, lateS = lateS,
            fixS = fixS, acc = acc, outcomes = outcomes.toList()
        )
    }
}
