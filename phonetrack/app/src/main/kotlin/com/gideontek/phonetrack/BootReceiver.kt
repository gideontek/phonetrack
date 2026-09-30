package com.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives BOOT_COMPLETED. Prunes expired subscriptions silently and restarts the
 * periodic [SubscriptionService] if any active subscriptions remain.
 *
 * Deliberately does not touch sms_enabled: SmsReceiver is manifest-registered and
 * sms_enabled is persisted, so the listener comes back in whatever state it was in
 * before the reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        SubscriptionManager.pruneExpired(context) // silent — no SMS on boot
        if (SubscriptionManager.hasActive(context)) {
            SubscriptionManager.ensureServiceRunning(context)
        }
    }
}
