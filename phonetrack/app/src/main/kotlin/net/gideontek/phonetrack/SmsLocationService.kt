package net.gideontek.phonetrack

import android.Manifest
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
 */
class SmsLocationService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "sms_location"
        private const val TIMEOUT_MS = 60_000L
    }

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
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

    private fun sendLocationSms(to: String, loc: Location) {
        val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        sendSms(
            to,
            "[PhoneTrack] Lat: ${loc.latitude}, Lon: ${loc.longitude}\n" +
                "Acc: ${loc.accuracy.toInt()}m, Bat: $battery%"
        )
        sendSms(
            to,
            "geo:${loc.latitude},${loc.longitude}"
        )
        sendSms(
            to,
            "https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}#map=10/${loc.latitude}/${loc.longitude}"
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

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Location Reply",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fetching location for SMS reply")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationListener?.let { locationManager?.removeUpdates(it) }
        super.onDestroy()
    }
}
