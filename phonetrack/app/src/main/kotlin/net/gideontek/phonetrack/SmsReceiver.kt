package net.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
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
        val blockAll = prefs.getBoolean("block_all", false)
        val approvalsJson = prefs.getString("approvals_list", "[]") ?: "[]"
        val approvalsArray = try { JSONArray(approvalsJson) } catch (_: Exception) { JSONArray() }

        var senderIndex = -1
        var senderState = "DEFAULT"
        for (i in 0 until approvalsArray.length()) {
            val obj = approvalsArray.optJSONObject(i) ?: continue
            if (obj.optString("number") == sender) {
                senderIndex = i
                senderState = obj.optString("state", "DEFAULT")
                break
            }
        }

        if (senderIndex == -1) {
            approvalsArray.put(JSONObject().put("number", sender).put("state", "DEFAULT"))
            prefs.edit().putString("approvals_list", approvalsArray.toString()).apply()
        }

        val isApproved = when (senderState) {
            "APPROVED" -> true
            "BLOCKED" -> false
            else -> !blockAll
        }
        if (!isApproved) return
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
        val sub = parseSubscribeParams(ctx, sender, keyword, tokens) ?: return
        SubscriptionManager.add(ctx, sub)
        SubscriptionManager.ensureServiceRunning(ctx)
        // Immediate location fix (same as one-shot)
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, SmsLocationService::class.java).putExtra("sender", sender)
        )
    }

    private fun handleUnsubscribe(ctx: Context, sender: String) {
        sendSms(ctx, sender, "[PhoneTrack] Your location subscription has been cancelled.")
        SubscriptionManager.remove(ctx, sender)
    }

    /**
     * Parses optional `--dist N --freq N --time N` tokens (any order).
     * Returns null and sends an error SMS if parsing fails.
     */
    private fun parseSubscribeParams(
        ctx: Context,
        sender: String,
        keyword: String,
        tokens: List<String>
    ): Subscription? {
        var dist = 100
        var freq = 15
        var time = 240

        var i = 2
        while (i < tokens.size) {
            when (tokens[i]) {
                "--dist" -> {
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull()
                    if (v == null) { sendUsageError(ctx, sender, keyword); return null }
                    dist = v; i += 2
                }
                "--freq" -> {
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull()
                    if (v == null) { sendUsageError(ctx, sender, keyword); return null }
                    if (v < 1) {
                        sendSms(ctx, sender, "[PhoneTrack] Minimum frequency is 1 minute")
                        return null
                    }
                    freq = v; i += 2
                }
                "--time" -> {
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull()
                    if (v == null) { sendUsageError(ctx, sender, keyword); return null }
                    time = v; i += 2
                }
                else -> { sendUsageError(ctx, sender, keyword); return null }
            }
        }

        val now = System.currentTimeMillis()
        return Subscription(
            number = sender,
            distMeters = dist,
            freqMinutes = freq,
            durationMinutes = time,
            subscribedAt = now,
            expiresAt = now + time * 60_000L,
            lastLat = 0.0,
            lastLon = 0.0,
            lastSentAt = now
        )
    }

    private fun sendUsageError(ctx: Context, sender: String, keyword: String) {
        sendSms(
            ctx, sender,
            "[PhoneTrack] Usage: $keyword subscribe [--dist N] [--freq N] [--time N]"
        )
    }

    private fun sendSms(ctx: Context, to: String, text: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(to, null, text, null, null)
    }
}
