package com.gideontek.phonetrack

import android.content.Context
import android.location.Location
import android.os.Build
import android.telephony.SmsManager

/**
 * Single entry point for all outgoing SMS messages. Every public method delegates
 * string composition to [SmsComposer] and routes each resulting string through the
 * single [sendRaw] helper that contains the API-31 SmsManager compat boilerplate.
 */
object SmsSender {

    fun sendOneShotLocation(ctx: Context, to: String, loc: Location, battery: Int) {
        SmsComposer.composeOneShotLocation(loc.latitude, loc.longitude, loc.accuracy.toInt(), battery)
            .forEach { sendRaw(ctx, to, it) }
    }

    fun sendSubscriptionLocation(
        ctx: Context,
        to: String,
        loc: Location,
        prevLat: Double,
        prevLon: Double
    ) {
        SmsComposer.composeSubscriptionLocation(loc.latitude, loc.longitude, loc.accuracy.toInt(), prevLat, prevLon)
            .forEach { sendRaw(ctx, to, it) }
    }

    fun sendSubscriptionExpired(ctx: Context, to: String) {
        SmsComposer.composeSubscriptionExpired().forEach { sendRaw(ctx, to, it) }
    }

    fun sendSubscriptionCancelled(ctx: Context, to: String) {
        SmsComposer.composeSubscriptionCancelled().forEach { sendRaw(ctx, to, it) }
    }

    fun sendUsageHint(ctx: Context, to: String, keyword: String) {
        SmsComposer.composeUsageHint(keyword).forEach { sendRaw(ctx, to, it) }
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
        smsManager.sendTextMessage(to, null, text, null, null)
    }
}
