package com.gideontek.phonetrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Notifications for the phone's owner about problems only they can fix. Anything the
 * requester can act on goes to them by SMS instead; owner-only instructions never do.
 */
object HostAlerts {
    private const val CHANNEL_ID = "host_alerts"
    private const val BACKGROUND_LOCATION_ID = 45

    /**
     * A location request (or a resumed subscription) couldn't be served because PhoneTrack
     * lacks a location permission it needs: precise location, or background location ("Allow
     * all the time") so its location services can start from an incoming SMS. [requester] is the number that asked, or null when
     * there is no single requester (the periodic service, boot). Uses a fixed notification
     * id, so repeated requests replace the notification instead of stacking. On a secure lock
     * screen only the public version (no number, no body) is shown.
     */
    fun locationPermissionNeeded(ctx: Context, requester: String?) {
        if (!NotificationSupport.canPost(ctx)) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_host_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = ctx.getString(R.string.channel_host_desc) }
        )

        val title = ctx.getString(R.string.notif_host_title)
        val text = if (requester == null) {
            ctx.getString(R.string.notif_host_text_updates)
        } else {
            ctx.getString(R.string.notif_host_text_requester, requester)
        }
        val publicVersion = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_stat_phonetrack)
            .build()
        // Open the app rather than the system settings page: its permission flow handles the
        // grant, and resuming it starts any stored subscriptions.
        nm.notify(
            BACKGROUND_LOCATION_ID,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_stat_phonetrack)
                .setContentIntent(NotificationSupport.openApp(ctx))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .build()
        )
    }

    /** Removes the alert; call when the permission it asks for is known to be granted. */
    fun clearLocationPermissionNeeded(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(BACKGROUND_LOCATION_ID)
    }

    /** Clears the alert if the location services can now start from the background. */
    fun clearIfResolved(ctx: Context) {
        if (LocationPermission.canStartLocationService(ctx)) clearLocationPermissionNeeded(ctx)
    }
}
