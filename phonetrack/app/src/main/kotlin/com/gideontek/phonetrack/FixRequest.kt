package com.gideontek.phonetrack

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper

/**
 * One request for a fresh fix: asks every enabled provider (see [FixPolicy]). A GPS fix is handed to
 * the caller at once; a network fix is held until GPS answers or the caller's deadline arrives and it
 * calls [deliverHeld]. The caller keeps its own timeout and must call [cancel] when it gives up or its
 * service is destroyed. Single use: make a new one for each fetch.
 */
class FixRequest(private val lm: LocationManager, private val handler: Handler) {
    private val listeners = mutableListOf<LocationListener>()
    private var onFix: ((Location) -> Unit)? = null
    private var gpsAsked = false
    private var held: Location? = null
    private var done = false

    /** Starts asking; false if no provider is enabled. The caller must hold ACCESS_FINE_LOCATION. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // requestSingleUpdate deprecated at API 30; acceptable for MVP
    fun start(onFix: (Location) -> Unit): Boolean {
        val sources = FixPolicy.sources(
            gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER),
            networkOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        )
        if (sources.isEmpty()) return false
        this.onFix = onFix
        gpsAsked = FixSource.GPS in sources
        for (source in sources) {
            val listener = LocationListener { loc -> onLocation(source, loc) }
            listeners += listener
            val provider = if (source == FixSource.GPS) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
        return true
    }

    /**
     * For the caller's deadline: if a network fix is still waiting for GPS, deliver it now and return
     * true. False means there is nothing to deliver (no fix yet, or already delivered or cancelled).
     */
    fun deliverHeld(): Boolean {
        if (done) return false
        val loc = held ?: return false
        finish(loc)
        return true
    }

    /** Stops listening and drops any fix still waiting. Safe to call more than once. */
    fun cancel() {
        done = true
        listeners.forEach { lm.removeUpdates(it) }
        listeners.clear()
        held = null
    }

    private fun onLocation(source: FixSource, loc: Location) {
        if (done) return
        val waiting = held
        if (FixPolicy.sendNow(source, gpsAsked)) {
            // A held network fix only wins if it is strictly more accurate than the GPS fix.
            finish(if (waiting != null && FixPolicy.isBetter(waiting.accuracy, loc.accuracy)) waiting else loc)
        } else {
            held = if (waiting != null && !FixPolicy.isBetter(loc.accuracy, waiting.accuracy)) waiting else loc
        }
    }

    private fun finish(loc: Location) {
        val callback = onFix
        cancel()
        callback?.invoke(loc)
    }
}
