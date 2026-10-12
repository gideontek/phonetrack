package com.gideontek.phonetrack

/**
 * What happened. The per-subscriber results of a tick (sent, skipped) are [Outcome]s inside the
 * TICK entry rather than entries of their own. An entry has no message, coordinate or number
 * field; its only strings are an alias, an exception class name and the device model.
 * The constant names are stored in prefs: do not rename them.
 */
enum class EventType {
    TICK, SERVICE_START, SERVICE_STOP, BOOT_RESUME, SUBSCRIBED, REPLACED, EXPIRED,
    CANCELLED, SUBSCRIBE_REJECTED, FIX_FAIL, SEND_FAIL, ENV
}

/** Why something happened. [text] is what is stored and shown. */
enum class Reason(val text: String) {
    NO_PERMISSION("no-permission"),
    PROVIDER_OFF("provider-off"),
    TIMEOUT("timeout"),
    REQUESTED("requested"),
    STICKY_RESTART("sticky-restart"),
    NO_SUBS("no-subs"),
    FOREGROUND_DENIED("startForeground-denied"),
    CANNOT_START("cannot-start"),
    LIMIT("limit"),
    INVALID_FLAGS("invalid-flags");

    companion object {
        fun fromText(text: String?): Reason? = entries.firstOrNull { it.text == text }
    }
}

/** A distance or accuracy in a range, never an exact value (exact movement could be reconstructed). */
enum class Bucket(val text: String) {
    UNDER_10("<10m"),
    M10_50("10-50m"),
    M50_100("50-100m"),
    M100_200("100-200m"),
    M200_500("200-500m"),
    M500_1K("0.5-1km"),
    K1_5("1-5km"),
    OVER_5K(">5km");

    companion object {
        fun fromText(text: String?): Bucket? = entries.firstOrNull { it.text == text }
    }
}

enum class OutcomeKind(val text: String) {
    SENT("sent"),
    NOT_MOVED("not-moved"),
    LISTENER_OFF("listener-off");

    companion object {
        fun fromText(text: String?): OutcomeKind? = entries.firstOrNull { it.text == text }
    }
}

/** One due subscriber's result in a tick. [who] is an alias, [needM] the subscription's own distance setting. */
data class Outcome(
    val who: String,
    val kind: OutcomeKind,
    val moved: Bucket? = null,
    val needM: Int? = null,
)

/** Permission and device state at a moment. [device] is manufacturer and model only. */
data class EnvInfo(
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val locationServices: Boolean,
    val batteryUnrestricted: Boolean,
    val device: String,
)

data class EventEntry(
    val t: Long,
    val type: EventType,
    val who: String? = null,
    val n: Int? = null,
    val due: Int? = null,
    val lateS: Int? = null,
    val reason: Reason? = null,
    val fixS: Int? = null,
    val acc: Bucket? = null,
    val parts: Int? = null,
    val err: String? = null,
    val freqMin: Int? = null,
    val distM: Int? = null,
    val hours: Int? = null,
    val outcomes: List<Outcome> = emptyList(),
    val env: EnvInfo? = null,
)
