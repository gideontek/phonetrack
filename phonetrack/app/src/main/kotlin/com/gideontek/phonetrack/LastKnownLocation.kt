package com.gideontek.phonetrack

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

/** Reads the newest cached fix across providers. Never starts the GPS. */
object LastKnownLocation {

    /** Caller must have verified ACCESS_FINE_LOCATION. Returns null if nothing is cached. */
    @SuppressLint("MissingPermission")
    fun get(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
        }.maxByOrNull { it.time }
    }
}
