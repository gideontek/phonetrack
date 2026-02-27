package net.gideontek.phonetrack

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Subscription(
    val number: String,
    val distMeters: Int,       // default 100 m
    val freqMinutes: Int,      // default 15 (WorkManager minimum)
    val durationMinutes: Int,  // default 240
    val subscribedAt: Long,
    val expiresAt: Long,       // subscribedAt + durationMinutes * 60_000L
    val lastLat: Double,       // 0.0 sentinel = no fix sent yet by worker
    val lastLon: Double,
    val lastSentAt: Long       // set to subscribedAt on create; worker compares against this
)

object SubscriptionManager {
    private const val PREFS_KEY = "subscriptions_list"
    private const val WORKER_NAME = "subscription_check"

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

    fun getFor(ctx: Context, number: String): Subscription? =
        getAll(ctx).find { it.number == number }

    fun hasActive(ctx: Context): Boolean = getAll(ctx).isNotEmpty()

    /** Adds or replaces the subscription for [sub.number]. */
    fun add(ctx: Context, sub: Subscription) {
        val prefs = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        var replaced = false
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("number") == sub.number) {
                array.put(i, toJson(sub))
                replaced = true
                break
            }
        }
        if (!replaced) array.put(toJson(sub))
        prefs.edit().putString(PREFS_KEY, array.toString()).apply()
    }

    /** Removes the subscription for [number]; cancels worker if list becomes empty. */
    fun remove(ctx: Context, number: String) {
        val prefs = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("number") != number) newArray.put(obj)
        }
        prefs.edit().putString(PREFS_KEY, newArray.toString()).apply()
        if (newArray.length() == 0) cancelWorker(ctx)
    }

    fun updateTracking(ctx: Context, number: String, lat: Double, lon: Double, sentAt: Long) {
        val prefs = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("number") == number) {
                obj.put("lastLat", lat)
                obj.put("lastLon", lon)
                obj.put("lastSentAt", sentAt)
                array.put(i, obj)
                break
            }
        }
        prefs.edit().putString(PREFS_KEY, array.toString()).apply()
    }

    /**
     * Removes expired entries from SharedPreferences and returns the removed ones
     * so the caller can send "subscription ended" SMS messages.
     */
    fun pruneExpired(ctx: Context): List<Subscription> {
        val now = System.currentTimeMillis()
        val all = getAll(ctx)
        val expired = all.filter { it.expiresAt <= now }
        if (expired.isNotEmpty()) {
            val prefs = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
            val active = all.filter { it.expiresAt > now }
            val newArray = JSONArray()
            active.forEach { newArray.put(toJson(it)) }
            prefs.edit().putString(PREFS_KEY, newArray.toString()).apply()
        }
        return expired
    }

    fun ensureWorkerScheduled(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORKER_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelWorker(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORKER_NAME)
    }

    private fun toJson(sub: Subscription): JSONObject = JSONObject().apply {
        put("number", sub.number)
        put("distMeters", sub.distMeters)
        put("freqMinutes", sub.freqMinutes)
        put("durationMinutes", sub.durationMinutes)
        put("subscribedAt", sub.subscribedAt)
        put("expiresAt", sub.expiresAt)
        put("lastLat", sub.lastLat)
        put("lastLon", sub.lastLon)
        put("lastSentAt", sub.lastSentAt)
    }

    private fun fromJson(obj: JSONObject): Subscription = Subscription(
        number = obj.optString("number"),
        distMeters = obj.optInt("distMeters", 100),
        freqMinutes = obj.optInt("freqMinutes", 15),
        durationMinutes = obj.optInt("durationMinutes", 240),
        subscribedAt = obj.optLong("subscribedAt"),
        expiresAt = obj.optLong("expiresAt"),
        lastLat = obj.optDouble("lastLat", 0.0),
        lastLon = obj.optDouble("lastLon", 0.0),
        lastSentAt = obj.optLong("lastSentAt")
    )
}
