package com.gideontek.phonetrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

/**
 * Long-running foreground service that sends periodic location updates to all active
 * subscribers. Uses a Handler tick loop to avoid WorkManager's Doze-mode deferrals.
 *
 * The service starts itself when the first subscription is created and stops itself
 * when the last subscription expires or is cancelled. [START_STICKY] ensures Android
 * restarts it after a process kill.
 */
class SubscriptionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 44
        private const val CHANNEL_ID = "subscription_service"
        private const val LOCATION_TIMEOUT_MS = 60_000L
        private const val RETRY_DELAY_MS = 5 * 60_000L  // retry after location failure
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationTimeoutRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // SubscriptionManager.ensureServiceRunning() — the sole caller that starts this
        // service — must verify LocationPermission.canStartLocationService() before calling
        // startForegroundService(). This service declares foregroundServiceType="location",
        // and startForeground() must be called immediately once started this way (skipping it,
        // or calling it without the permissions held, both crash the process on Android 14+ /
        // targetSdk 35 — one via SecurityException, the other via
        // ForegroundServiceDidNotStartInTimeException) — so the check can't safely live here;
        // it has to gate the startForegroundService() call itself. (Permission being revoked
        // *after* a successful start is a separate case, already handled by fetchLocation()'s
        // onFailure → retry path below.)
        //
        // Backstop for anything that check misses, including the system's own sticky restart
        // (a null intent, from the background): a SecurityException from startForeground()
        // would otherwise kill the process and put it in a restart loop.
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            Log.w("SubscriptionService", "Cannot start as a location foreground service", e)
            HostAlerts.backgroundLocationNeeded(this, null)
            stopSelf()
            return START_NOT_STICKY
        }
        // Cancel any pending tick and run immediately so a new/replaced subscription
        // gets its first update without waiting for the previous tick delay.
        tickRunnable?.let { handler.removeCallbacks(it) }
        scheduleTick(0)
        return START_STICKY
    }

    // -------------------------------------------------------------------------
    // Tick loop
    // -------------------------------------------------------------------------

    private fun scheduleTick(delayMs: Long) {
        val runnable = Runnable { tick() }
        tickRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun tick() {
        // 1. Prune expired subscriptions; notify each one.
        val expired = SubscriptionManager.pruneExpired(this)
        for (sub in expired) {
            SmsSender.sendSubscriptionExpired(this, sub.number)
        }

        // 2. Reload; stop if no subscribers remain.
        val subs = SubscriptionManager.getAll(this)
        if (subs.isEmpty()) {
            stopSelf()
            return
        }

        // Update the persistent notification with the current subscriber count.
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())

        // 3. Check which subscribers are due for an update.
        val now = System.currentTimeMillis()
        val dueSubs = SubscriptionLogic.dueSubs(subs, now)

        // While the SMS listener is switched off, send nothing: skip the fetch and idle at the
        // slowest tick. Expiry above still runs, and the service stays alive so re-enabling the
        // listener resumes updates without a restart.
        val smsEnabled = getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
            .getBoolean("sms_enabled", false)
        if (!SubscriptionLogic.shouldFetch(smsEnabled, dueSubs.size)) {
            scheduleTick(
                if (smsEnabled) SubscriptionLogic.nextTickDelay(subs, now)
                else SubscriptionLogic.MAX_TICK_MS
            )
            return
        }

        // 4. Fetch location; process due subscribers in the callback.
        fetchLocation(
            onLocation = { loc ->
                val sendTime = System.currentTimeMillis()
                for (sub in dueSubs) {
                    val dist = FloatArray(1)
                    if (sub.lastLat != 0.0 || sub.lastLon != 0.0) {
                        Location.distanceBetween(
                            sub.lastLat, sub.lastLon,
                            loc.latitude, loc.longitude, dist
                        )
                    }
                    val shouldSend = SubscriptionLogic.shouldSend(
                        sub.lastLat, sub.lastLon, dist[0], sub.distMeters
                    )
                    if (shouldSend) {
                        SmsSender.sendSubscriptionLocation(this, sub.number, loc, sub.lastLat, sub.lastLon)
                        SubscriptionManager.updateTracking(
                            this, sub.number, loc.latitude, loc.longitude, sendTime
                        )
                    }
                }
                // Reload subs (tracking was updated) and schedule next tick.
                val updated = SubscriptionManager.getAll(this)
                scheduleTick(SubscriptionLogic.nextTickDelay(updated, System.currentTimeMillis()))
            },
            onFailure = {
                // Location unavailable this cycle; retry sooner than the normal interval.
                scheduleTick(RETRY_DELAY_MS)
            }
        )
    }

    // -------------------------------------------------------------------------
    // Location fetch (Handler + LocationListener, same pattern as SmsLocationService)
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // requestSingleUpdate deprecated at API 30; acceptable for MVP
    private fun fetchLocation(onLocation: (Location) -> Unit, onFailure: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onFailure()
            return
        }

        val lm = (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .also { locationManager = it }

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> { onFailure(); return }
        }

        val timeoutRunnable = Runnable {
            locationTimeoutRunnable = null
            locationListener?.let { lm.removeUpdates(it) }
            locationListener = null
            onFailure()
        }
        locationTimeoutRunnable = timeoutRunnable
        handler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MS)

        locationListener = LocationListener { loc ->
            handler.removeCallbacks(timeoutRunnable)
            locationTimeoutRunnable = null
            // Remove the listener via the instance variable (same pattern as SmsLocationService).
            locationListener?.let { lm.removeUpdates(it) }
            locationListener = null
            onLocation(loc)
        }
        lm.requestSingleUpdate(provider, locationListener!!, Looper.getMainLooper())
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Location Subscriptions",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val count = SubscriptionManager.getAll(this).size
        val label = if (count == 1) "1 active subscription" else "$count active subscriptions"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneTrack — $label")
            .setContentText("Sending periodic location updates")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        locationListener?.let { locationManager?.removeUpdates(it) }
        super.onDestroy()
    }
}
