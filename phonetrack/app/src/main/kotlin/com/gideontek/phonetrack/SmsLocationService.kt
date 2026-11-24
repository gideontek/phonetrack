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
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

/**
 * Foreground service that acquires a single location fix and sends it back to [sender]
 * via SMS. Times out after [TIMEOUT_MS] milliseconds with an error reply.
 *
 * If location services are disabled when the request arrives, the service posts a
 * high-priority notification to the user (with a countdown chrono) and waits up to
 * [TIMEOUT_MS] for the user to re-enable location services. If services come back on
 * within that window the location is acquired and sent immediately; otherwise the
 * standard timeout-failure SMS is sent.
 */
class SmsLocationService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val ALERT_NOTIFICATION_ID = 43
        private const val CHANNEL_ID = "sms_location"
        private const val ALERT_CHANNEL_ID = "sms_location_alert"
        private const val TIMEOUT_MS = 60_000L
    }

    private var locationManager: LocationManager? = null
    private var fixRequest: FixRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    // Held while waiting for the user to enable location services
    private var providerChangeReceiver: BroadcastReceiver? = null
    private var waitTimeoutRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        // Callers (SmsReceiver) must verify LocationPermission.canStartLocationService()
        // *before* calling startForegroundService() for this class. This service declares
        // foregroundServiceType="location", and startForeground() must be called immediately
        // once started this way (skipping it, or calling it without the permissions held, both
        // crash the process on Android 14+ / targetSdk 35 — one via SecurityException, the
        // other via ForegroundServiceDidNotStartInTimeException) — so the check can't safely
        // live here; it has to gate the startForegroundService() call itself.
        //
        // Backstop for anything that check misses (permission revoked between the check and
        // here, other ineligible-state rules): a SecurityException from startForeground()
        // would otherwise kill the process, and with it every later incoming command.
        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
            HostAlerts.clearLocationPermissionNeeded(this)
        } catch (e: SecurityException) {
            Log.w("SmsLocationService", "Cannot start as a location foreground service", e)
            SmsSender.sendPermissionError(this, sender)
            HostAlerts.locationPermissionNeeded(this, sender)
            stopSelf()
            return START_NOT_STICKY
        }
        fetchAndSend(sender)
        return START_NOT_STICKY
    }

    private fun fetchAndSend(sender: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Defense in depth only — callers are expected to have already checked this
            // before starting the service (see the note in onStartCommand above).
            SmsSender.sendPermissionError(this, sender)
            HostAlerts.locationPermissionNeeded(this, sender)
            stopSelf()
            return
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providersAvailable =
            locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!providersAvailable) {
            waitForLocationServices(sender)
            return
        }

        startLocationRequest(sender)
    }

    /**
     * Location services are currently off. Post a prominent notification so the user
     * knows they have [TIMEOUT_MS] to re-enable them before the request fails.
     */
    private fun waitForLocationServices(sender: String) {
        postAlertNotification(sender)

        val timeoutRunnable = Runnable {
            waitTimeoutRunnable = null
            unregisterProviderReceiver()
            dismissAlertNotification()
            SmsSender.sendServicesDisabledError(this, sender)
            stopSelf()
        }
        waitTimeoutRunnable = timeoutRunnable

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lm.isLocationEnabled
                } else {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }
                if (enabled) {
                    handler.removeCallbacks(timeoutRunnable)
                    waitTimeoutRunnable = null
                    unregisterProviderReceiver()
                    dismissAlertNotification()
                    startLocationRequest(sender)
                }
            }
        }
        providerChangeReceiver = receiver
        registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    // ACCESS_FINE_LOCATION is verified in fetchAndSend() before this can be reached
    @SuppressLint("MissingPermission")
    private fun startLocationRequest(sender: String) {
        val lm = locationManager
            ?: (getSystemService(Context.LOCATION_SERVICE) as LocationManager).also { locationManager = it }
        val request = FixRequest(lm, handler).also { fixRequest = it }

        val timeoutRunnable = Runnable {
            // A network fix that was waiting for GPS is better than an error.
            if (request.deliverHeld()) return@Runnable
            request.cancel()
            SmsSender.sendTimeoutError(this, sender)
            stopSelf()
        }
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        val started = request.start { location ->
            handler.removeCallbacks(timeoutRunnable)
            SmsSender.sendOneShotLocation(this, sender, location, DeviceStatus.battery(this))
            // Seed subscription tracking so the first periodic update has a meaningful delta.
            if (SubscriptionManager.getFor(this, sender) != null) {
                SubscriptionManager.updateTracking(this, sender, location.latitude, location.longitude, System.currentTimeMillis())
            }
            stopSelf()
        }
        if (!started) {
            handler.removeCallbacks(timeoutRunnable)
            SmsSender.sendNoProviderError(this, sender)
            stopSelf()
        }
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_reply_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alert_desc)
            }
        )
    }

    private fun buildForegroundNotification(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_fetching_title))
            .setSmallIcon(R.drawable.ic_stat_phonetrack)
            .setContentIntent(NotificationSupport.openApp(this))
            .build()
    }

    private fun postAlertNotification(sender: String) {
        // POST_NOTIFICATIONS runtime permission is required on Android 13+
        if (!NotificationSupport.canPost(this)) return

        ensureChannels()
        // On a secure lock screen only this version shows, without the requester's number.
        val publicVersion = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_alert_public_title))
            .setSmallIcon(R.drawable.ic_stat_phonetrack)
            .build()
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_alert_title, sender))
            .setContentText(getString(R.string.notif_alert_text))
            .setSmallIcon(R.drawable.ic_stat_phonetrack)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            // Countdown chrono: setWhen to the deadline, then enable countdown mode
            .setWhen(System.currentTimeMillis() + TIMEOUT_MS)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun dismissAlertNotification() {
        getSystemService(NotificationManager::class.java).cancel(ALERT_NOTIFICATION_ID)
    }

    private fun unregisterProviderReceiver() {
        providerChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
            providerChangeReceiver = null
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterProviderReceiver()
        dismissAlertNotification()
        fixRequest?.cancel()
        super.onDestroy()
    }
}
