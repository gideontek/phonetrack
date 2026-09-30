package com.gideontek.phonetrack

import android.content.Context
import org.json.JSONObject

/**
 * SharedPreferences-backed store for [RateLimiter] state (`rate_state`, a JSON object of
 * `key -> {start, count, noticed}`). The decisions are in the pure [RateLimiter]; this only
 * does the JSON and prefs plumbing, behind a process-wide lock like [ApprovalStore].
 */
object RateStore {
    private const val PREFS_KEY = "rate_state"
    private val lock = Any()

    /** Records one hit for [key] and returns whether it is within [limit] per window. */
    fun check(
        ctx: Context,
        key: String,
        limit: Int,
        now: Long = System.currentTimeMillis()
    ): RateDecision = synchronized(lock) {
        val state = read(ctx)
        val decision = RateLimiter.check(state, key, now, limit)
        if (decision.state != state) write(ctx, decision.state)
        decision
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

    private fun read(ctx: Context): Map<String, RateWindow> {
        val json = prefs(ctx).getString(PREFS_KEY, "{}") ?: "{}"
        val obj = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
        val result = mutableMapOf<String, RateWindow>()
        for (key in obj.keys()) {
            val w = obj.optJSONObject(key) ?: continue
            result[key] = RateWindow(w.optLong("start"), w.optInt("count"), w.optBoolean("noticed"))
        }
        return result
    }

    private fun write(ctx: Context, state: Map<String, RateWindow>) {
        val obj = JSONObject()
        state.forEach { (key, w) ->
            obj.put(key, JSONObject().put("start", w.start).put("count", w.count).put("noticed", w.noticed))
        }
        prefs(ctx).edit().putString(PREFS_KEY, obj.toString()).apply()
    }
}
