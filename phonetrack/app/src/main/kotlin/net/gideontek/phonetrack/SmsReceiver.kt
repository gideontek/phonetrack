package net.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Listens for incoming SMS messages. If the app is enabled and the first word of the
 * message body matches the configured keyword, it starts [SmsLocationService] to fetch
 * the current location and reply to the sender.
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

        val firstWord = body.trim().split("\\s+".toRegex()).firstOrNull()?.lowercase()
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

        val serviceIntent = Intent(context, SmsLocationService::class.java)
            .putExtra("sender", sender)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
