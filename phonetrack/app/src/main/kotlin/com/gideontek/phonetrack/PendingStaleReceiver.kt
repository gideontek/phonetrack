package com.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarm target set when the pending notification is posted fresh: re-post it with stale buttons. */
class PendingStaleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PendingAlerts.refresh(context)
    }
}
