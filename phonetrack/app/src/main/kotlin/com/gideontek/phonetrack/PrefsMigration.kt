package com.gideontek.phonetrack

import android.content.Context

/**
 * One-time upgrade of stored data, guarded by the `prefs_schema_version` pref.
 *
 * Version 1: phone numbers in `approvals_list` and `subscriptions_list` are normalized and
 * exact duplicates merged; approval entries gain `firstSeen` / `lastSeen` (see [NumberMigration]).
 *
 * Version 2: a legacy plaintext settings PIN is hashed and the plaintext deleted (see [PinStore]).
 *
 * Cheap after the first run (one int read), so it is safe to call from every entry point
 * (receiver, boot receiver, UI) before touching the lists.
 */
object PrefsMigration {
    private const val VERSION_KEY = "prefs_schema_version"
    private const val CURRENT_VERSION = 2
    private val lock = Any()

    fun run(ctx: Context) {
        val prefs = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        if (prefs.getInt(VERSION_KEY, 0) >= CURRENT_VERSION) return
        synchronized(lock) {
            val from = prefs.getInt(VERSION_KEY, 0)
            if (from >= CURRENT_VERSION) return
            if (from < 1) {
                val now = System.currentTimeMillis()
                ApprovalStore.update(ctx) { NumberMigration.mergeApprovals(it, now) }
                SubscriptionManager.update(ctx) { NumberMigration.mergeSubscriptions(it) }
            }
            if (from < 2) PinStore.migrateLegacy(ctx)
            prefs.edit().putInt(VERSION_KEY, CURRENT_VERSION).apply()
        }
    }
}
