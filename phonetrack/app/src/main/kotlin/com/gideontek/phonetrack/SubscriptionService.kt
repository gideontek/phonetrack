package com.gideontek.phonetrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

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
        private const val FETCH_BUSY_RETRY_MS = 5_000L  // a tick that finds an alert's fetch still running
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationTimeoutRunnable: Runnable? = null
    private var startLogged = false
    private var stopReason: Reason? = null
    private var expectedTickAt = 0L
    private var currentReport: TickReport? = null

    // A location fetch is in flight (a tick's or a low-battery alert's); the fields above hold only one.
    private var fetching = false

    // Low-battery alert: the armed state between readings (see LowBatteryLogic), the receiver that
    // feeds it, and an alert that arrived while a tick's fetch was running (that fetch sends it).
    private var batteryArmed: Boolean? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var pendingAlertPercent: Int? = null

    override fun onCreate() {
        super.onCreate()
        // ACTION_BATTERY_CHANGED can't be declared in the manifest on Android 8+, so this running
        // service is what listens. The current state arrives as soon as the receiver is registered.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onBatteryChanged(intent)
        }
        batteryReceiver = receiver
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

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
            HostAlerts.clearLocationPermissionNeeded(this)
        } catch (e: SecurityException) {
            Log.w("SubscriptionService", "Cannot start as a location foreground service", e)
            HostAlerts.locationPermissionNeeded(this, null)
            stopReason = Reason.FOREGROUND_DENIED
            stopSelf()
            return START_NOT_STICKY
        }
        stopReason = null
        // onStartCommand runs on every startForegroundService call; log the service starting once.
        if (!startLogged) {
            startLogged = true
            EventLog.serviceStarted(this, if (intent == null) Reason.STICKY_RESTART else Reason.REQUESTED)
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
        expectedTickAt = System.currentTimeMillis() + delayMs
        val runnable = Runnable { tick() }
        tickRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    /** Logs the tick (one summary line, however it ended) and schedules the next one. */
    private fun endTick(report: TickReport, delayMs: Long) {
        EventLog.recordTick(this, report)
        currentReport = null
        scheduleTick(delayMs)
    }

    /**
     * A new tick can start while the previous fetch is still pending (onStartCommand re-runs the
     * tick on every start request). Drop that fetch, so its listener cannot remove the new one and
     * its timeout cannot log a false FIX_FAIL, and log the abandoned tick.
     */
    private fun cancelInFlightFetch() {
        locationTimeoutRunnable?.let { handler.removeCallbacks(it) }
        locationTimeoutRunnable = null
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
        currentReport?.let { EventLog.recordTick(this, it) }
        currentReport = null
    }

    private fun tick() {
        // A low-battery alert is fetching a fix: let it finish (it has a timeout) rather than start a second fetch.
        if (fetching) {
            scheduleTick(FETCH_BUSY_RETRY_MS)
            return
        }
        cancelInFlightFetch()
        EventLog.env(this)
        val report = EventLog.tickReport(this, expectedTickAt)
        currentReport = report

        // 1. Prune expired subscriptions; notify each one.
        val expired = SubscriptionManager.pruneExpired(this)
        for (sub in expired) {
            EventLog.expired(this, sub.number)
            SmsSender.sendSubscriptionExpired(this, sub.number)
        }

        // 2. Reload; stop if no subscribers remain.
        val subs = SubscriptionManager.getAll(this)
        report.subs = subs.size
        if (subs.isEmpty()) {
            stopReason = Reason.NO_SUBS
            EventLog.recordTick(this, report)
            stopSelf()
            return
        }

        // Update the persistent notification with the current subscriber count.
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())

        // 3. Check which subscribers are due for an update.
        val now = System.currentTimeMillis()
        val dueSubs = SubscriptionLogic.dueSubs(subs, now)
        report.due = dueSubs.size

        // While the SMS listener is switched off, send nothing: skip the fetch. Expiry above
        // still runs, and the service stays alive. Keep ticking at the normal pace rather than
        // idling on a long delay: a skipped tick only reads a pref, and nothing wakes the
        // service when the listener is switched back on, so a long idle tick would delay the
        // resumed updates by up to MAX_TICK_MS (15 min) even for a 1-minute subscription.
        val smsEnabled = getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
            .getBoolean("sms_enabled", false)
        if (!SubscriptionLogic.shouldFetch(smsEnabled, dueSubs.size)) {
            if (!smsEnabled) report.listenerOff(dueSubs.map { it.number })
            endTick(report, SubscriptionLogic.nextTickDelay(subs, now))
            return
        }

        // 4. Fetch location; process due subscribers in the callback.
        val fetchStartedAt = SystemClock.elapsedRealtime()
        fetchLocation(
            onLocation = { loc ->
                report.fix(((SystemClock.elapsedRealtime() - fetchStartedAt) / 1000).toInt(), loc.accuracy)
                val sendTime = System.currentTimeMillis()
                val battery = DeviceStatus.battery(this)
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
                        SmsSender.sendSubscriptionLocation(this, sub.number, loc, sub.lastLat, sub.lastLon, battery)
                        SubscriptionManager.updateTracking(
                            this, sub.number, loc.latitude, loc.longitude, sendTime
                        )
                        report.sent(sub.number)
                    } else {
                        report.notMoved(sub.number, dist[0], sub.distMeters)
                    }
                }
                pendingAlertPercent?.let { deliverLowBattery(it, loc) }
                // Reload subs (tracking was updated) and schedule next tick.
                val updated = SubscriptionManager.getAll(this)
                endTick(report, SubscriptionLogic.nextTickDelay(updated, System.currentTimeMillis()))
            },
            onFailure = { reason ->
                pendingAlertPercent?.let { deliverLowBattery(it, null) }
                // Location unavailable this cycle; retry sooner than the normal interval.
                EventLog.fixFailed(this, reason)
                endTick(report, RETRY_DELAY_MS)
            }
        )
    }

    // -------------------------------------------------------------------------
    // Location fetch (Handler + LocationListener, same pattern as SmsLocationService)
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // requestSingleUpdate deprecated at API 30; acceptable for MVP
    private fun fetchLocation(onLocation: (Location) -> Unit, onFailure: (Reason) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onFailure(Reason.NO_PERMISSION)
            return
        }

        val lm = (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .also { locationManager = it }

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> { onFailure(Reason.PROVIDER_OFF); return }
        }

        val timeoutRunnable = Runnable {
            locationTimeoutRunnable = null
            locationListener?.let { lm.removeUpdates(it) }
            locationListener = null
            fetching = false
            onFailure(Reason.TIMEOUT)
        }
        locationTimeoutRunnable = timeoutRunnable
        fetching = true
        handler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MS)

        locationListener = LocationListener { loc ->
            handler.removeCallbacks(timeoutRunnable)
            locationTimeoutRunnable = null
            fetching = false
            // Remove the listener via the instance variable (same pattern as SmsLocationService).
            locationListener?.let { lm.removeUpdates(it) }
            locationListener = null
            onLocation(loc)
        }
        lm.requestSingleUpdate(provider, locationListener!!, Looper.getMainLooper())
    }

    // -------------------------------------------------------------------------
    // Low-battery alert
    // -------------------------------------------------------------------------

    private fun onBatteryChanged(intent: Intent) {
        val battery = DeviceStatus.batteryFrom(intent)
        val threshold = SmsLimits.coerceLowBattery(
            getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
                .getInt(HomeViewModel.KEY_LOW_BATTERY, SmsLimits.DEFAULT_LOW_BATTERY_PCT)
        )
        val step = LowBatteryLogic.step(batteryArmed, battery.percent, battery.charging, threshold)
        batteryArmed = step.armed
        if (step.alert) startLowBatteryAlert(battery.percent)
    }

    /** The battery just crossed the threshold: get a fix (or use the cached one) and text the subscribers. */
    private fun startLowBatteryAlert(percent: Int) {
        if (!alertRecipients().any()) return
        if (fetching) {
            // A tick's fetch is running; its callback delivers this alert with that fix.
            pendingAlertPercent = percent
            return
        }
        fetchLocation(
            onLocation = { loc -> deliverLowBattery(percent, loc) },
            onFailure = { deliverLowBattery(percent, null) }
        )
    }

    /** Active subscribers, or none while the SMS listener is off (nothing is sent then, as in the tick loop). */
    private fun alertRecipients(): List<Subscription> {
        val smsEnabled = getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
            .getBoolean("sms_enabled", false)
        if (!smsEnabled) return emptyList()
        val now = System.currentTimeMillis()
        return SubscriptionManager.getAll(this).filter { it.expiresAt > now }
    }

    /**
     * Sends the alert to every active subscriber. [fresh] is the fix just taken; without one the
     * newest cached fix goes out with its age, and without that the message says there is no location.
     */
    private fun deliverLowBattery(percent: Int, fresh: Location?) {
        pendingAlertPercent = null
        val recipients = alertRecipients()
        if (recipients.isEmpty()) return
        val now = System.currentTimeMillis()
        val cached = if (fresh == null &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) LastKnownLocation.get(this) else null
        val loc = fresh ?: cached
        val ageMs = cached?.let { now - it.time }
        for (sub in recipients) {
            SmsSender.sendLowBattery(this, sub.number, loc, percent, ageMs)
            // The next regular update compares against this position, so it doesn't repeat the alert.
            if (fresh != null) {
                SubscriptionManager.updateTracking(this, sub.number, fresh.latitude, fresh.longitude, now)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_subscriptions_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val count = SubscriptionManager.getAll(this).size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(resources.getQuantityString(R.plurals.notif_subs_title, count, count))
            .setContentText(getString(R.string.notif_subs_text))
            .setSmallIcon(R.drawable.ic_stat_phonetrack)
            .setContentIntent(NotificationSupport.openApp(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        batteryReceiver?.let { unregisterReceiver(it) }
        batteryReceiver = null
        locationListener?.let { locationManager?.removeUpdates(it) }
        // Destroyed mid-fetch: the tick that was waiting for a fix is still logged (finish() is once-only).
        currentReport?.let { EventLog.recordTick(this, it) }
        EventLog.serviceStopped(this, stopReason)
        super.onDestroy()
    }
}
