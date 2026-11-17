package com.gideontek.phonetrack

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

/** Small helpers shared by every notification the app posts. */
object NotificationSupport {

    /**
     * False on Android 13+ when POST_NOTIFICATIONS isn't granted. Foreground-service notices
     * don't need this (the service runs either way); owner alerts do.
     */
    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Opens the app. Used as the tap action of every notification: its permission flow handles
     * a grant, and resuming it starts any stored subscriptions.
     */
    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
