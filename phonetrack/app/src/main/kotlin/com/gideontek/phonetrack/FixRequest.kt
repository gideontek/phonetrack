package com.gideontek.phonetrack

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper

/**
 * One request for a fresh fix: asks every enabled provider (see [FixPolicy]) and hands the first
 * good fix to the caller exactly once. The caller keeps its own timeout and must call [cancel] when
 * it gives up or its service is destroyed. Single use: make a new one for each fetch.
 */
class FixRequest(private val lm: LocationManager, private val handler: Handler) {
    private val listeners = mutableListOf<LocationListener>()
    private var held: Location? = null
    private var grace: Runnable? = null
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
        for (source in sources) {
            val listener = LocationListener { loc -> onLocation(source, loc, onFix) }
            listeners += listener
            val provider = if (source == FixSource.GPS) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
        return true
    }

    /** Stops listening and drops any fix still waiting. Safe to call more than once. */
    fun cancel() {
        done = true
        listeners.forEach { lm.removeUpdates(it) }
        listeners.clear()
        grace?.let { handler.removeCallbacks(it) }
        grace = null
        held = null
    }

    private fun onLocation(source: FixSource, loc: Location, onFix: (Location) -> Unit) {
        if (done) return
        val best = held?.takeIf { !FixPolicy.isBetter(loc.accuracy, it.accuracy) } ?: loc
        if (FixPolicy.sendNow(source, loc.accuracy)) {
            finish(best, onFix)
        } else {
            held = best
            if (grace == null) {
                grace = Runnable { held?.let { finish(it, onFix) } }.also { handler.postDelayed(it, FixPolicy.GRACE_MS) }
            }
        }
    }

    private fun finish(loc: Location, onFix: (Location) -> Unit) {
        cancel()
        onFix(loc)
    }
}
