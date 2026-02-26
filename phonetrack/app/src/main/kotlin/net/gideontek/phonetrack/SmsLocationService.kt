package net.gideontek.phonetrack

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
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
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
    private var locationListener: LocationListener? = null
    private val handler = Handler(Looper.getMainLooper())

    // Held while waiting for the user to enable location services
    private var providerChangeReceiver: BroadcastReceiver? = null
    private var waitTimeoutRunnable: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        fetchAndSend(sender)
        return START_NOT_STICKY
    }

    private fun fetchAndSend(sender: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sendSms(sender, "[PhoneTrack] Location permission not granted")
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
            sendSms(sender, "[PhoneTrack] Location unavailable (services disabled)")
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
        if (locationManager == null) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }

        val timeoutRunnable = Runnable {
            locationManager?.removeUpdates(locationListener ?: return@Runnable)
            sendSms(sender, "[PhoneTrack] Location unavailable (timeout)")
            stopSelf()
        }
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        locationListener = LocationListener { location ->
            handler.removeCallbacks(timeoutRunnable)
            locationManager?.removeUpdates(locationListener!!)
            sendLocationSms(sender, location)
            stopSelf()
        }

        val provider = when {
            locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                handler.removeCallbacks(timeoutRunnable)
                sendSms(sender, "[PhoneTrack] No location provider available")
                stopSelf()
                return
            }
        }

        @Suppress("DEPRECATION") // requestSingleUpdate deprecated at API 30; acceptable for MVP
        locationManager!!.requestSingleUpdate(provider, locationListener!!, Looper.getMainLooper())
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Location Reply", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Location Request Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alert when a location request arrives but location services are off"
            }
        )
    }

    private fun buildForegroundNotification(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fetching location for SMS reply")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun postAlertNotification(sender: String) {
        // POST_NOTIFICATIONS runtime permission is required on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannels()
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Location request from $sender")
            .setContentText("Enable location services to send location")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
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
    // SMS helpers
    // -------------------------------------------------------------------------

    private fun sendLocationSms(to: String, loc: Location) {
        val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        sendSms(
            to,
            "[PhoneTrack] Lat: ${loc.latitude}, Lon: ${loc.longitude}\n" +
                "Acc: ${loc.accuracy.toInt()}m, Bat: $battery%"
        )
        sendSms(to, "geo:${loc.latitude},${loc.longitude}")
        sendSms(
            to,
            "https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}" +
                "#map=10/${loc.latitude}/${loc.longitude}"
        )
    }

    private fun sendSms(to: String, text: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(to, null, text, null, null)
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterProviderReceiver()
        dismissAlertNotification()
        locationListener?.let { locationManager?.removeUpdates(it) }
        super.onDestroy()
    }
}
