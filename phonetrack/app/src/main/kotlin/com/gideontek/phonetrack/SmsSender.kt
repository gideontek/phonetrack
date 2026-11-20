package com.gideontek.phonetrack

import android.content.Context
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * Single entry point for all outgoing SMS messages. Every public method delegates
 * string composition to [SmsComposer] and routes each resulting string through the
 * single [sendRaw] helper that contains the API-31 SmsManager compat boilerplate. [send] also
 * tells the event log about replies that nothing else records.
 */
object SmsSender {

    private fun fixOf(loc: Location, battery: DeviceStatus.Battery?) = LocationFix(
        lat = loc.latitude,
        lon = loc.longitude,
        accuracyM = loc.accuracy.toInt(),
        timeMs = loc.time,
        batteryPct = battery?.percent ?: -1,
        charging = battery?.charging ?: false
    )

    /** One-shot reply: the parts chosen in Reply contents (default: accuracy, battery and the map link). */
    fun sendOneShotLocation(ctx: Context, to: String, loc: Location, battery: DeviceStatus.Battery) {
        send(ctx, to, SmsComposer.composeLocation(fixOf(loc, battery), ReplyOptionsStore.read(ctx)), MessageKind.ONE_SHOT)
    }

    /** Subscription update: the same parts, plus the movement arrow and distance since the last one. */
    fun sendSubscriptionLocation(
        ctx: Context,
        to: String,
        loc: Location,
        prevLat: Double,
        prevLon: Double,
        battery: DeviceStatus.Battery
    ) {
        send(ctx, to, SmsComposer.composeLocation(fixOf(loc, battery), ReplyOptionsStore.read(ctx), prevLat, prevLon))
    }

    /** Low-battery alert: [loc] is the newest fix (null = none), [ageMs] non-null when it is a cached one. */
    fun sendLowBattery(ctx: Context, to: String, loc: Location?, percent: Int, ageMs: Long?) {
        send(ctx, to, SmsComposer.composeLowBattery(loc?.let { fixOf(it, null) }, ReplyOptionsStore.read(ctx), percent, ageMs))
    }

    fun sendSubscriptionExpired(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeSubscriptionExpired())
    }

    fun sendSubscriptionCancelled(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeSubscriptionCancelled())
    }

    fun sendNoSubscription(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeNoSubscription(), MessageKind.NO_SUBSCRIPTION)
    }

    fun sendHelp(ctx: Context, to: String, keyword: String) {
        send(ctx, to, SmsComposer.composeHelp(keyword), MessageKind.HELP)
    }

    fun sendInvalidSubscribe(ctx: Context, to: String, message: String, keyword: String) {
        send(ctx, to, SmsComposer.composeInvalidSubscribe(message, keyword))
    }

    fun sendSubscribeAck(ctx: Context, to: String, keyword: String, params: SubscribeParams, expiresAtMs: Long) {
        send(ctx, to, SmsComposer.composeSubscribeAck(keyword, params, expiresAtMs))
    }

    fun sendLastKnown(ctx: Context, to: String, loc: Location, nowMs: Long) {
        send(ctx, to, SmsComposer.composeLastKnown(fixOf(loc, DeviceStatus.battery(ctx)), ReplyOptionsStore.read(ctx), nowMs - loc.time), MessageKind.LAST)
    }

    fun sendNoCachedLocation(ctx: Context, to: String, keyword: String) {
        send(ctx, to, SmsComposer.composeNoCachedLocation(keyword), MessageKind.LAST_NONE)
    }

    fun sendRateLimited(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeRateLimited(), MessageKind.RATE_LIMITED)
    }

    fun sendSubscriptionLimit(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeSubscriptionLimit())
    }

    fun sendPermissionError(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composePermissionError(), MessageKind.PERMISSION_ERROR)
    }

    fun sendServicesDisabledError(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeServicesDisabledError(), MessageKind.SERVICES_OFF)
    }

    fun sendTimeoutError(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeTimeoutError(), MessageKind.TIMEOUT)
    }

    fun sendNoProviderError(ctx: Context, to: String) {
        send(ctx, to, SmsComposer.composeNoProviderError(), MessageKind.NO_PROVIDER)
    }

    /**
     * Sends every text of one message. [logAs] names a reply the event log should show; leave it out
     * for messages another event already describes (subscribe, expiry, subscription updates, low battery).
     */
    private fun send(ctx: Context, to: String, texts: List<String>, logAs: MessageKind? = null) {
        val allSent = texts.map { sendRaw(ctx, to, it) }.all { it }
        if (allSent && logAs != null) EventLog.replied(ctx, to, logAs)
    }

    /** True if the system accepted the text; false (and a SEND_FAIL line) if it threw. */
    private fun sendRaw(ctx: Context, to: String, text: String): Boolean {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        // A send failure (permission revoked, malformed number, etc.) must not crash the
        // receiver or service that called us, so log it and skip the last_send_at update.
        var partCount = 1
        try {
            val parts = smsManager.divideMessage(text)
            partCount = parts.size
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(to, null, text, null, null)
            }
        } catch (e: RuntimeException) {
            Log.w("SmsSender", "Failed to send SMS", e)
            EventLog.sendFailed(ctx, to, partCount, e)
            return false
        }
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
            .edit().putLong("last_send_at", System.currentTimeMillis()).apply()
        return true
    }
}
