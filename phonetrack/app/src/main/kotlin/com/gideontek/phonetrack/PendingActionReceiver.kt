package com.gideontek.phonetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Button presses on the pending-request notification. Everything is re-checked at press time:
 * the number must still be PENDING and no settings PIN may have been set since the buttons
 * were posted. "Approve & send" only answers a request that is still fresh.
 */
class PendingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(PendingAlerts.EXTRA_NUMBER) ?: return
        val requestedAt = intent.getLongExtra(PendingAlerts.EXTRA_REQUESTED_AT, 0L)
        val now = System.currentTimeMillis()

        val stillPending = ApprovalStore.stateFor(context, number) == ApprovalState.PENDING
        if (stillPending && !PinStore.isSet(context)) {
            when (intent.action) {
                PendingAlerts.ACTION_BLOCK ->
                    ApprovalStore.setState(context, number, ApprovalState.BLOCKED)
                PendingAlerts.ACTION_APPROVE ->
                    ApprovalStore.setState(context, number, ApprovalState.APPROVED)
                PendingAlerts.ACTION_APPROVE_SEND -> {
                    ApprovalStore.setState(context, number, ApprovalState.APPROVED)
                    if (PendingNotificationLogic.isFresh(requestedAt, now)) {
                        SmsReceiver.startLocationFetch(context, number)
                    } else {
                        Toast.makeText(context, R.string.notif_pending_too_old, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        PendingAlerts.refresh(context)
    }
}
