package net.gideontek.phonetrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Periodic CoroutineWorker that sends location updates to all active subscribers.
 * Runs every 15 minutes (WorkManager minimum). Cancels itself when the subscriber
 * list is empty.
 */
class SubscriptionWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val CHANNEL_ID = "subscription_worker"
        private const val NOTIFICATION_ID = 44
    }

    override suspend fun doWork(): Result {
        setForeground(buildForegroundInfo())

        if (ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure()
        }

        var subs = SubscriptionManager.getAll(applicationContext)
        if (subs.isEmpty()) {
            SubscriptionManager.cancelWorker(applicationContext)
            return Result.success()
        }

        // Prune expired subscriptions and notify each expired subscriber
        val expired = SubscriptionManager.pruneExpired(applicationContext)
        for (sub in expired) {
            sendSms(sub.number, "[PhoneTrack] Your location subscription has ended.")
        }

        subs = SubscriptionManager.getAll(applicationContext)
        if (subs.isEmpty()) {
            SubscriptionManager.cancelWorker(applicationContext)
            return Result.success()
        }

        // Fetch location with 60 s timeout; skip this cycle on failure rather than failing the job
        val location = withTimeoutOrNull(60_000L) { fetchLocation() }
            ?: return Result.success()

        val now = System.currentTimeMillis()
        for (sub in subs) {
            val timeSinceLast = now - sub.lastSentAt
            if (timeSinceLast < sub.freqMinutes * 60_000L) continue

            val shouldSend = if (sub.lastLat == 0.0 && sub.lastLon == 0.0) {
                true // first worker cycle — always send
            } else {
                val results = FloatArray(1)
                Location.distanceBetween(
                    sub.lastLat, sub.lastLon,
                    location.latitude, location.longitude,
                    results
                )
                results[0] >= sub.distMeters
            }

            if (shouldSend) {
                sendLocationSms(sub.number, location)
                SubscriptionManager.updateTracking(
                    applicationContext, sub.number, location.latitude, location.longitude, now
                )
            }
        }

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // requestSingleUpdate deprecated at API 30; acceptable for MVP
    private suspend fun fetchLocation(): Location? = suspendCancellableCoroutine { cont ->
        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providersAvailable = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!providersAvailable) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        val listener = LocationListener { loc ->
            if (cont.isActive) cont.resume(loc)
        }

        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        cont.invokeOnCancellation { lm.removeUpdates(listener) }
    }

    private fun sendLocationSms(to: String, loc: Location) {
        sendSms(
            to,
            "[PhoneTrack] Lat: ${loc.latitude}, Lon: ${loc.longitude}\n" +
                "Acc: ${loc.accuracy.toInt()}m"
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
            applicationContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(to, null, text, null, null)
    }

    private fun buildForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Subscription Updates",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Sending periodic location updates")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
