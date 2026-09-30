package com.gideontek.phonetrack

import android.content.Context
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * Single entry point for all outgoing SMS messages. Every public method delegates
 * string composition to [SmsComposer] and routes each resulting string through the
 * single [sendRaw] helper that contains the API-31 SmsManager compat boilerplate.
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

    /** One-shot reply: the parts chosen in Reply contents (default: just the map link). */
    fun sendOneShotLocation(ctx: Context, to: String, loc: Location, battery: DeviceStatus.Battery) {
        SmsComposer.composeLocation(fixOf(loc, battery), ReplyOptionsStore.read(ctx))
            .forEach { sendRaw(ctx, to, it) }
    }

    /** Periodic update: the same parts, plus the movement arrow and distance since the last one. */
    fun sendSubscriptionLocation(
        ctx: Context,
        to: String,
        loc: Location,
        prevLat: Double,
        prevLon: Double,
        battery: DeviceStatus.Battery
    ) {
        SmsComposer.composeLocation(fixOf(loc, battery), ReplyOptionsStore.read(ctx), prevLat, prevLon)
            .forEach { sendRaw(ctx, to, it) }
    }

    fun sendSubscriptionExpired(ctx: Context, to: String) {
        SmsComposer.composeSubscriptionExpired().forEach { sendRaw(ctx, to, it) }
    }

    fun sendSubscriptionCancelled(ctx: Context, to: String) {
        SmsComposer.composeSubscriptionCancelled().forEach { sendRaw(ctx, to, it) }
    }

    fun sendNoSubscription(ctx: Context, to: String) {
        SmsComposer.composeNoSubscription().forEach { sendRaw(ctx, to, it) }
    }

    fun sendHelp(ctx: Context, to: String, keyword: String) {
        SmsComposer.composeHelp(keyword).forEach { sendRaw(ctx, to, it) }
    }

    fun sendInvalidSubscribe(ctx: Context, to: String, message: String, keyword: String) {
        SmsComposer.composeInvalidSubscribe(message, keyword).forEach { sendRaw(ctx, to, it) }
    }

    fun sendSubscribeAck(ctx: Context, to: String, keyword: String, params: SubscribeParams, expiresAtMs: Long) {
        SmsComposer.composeSubscribeAck(keyword, params, expiresAtMs).forEach { sendRaw(ctx, to, it) }
    }

    fun sendLastKnown(ctx: Context, to: String, loc: Location, nowMs: Long) {
        SmsComposer.composeLastKnown(fixOf(loc, null), ReplyOptionsStore.read(ctx), nowMs - loc.time)
            .forEach { sendRaw(ctx, to, it) }
    }

    fun sendNoCachedLocation(ctx: Context, to: String, keyword: String) {
        SmsComposer.composeNoCachedLocation(keyword).forEach { sendRaw(ctx, to, it) }
    }

    fun sendRateLimited(ctx: Context, to: String) {
        SmsComposer.composeRateLimited().forEach { sendRaw(ctx, to, it) }
    }

    fun sendSubscriptionLimit(ctx: Context, to: String) {
        SmsComposer.composeSubscriptionLimit().forEach { sendRaw(ctx, to, it) }
    }

    fun sendPermissionError(ctx: Context, to: String) {
        SmsComposer.composePermissionError().forEach { sendRaw(ctx, to, it) }
    }

    fun sendServicesDisabledError(ctx: Context, to: String) {
        SmsComposer.composeServicesDisabledError().forEach { sendRaw(ctx, to, it) }
    }

    fun sendTimeoutError(ctx: Context, to: String) {
        SmsComposer.composeTimeoutError().forEach { sendRaw(ctx, to, it) }
    }

    fun sendNoProviderError(ctx: Context, to: String) {
        SmsComposer.composeNoProviderError().forEach { sendRaw(ctx, to, it) }
    }

    private fun sendRaw(ctx: Context, to: String, text: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        // A send failure (permission revoked, malformed number, etc.) must not crash the
        // receiver or service that called us, so log it and skip the last_send_at update.
        try {
            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(to, null, text, null, null)
            }
        } catch (e: RuntimeException) {
            Log.w("SmsSender", "Failed to send SMS", e)
            return
        }
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
            .edit().putLong("last_send_at", System.currentTimeMillis()).apply()
    }
}
