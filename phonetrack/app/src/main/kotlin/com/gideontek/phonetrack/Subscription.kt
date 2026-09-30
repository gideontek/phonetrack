package com.gideontek.phonetrack

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

data class Subscription(
    val number: String,
    val distMeters: Int,       // default 200 m
    val freqMinutes: Int,      // default 15 min
    val durationHours: Int,    // default 4
    val subscribedAt: Long,
    val expiresAt: Long,       // subscribedAt + durationHours * 3_600_000L
    val lastLat: Double,       // 0.0 sentinel = no fix sent yet by service
    val lastLon: Double,
    val lastSentAt: Long       // set to subscribedAt on create; service compares against this
)

object SubscriptionManager {
    private const val PREFS_KEY = "subscriptions_list"
    private val lock = Any()

    fun getAll(ctx: Context): List<Subscription> {
        val prefs = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val result = mutableListOf<Subscription>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(fromJson(obj))
        }
        return result
    }

    /** The subscription for [number], matched with [PhoneNumber.matches]. */
    fun getFor(ctx: Context, number: String): Subscription? =
        getAll(ctx).find { PhoneNumber.matches(it.number, number) }

    fun hasActive(ctx: Context): Boolean = getAll(ctx).isNotEmpty()

    /**
     * Atomically reads the list, applies [transform], and writes it back if it changed.
     * The receiver, the service and the UI share one process, so a process-wide lock keeps
     * concurrent read-modify-write cycles from losing each other's changes.
     */
    fun update(ctx: Context, transform: (List<Subscription>) -> List<Subscription>) {
        synchronized(lock) {
            val current = getAll(ctx)
            val updated = transform(current)
            if (updated != current) writeAll(ctx, updated)
        }
    }

    /**
     * Adds the subscription, replacing any existing one for the same person (numbers are
     * compared with [PhoneNumber.matches], so "+1555…" and "555…" are one subscriber).
     */
    fun add(ctx: Context, sub: Subscription) {
        update(ctx) { list ->
            val index = list.indexOfFirst { PhoneNumber.matches(it.number, sub.number) }
            if (index >= 0) list.toMutableList().also { it[index] = sub } else list + sub
        }
    }

    /**
     * Removes the subscription for [number]; stops the service if the list becomes empty.
     * Returns true if a subscription was actually removed.
     */
    fun remove(ctx: Context, number: String): Boolean {
        var removed = false
        update(ctx) { list ->
            val kept = list.filterNot { PhoneNumber.matches(it.number, number) }
            removed = kept.size != list.size
            kept
        }
        if (getAll(ctx).isEmpty()) stopService(ctx)
        return removed
    }

    fun updateTracking(ctx: Context, number: String, lat: Double, lon: Double, sentAt: Long) {
        update(ctx) { list ->
            val index = list.indexOfFirst { PhoneNumber.matches(it.number, number) }
            if (index < 0) list else list.toMutableList().also {
                it[index] = it[index].copy(lastLat = lat, lastLon = lon, lastSentAt = sentAt)
            }
        }
    }

    /**
     * Removes expired entries from SharedPreferences and returns the removed ones
     * so the caller can send "subscription ended" SMS messages.
     */
    fun pruneExpired(ctx: Context): List<Subscription> {
        val now = System.currentTimeMillis()
        var expired = emptyList<Subscription>()
        update(ctx) { all ->
            expired = all.filter { it.expiresAt <= now }
            all.filter { it.expiresAt > now }
        }
        return expired
    }

    /**
     * Starts [SubscriptionService] as a foreground service if not already running.
     * No-ops if ACCESS_FINE_LOCATION isn't granted: that service declares
     * foregroundServiceType="location", and calling startForegroundService() when the
     * permission isn't already held leads to a guaranteed crash inside the service
     * (either a SecurityException from startForeground() itself, or a
     * ForegroundServiceDidNotStartInTimeException if it's skipped) — so this has to be
     * checked here, before the service is ever started, not inside it. Safe to call
     * speculatively (e.g. from BootReceiver): the service will be started for real the
     * next time this is called after permission is granted.
     */
    fun ensureServiceRunning(ctx: Context) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, SubscriptionService::class.java)
        )
    }

    /** Stops [SubscriptionService]. */
    fun stopService(ctx: Context) {
        ctx.stopService(Intent(ctx, SubscriptionService::class.java))
    }

    private fun writeAll(ctx: Context, subs: List<Subscription>) {
        val array = JSONArray()
        subs.forEach { array.put(toJson(it)) }
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY, array.toString()).apply()
    }

    private fun toJson(sub: Subscription): JSONObject = JSONObject().apply {
        put("number", sub.number)
        put("distMeters", sub.distMeters)
        put("freqMinutes", sub.freqMinutes)
        put("durationHours", sub.durationHours)
        put("subscribedAt", sub.subscribedAt)
        put("expiresAt", sub.expiresAt)
        put("lastLat", sub.lastLat)
        put("lastLon", sub.lastLon)
        put("lastSentAt", sub.lastSentAt)
    }

    private fun fromJson(obj: JSONObject): Subscription = Subscription(
        number = obj.optString("number"),
        distMeters = obj.optInt("distMeters", 200),
        freqMinutes = obj.optInt("freqMinutes", 15),
        durationHours = obj.optInt("durationHours", 4),
        subscribedAt = obj.optLong("subscribedAt"),
        expiresAt = obj.optLong("expiresAt"),
        lastLat = obj.optDouble("lastLat", 0.0),
        lastLon = obj.optDouble("lastLon", 0.0),
        lastSentAt = obj.optLong("lastSentAt")
    )
}
