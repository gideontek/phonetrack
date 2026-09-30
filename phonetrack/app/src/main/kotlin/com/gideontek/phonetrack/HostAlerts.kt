package com.gideontek.phonetrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
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
     * only has foreground-only location. [requester] is the number that asked, or null when
     * there is no single requester (the periodic service, boot). Uses a fixed notification
     * id, so repeated requests replace the notification instead of stacking.
     */
    fun backgroundLocationNeeded(ctx: Context, requester: String?) {
        // POST_NOTIFICATIONS is a runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PhoneTrack alerts", NotificationManager.IMPORTANCE_HIGH)
        )

        val openSettings = PendingIntent.getActivity(
            ctx,
            0,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val what = if (requester == null) "Location updates" else "A location request from $requester"
        val text = "$what couldn't be answered. Tap to open settings, then set Location " +
            "permission to \"Allow all the time\"."
        nm.notify(
            BACKGROUND_LOCATION_ID,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle("PhoneTrack can't send location")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(openSettings)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }
}
