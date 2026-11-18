package com.gideontek.phonetrack

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat

/**
 * The owner's "new number is waiting for approval" notification: one notification (fixed id) that
 * names the number, or counts them, and for a single fresh request offers Block / Approve & send
 * (stale: Block / Approve). Nothing is persisted: the time of the latest attempt rides on the
 * notification's own extras, so it is read back from the active notification.
 */
object PendingAlerts {
    const val NOTIFICATION_ID = 46
    const val EXTRA_NUMBER = "number"
    const val EXTRA_REQUESTED_AT = "requestedAt"
    const val ACTION_BLOCK = "com.gideontek.phonetrack.PENDING_BLOCK"
    const val ACTION_APPROVE = "com.gideontek.phonetrack.PENDING_APPROVE"
    const val ACTION_APPROVE_SEND = "com.gideontek.phonetrack.PENDING_APPROVE_SEND"
    const val ACTION_STALE = "com.gideontek.phonetrack.PENDING_STALE"
    private const val EXTRA_POSTED_AT = "postedAt"
    private const val CHANNEL_ID = "pending_requests"

    /**
     * A contact from an unknown or pending number. A new number alerts. A repeat re-posts quietly
     * with a fresh request time, only while the notification is still showing and at most once per
     * [SmsLimits.PENDING_REPOST_INTERVAL_MS]; a dismissed or expired one is left alone.
     * [requestedAt] is when the request was made; it differs from [now] only in tests.
     */
    fun onContact(ctx: Context, isNew: Boolean, now: Long = System.currentTimeMillis(), requestedAt: Long = now) {
        if (!NotificationSupport.canPost(ctx)) return
        if (!isNew) {
            val active = activeExtras(ctx) ?: return
            if (!PendingNotificationLogic.shouldRepost(active.getLong(EXTRA_POSTED_AT), now)) return
        }
        post(ctx, pendingEntries(ctx), requestedAt = requestedAt, now = now, alert = isNew)
    }

    /**
     * Brings the notification in line with the approvals list: cancels it when nothing is pending,
     * otherwise re-posts it quietly (new count, buttons for the current age). Does nothing if the
     * notification is not showing. Call after a decision, on app resume, and from the stale alarm.
     */
    fun refresh(ctx: Context, now: Long = System.currentTimeMillis()) {
        val pending = pendingEntries(ctx)
        if (pending.isEmpty()) {
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            cancelStaleAlarm(ctx)
            return
        }
        val active = activeExtras(ctx) ?: return
        val only = pending.singleOrNull()
        // If the notification described other numbers, its time doesn't belong to the one left;
        // that entry's lastSeen can only be older than its real latest attempt, so buttons err stale.
        val requestedAt = if (only != null && active.getString(EXTRA_NUMBER) != only.number) {
            only.lastSeen
        } else {
            active.getLong(EXTRA_REQUESTED_AT)
        }
        post(ctx, pending, requestedAt, now, alert = false)
    }

    private fun pendingEntries(ctx: Context): List<ApprovalEntry> =
        ApprovalStore.getAll(ctx).filter { it.state == ApprovalState.PENDING }

    private fun activeExtras(ctx: Context): Bundle? =
        ctx.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.id == NOTIFICATION_ID }?.notification?.extras

    private fun post(ctx: Context, pending: List<ApprovalEntry>, requestedAt: Long, now: Long, alert: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (pending.isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            cancelStaleAlarm(ctx)
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_pending_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = ctx.getString(R.string.channel_pending_desc) }
        )

        val single = pending.singleOrNull()
        val title = ctx.getString(if (single != null) R.string.notif_pending_title_one else R.string.notif_pending_title_many)
        val text = if (single != null) {
            ctx.getString(R.string.notif_pending_text_one, single.number)
        } else {
            ctx.getString(R.string.notif_pending_text_many, pending.size)
        }
        // On a secure lock screen only this version shows: no number, no buttons.
        val publicVersion = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_stat_phonetrack)
            .build()

        val extras = Bundle().apply {
            putLong(EXTRA_REQUESTED_AT, requestedAt)
            putLong(EXTRA_POSTED_AT, now)
            putString(EXTRA_NUMBER, single?.number.orEmpty())
        }
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_phonetrack)
            .setContentIntent(NotificationSupport.openApp(ctx))
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setTimeoutAfter(SmsLimits.PENDING_NOTIFICATION_TIMEOUT_MS)
            .addExtras(extras)

        val buttons = PendingNotificationLogic.buttons(pending.size, PinStore.isSet(ctx), requestedAt, now)
        for (button in buttons) {
            val (action, label) = when (button) {
                PendingButton.BLOCK -> ACTION_BLOCK to R.string.notif_pending_action_block
                PendingButton.APPROVE -> ACTION_APPROVE to R.string.notif_pending_action_approve
                PendingButton.APPROVE_SEND -> ACTION_APPROVE_SEND to R.string.notif_pending_action_approve_send
            }
            builder.addAction(0, ctx.getString(label), actionIntent(ctx, action, single!!.number, requestedAt))
        }
        nm.notify(NOTIFICATION_ID, builder.build())

        // The buttons can't change themselves while the process isn't running: one inexact alarm
        // re-posts the stale version just after the window closes.
        if (PendingButton.APPROVE_SEND in buttons) scheduleStaleAlarm(ctx, requestedAt) else cancelStaleAlarm(ctx)
    }

    private fun actionIntent(ctx: Context, action: String, number: String, requestedAt: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, PendingActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_REQUESTED_AT, requestedAt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun staleIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, PendingStaleReceiver::class.java).setAction(ACTION_STALE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun scheduleStaleAlarm(ctx: Context, requestedAt: Long) {
        ctx.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC, requestedAt + SmsLimits.PENDING_ACTION_WINDOW_MS + 1_000L, staleIntent(ctx))
    }

    private fun cancelStaleAlarm(ctx: Context) {
        val intent = staleIntent(ctx)
        ctx.getSystemService(AlarmManager::class.java).cancel(intent)
        intent.cancel() // also drops the PendingIntent, so "does it exist" means "is it scheduled"
    }
}
