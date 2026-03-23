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
 * message body matches the configured keyword, it dispatches based on the second token:
 *
 * - "subscribe"   → create/replace a subscription, schedule the periodic worker, send immediate fix
 * - "unsubscribe" → cancel the sender's subscription
 * - anything else → one-shot location reply (existing behaviour)
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("sms_enabled", false)
        val keyword = prefs.getString("sms_keyword", "phonetrack")?.lowercase() ?: return
        if (!enabled || keyword.isBlank()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody }

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

        when (tokens.getOrNull(1)?.lowercase()) {
            "subscribe" -> handleSubscribe(context, sender, keyword, tokens)
            "unsubscribe" -> handleUnsubscribe(context, sender)
            else -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SmsLocationService::class.java).putExtra("sender", sender)
                )
            }
        }
    }

    private fun handleSubscribe(
        ctx: Context,
        sender: String,
        keyword: String,
        tokens: List<String>
    ) {
        val params = SmsCommandParser.parseSubscribe(tokens.drop(2))
        if (params == null) {
            SmsSender.sendUsageHint(ctx, sender, keyword)
            return
        }
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
        // Immediate location fix (same as one-shot)
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, SmsLocationService::class.java).putExtra("sender", sender)
        )
    }

    private fun handleUnsubscribe(ctx: Context, sender: String) {
        SmsSender.sendSubscriptionCancelled(ctx, sender)
        SubscriptionManager.remove(ctx, sender)
    }
}
