package net.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives BOOT_COMPLETED. If the user has enabled "start on boot", sets
 * sms_enabled = true so that SmsReceiver begins responding to keyword SMS
 * without requiring the user to open the app first.
 *
 * Also prunes expired subscriptions silently and re-schedules the periodic
 * worker if any active subscriptions remain.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("auto_start_on_boot", false)) {
            prefs.edit().putBoolean("sms_enabled", true).apply()
        }

        SubscriptionManager.pruneExpired(context) // silent — no SMS on boot
        if (SubscriptionManager.hasActive(context)) {
            SubscriptionManager.ensureServiceRunning(context)
        }
    }
}
