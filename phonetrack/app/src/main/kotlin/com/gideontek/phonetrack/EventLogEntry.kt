package com.gideontek.phonetrack

/**
 * What happened. The per-subscriber results of a tick (sent, skipped) are [Outcome]s inside the
 * TICK entry rather than entries of their own. An entry has no message, coordinate or number
 * field; its only strings are an alias, an exception class name and the device model.
 * The constant names are stored in prefs: do not rename them.
 */
enum class EventType {
    TICK, SERVICE_START, SERVICE_STOP, BOOT_RESUME, SUBSCRIBED, REPLACED, EXPIRED,
    CANCELLED, SUBSCRIBE_REJECTED, FIX_FAIL, SEND_FAIL, ENV,
    LOW_BATTERY, NUMBER_PENDING, NUMBER_APPROVED, NUMBER_BLOCKED, MANUAL_SEND, SETTING_CHANGED,
    REQUEST_RECEIVED, REPLY_SENT, INBOUND_DROPPED, PIN_WRONG, PIN_LOCKED
}

/** A kind of message in the flow: what an approved sender asked for, or a reply worth its own line. Never the text. */
enum class MessageKind(val text: String) {
    ONE_SHOT("location"),
    LAST("last-known location"),
    LAST_NONE("no saved location"),
    HELP("help"),
    NO_SUBSCRIPTION("no subscription to cancel"),
    RATE_LIMITED("rate limit notice"),
    PERMISSION_ERROR("location permission error"),
    SERVICES_OFF("location services off error"),
    SUBSCRIBE("subscribe"),
    UNSUBSCRIBE("unsubscribe"),
    INVALID_SUBSCRIBE("invalid subscribe"),
    TIMEOUT("timeout error"),
    NO_PROVIDER("no location provider error");

    companion object {
        fun fromText(text: String?): MessageKind? = entries.firstOrNull { it.text == text }
    }
}

/**
 * Which setting changed. Only its name is logged, plus a number or on/off where that is plain
 * configuration; the keyword and the PIN never carry a value.
 */
enum class Setting(val text: String) {
    LISTENING("listening"),
    KEYWORD("keyword"),
    REPLY_CONTENTS("reply-contents"),
    MAP_LINK("map-link"),
    MOVEMENT("movement"),
    RATE_LIMIT("rate-limit"),
    MAX_SUBSCRIPTIONS("max-subscriptions"),
    LOW_BATTERY_ALERT("low-battery-alert"),
    PIN_SET("pin-set"),
    PIN_REMOVED("pin-removed"),
    RESTORE_DEFAULTS("restore-defaults");

    companion object {
        fun fromText(text: String?): Setting? = entries.firstOrNull { it.text == text }
    }
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
    INVALID_FLAGS("invalid-flags"),
    FRESH_FIX("fresh-fix"),
    CACHED_FIX("cached-fix"),
    NO_FIX("no-fix"),
    IN_APP("in-app"),
    NOTIFICATION("notification"),
    SEND_NOW("send-now"),
    APPROVE_SEND("approve-and-send"),
    NEW_NUMBER_LIMIT("new-number-limit"),
    LISTENING_OFF("listening-off"),
    BLOCKED("blocked"),
    AWAITING_APPROVAL("awaiting-approval"),
    RATE_LIMITED("rate-limited");

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
    val msg: MessageKind? = null,
    val setting: Setting? = null,
    /** A setting's new number, or the battery percent of a low-battery alert. */
    val value: Int? = null,
    /** A setting's new on/off state. */
    val on: Boolean? = null,
    val outcomes: List<Outcome> = emptyList(),
    val env: EnvInfo? = null,
)
