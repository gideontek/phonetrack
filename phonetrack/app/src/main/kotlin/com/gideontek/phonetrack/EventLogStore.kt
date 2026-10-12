package com.gideontek.phonetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences-backed storage for the event log: the entries (`event_log`, a JSON array,
 * oldest first), the number-to-alias map (`event_log_aliases`, never shown or copied) and the
 * toggle (`event_log_enabled`, absent = off). The rules are in the pure [EventLogLogic]; this does
 * the JSON and prefs plumbing behind a process-wide lock, like [RateStore].
 */
object EventLogStore {
    const val KEY_ENABLED = "event_log_enabled"
    const val KEY_LOG = "event_log"
    const val KEY_ALIASES = "event_log_aliases"
    private val lock = Any()

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    /** Turning the log off deletes the entries and the alias map. */
    fun setEnabled(ctx: Context, on: Boolean) = synchronized(lock) {
        val edit = prefs(ctx).edit().putBoolean(KEY_ENABLED, on)
        if (!on) edit.remove(KEY_LOG).remove(KEY_ALIASES)
        edit.commit()
        Unit
    }

    fun clear(ctx: Context) = synchronized(lock) {
        prefs(ctx).edit().remove(KEY_LOG).remove(KEY_ALIASES).commit()
        Unit
    }

    /** The stored entries, oldest first, without any older than 48 hours. */
    fun read(ctx: Context, now: Long = System.currentTimeMillis()): List<EventEntry> = synchronized(lock) {
        EventLogLogic.prune(readLocked(ctx), now)
    }

    /** Adds [entry] if the log is on. [durable] writes synchronously, for events right before a possible kill. */
    fun append(ctx: Context, entry: EventEntry, durable: Boolean) = synchronized(lock) {
        if (!isEnabled(ctx)) return@synchronized
        val updated = EventLogLogic.append(readLocked(ctx), entry)
        val array = JSONArray()
        updated.forEach { array.put(toJson(it)) }
        val edit = prefs(ctx).edit().putString(KEY_LOG, array.toString())
        if (durable) edit.commit() else edit.apply()
    }

    /** The alias for [key] (a [PhoneNumber.rateKey]), assigning the next free one the first time. */
    fun alias(ctx: Context, key: String): String = synchronized(lock) {
        val current = readAliases(ctx)
        val (name, map) = EventLogLogic.assignAlias(current, key)
        if (map != current) {
            val obj = JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            prefs(ctx).edit().putString(KEY_ALIASES, obj.toString()).apply()
        }
        name
    }

    // --- JSON ------------------------------------------------------------------------------

    private fun readLocked(ctx: Context): List<EventEntry> {
        val json = prefs(ctx).getString(KEY_LOG, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { o -> fromJson(o) } }
    }

    private fun readAliases(ctx: Context): Map<String, String> {
        val json = prefs(ctx).getString(KEY_ALIASES, "{}") ?: "{}"
        val obj = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
        return obj.keys().asSequence().associateWith { obj.optString(it) }
    }

    internal fun toJson(e: EventEntry): JSONObject = JSONObject().apply {
        put("t", e.t)
        put("e", e.type.name)
        e.who?.let { put("who", it) }
        e.n?.let { put("n", it) }
        e.due?.let { put("due", it) }
        e.lateS?.let { put("lateS", it) }
        e.reason?.let { put("reason", it.text) }
        e.fixS?.let { put("fixS", it) }
        e.acc?.let { put("acc", it.text) }
        e.parts?.let { put("parts", it) }
        e.err?.let { put("err", it) }
        e.freqMin?.let { put("freqMin", it) }
        e.distM?.let { put("distM", it) }
        e.hours?.let { put("hours", it) }
        if (e.outcomes.isNotEmpty()) {
            val array = JSONArray()
            e.outcomes.forEach { array.put(outcomeToJson(it)) }
            put("outcomes", array)
        }
        e.env?.let { put("env", envToJson(it)) }
    }

    internal fun fromJson(o: JSONObject): EventEntry? {
        val type = EventType.entries.firstOrNull { it.name == o.optString("e") } ?: return null
        fun int(key: String): Int? = if (o.has(key)) o.optInt(key) else null
        fun str(key: String): String? = if (o.has(key)) o.optString(key) else null
        val outcomes = o.optJSONArray("outcomes")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { outcomeFromJson(it) } }
        } ?: emptyList()
        return EventEntry(
            t = o.optLong("t"), type = type, who = str("who"), n = int("n"), due = int("due"),
            lateS = int("lateS"), reason = Reason.fromText(str("reason")), fixS = int("fixS"),
            acc = Bucket.fromText(str("acc")), parts = int("parts"), err = str("err"),
            freqMin = int("freqMin"), distM = int("distM"), hours = int("hours"),
            outcomes = outcomes, env = o.optJSONObject("env")?.let { envFromJson(it) },
        )
    }

    private fun outcomeToJson(o: Outcome) = JSONObject().apply {
        put("who", o.who)
        put("k", o.kind.text)
        o.moved?.let { put("moved", it.text) }
        o.needM?.let { put("needM", it) }
    }

    private fun outcomeFromJson(o: JSONObject): Outcome? {
        val kind = OutcomeKind.fromText(o.optString("k")) ?: return null
        return Outcome(
            who = o.optString("who"), kind = kind, moved = Bucket.fromText(o.optString("moved")),
            needM = if (o.has("needM")) o.optInt("needM") else null,
        )
    }

    private fun envToJson(e: EnvInfo) = JSONObject()
        .put("fine", e.fineLocation).put("bg", e.backgroundLocation).put("notif", e.notifications)
        .put("loc", e.locationServices).put("batt", e.batteryUnrestricted).put("dev", e.device)

    private fun envFromJson(o: JSONObject) = EnvInfo(
        fineLocation = o.optBoolean("fine"), backgroundLocation = o.optBoolean("bg"),
        notifications = o.optBoolean("notif"), locationServices = o.optBoolean("loc"),
        batteryUnrestricted = o.optBoolean("batt"), device = o.optString("dev"),
    )
}
