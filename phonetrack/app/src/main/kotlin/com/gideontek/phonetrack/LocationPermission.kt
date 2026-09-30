package com.gideontek.phonetrack

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

/**
 * Permission checks for starting the location foreground services from a background
 * broadcast (an incoming SMS or BOOT_COMPLETED).
 *
 * Both services declare foregroundServiceType="location". With only the foreground-only
 * ("while in use") location permission the app is not in an eligible state to start them from
 * the background, and on Android 14+ / targetSdk 35 the system throws a SecurityException from
 * startForeground() that kills the process. ACCESS_BACKGROUND_LOCATION ("Allow all the time")
 * makes the start legal.
 */
object LocationPermission {

    fun hasFine(ctx: Context): Boolean = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * True if a location foreground service can safely be started from the background:
     * precise location, plus background location where that permission exists (API 29+).
     */
    fun canStartLocationService(ctx: Context): Boolean =
        hasFine(ctx) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    private fun granted(ctx: Context, permission: String) =
        ActivityCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
}
