package com.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Listens for incoming SMS messages. If the app is enabled and the first word of the
 * message body matches the configured keyword, an approved sender's command is parsed by
 * [SmsCommandParser] and dispatched:
 *
 * - (nothing)     → one-shot location reply
 * - "subscribe"   → create/replace a subscription, confirm it, send an immediate fix
 * - "unsubscribe" → cancel the sender's subscription
 * - "last"        → reply with the cached last-known fix (no GPS wake-up)
 * - anything else → help reply
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("sms_enabled", false)
        val keyword = prefs.getString("sms_keyword", "phonetrack")?.lowercase() ?: return
        if (!enabled || keyword.isBlank()) return
        // Our own replies start with "[PhoneTrack]"; a keyword that could match them would
        // let two phones answer each other forever.
        if (keyword.startsWith("[")) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody }
        if (body.length > SmsLimits.MAX_BODY) return

        val tokens = body.trim().split("\\s+".toRegex())
        val firstWord = tokens.firstOrNull()?.lowercase()
        if (firstWord != keyword) return

        // --- Approvals gate ---
        val approvalsJson = prefs.getString("approvals_list", "[]") ?: "[]"
        val approvalsArray = try { JSONArray(approvalsJson) } catch (_: Exception) { JSONArray() }

        var senderState = "NEW"
        for (i in 0 until approvalsArray.length()) {
            val obj = approvalsArray.optJSONObject(i) ?: continue
            if (obj.optString("number") == sender) {
                senderState = obj.optString("state", "PENDING")
                break
            }
        }

        if (senderState == "NEW") {
            // First contact — log as PENDING, do not reply
            approvalsArray.put(JSONObject().put("number", sender).put("state", "PENDING"))
            prefs.edit().putString("approvals_list", approvalsArray.toString()).apply()
            return
        }

        if (senderState != "APPROVED") return
        // --- End approvals gate ---

        prefs.edit().putLong("last_receive_at", System.currentTimeMillis()).apply()

        when (val command = SmsCommandParser.parse(tokens.drop(1))) {
            SmsCommand.OneShot -> startLocationFetch(context, sender)
            is SmsCommand.Subscribe -> handleSubscribe(context, sender, keyword, command.params)
            is SmsCommand.InvalidSubscribe ->
                SmsSender.sendInvalidSubscribe(context, sender, command.message, keyword)
            SmsCommand.Unsubscribe -> handleUnsubscribe(context, sender)
            SmsCommand.Last -> handleLast(context, sender, keyword)
            SmsCommand.Help -> SmsSender.sendHelp(context, sender, keyword)
        }
    }

    private fun hasLocationPermission(ctx: Context) = LocationPermission.hasFine(ctx)

    /**
     * Starts [SmsLocationService] for a one-shot location fetch, replying with a
     * permission-error SMS instead if ACCESS_FINE_LOCATION isn't granted or only
     * foreground-only location is (the latter also notifies the phone's owner).
     *
     * That service declares foregroundServiceType="location", and starting it from this
     * background broadcast without those permissions leads to a guaranteed crash inside the
     * service (a SecurityException from startForeground() itself on Android 14+, or a
     * ForegroundServiceDidNotStartInTimeException if it's skipped) — so this must be checked
     * here, before the service is ever started.
     */
    private fun startLocationFetch(ctx: Context, sender: String) {
        if (!hasLocationPermission(ctx)) {
            SmsSender.sendPermissionError(ctx, sender)
            return
        }
        if (!LocationPermission.canStartLocationService(ctx)) {
            // The requester can't fix this (it's a setting on this phone), so they only get the
            // generic error; the owner is told what to change via a notification.
            SmsSender.sendPermissionError(ctx, sender)
            HostAlerts.backgroundLocationNeeded(ctx, sender)
            return
        }
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, SmsLocationService::class.java).putExtra("sender", sender)
        )
    }

    private fun handleSubscribe(
        ctx: Context,
        sender: String,
        keyword: String,
        params: SubscribeParams
    ) {
        val now = System.currentTimeMillis()
        val sub = Subscription(
            number = sender,
            distMeters = params.dist,
            freqMinutes = params.freq,
            durationHours = params.hours,
            subscribedAt = now,
            expiresAt = now + params.hours * 3_600_000L,
            lastLat = 0.0,
            lastLon = 0.0,
            lastSentAt = now
        )
        SubscriptionManager.add(ctx, sub)
        SubscriptionManager.ensureServiceRunning(ctx)
        SmsSender.sendSubscribeAck(ctx, sender, keyword, params, sub.expiresAt)
        // Immediate location fix (same as one-shot)
        startLocationFetch(ctx, sender)
    }

    /** Replies from the cached fix; starts no service, so the GPS is never woken. */
    private fun handleLast(ctx: Context, sender: String, keyword: String) {
        if (!hasLocationPermission(ctx)) {
            SmsSender.sendPermissionError(ctx, sender)
            return
        }
        val loc = LastKnownLocation.get(ctx)
        if (loc == null) {
            SmsSender.sendNoCachedLocation(ctx, sender, keyword)
        } else {
            SmsSender.sendLastKnown(ctx, sender, loc, System.currentTimeMillis())
        }
    }

    private fun handleUnsubscribe(ctx: Context, sender: String) {
        if (SubscriptionManager.remove(ctx, sender)) {
            SmsSender.sendSubscriptionCancelled(ctx, sender)
        } else {
            SmsSender.sendNoSubscription(ctx, sender)
        }
    }
}
