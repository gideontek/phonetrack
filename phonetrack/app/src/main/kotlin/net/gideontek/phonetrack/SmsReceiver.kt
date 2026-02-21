package net.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat

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

        val serviceIntent = Intent(context, SmsLocationService::class.java)
            .putExtra("sender", sender)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
