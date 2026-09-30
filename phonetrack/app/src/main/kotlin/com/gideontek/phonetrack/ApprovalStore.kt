package com.gideontek.phonetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences-backed store for the approvals list (`approvals_list`), shared by
 * [SmsReceiver] and the UI so there is exactly one read-modify-write path. All decisions
 * live in the pure [ApprovalLogic]; this class only does the JSON and prefs plumbing.
 *
 * The receiver and the UI run in the same process, so a process-wide lock is enough to keep
 * one from overwriting the other's change.
 */
object ApprovalStore {
    private const val PREFS_KEY = "approvals_list"
    private val lock = Any()

    fun getAll(ctx: Context): List<ApprovalEntry> = synchronized(lock) { read(ctx) }

    /** The state that applies to [sender], or null if unknown. See [ApprovalLogic.stateFor]. */
    fun stateFor(ctx: Context, sender: String): ApprovalState? =
        ApprovalLogic.stateFor(getAll(ctx), sender)

    fun upsertPending(ctx: Context, sender: String, now: Long) =
        update(ctx) { ApprovalLogic.upsertPending(it, sender, now) }

    fun setState(ctx: Context, number: String, state: ApprovalState) =
        update(ctx) { ApprovalLogic.withState(it, number, state) }

    /** Drops stale PENDING entries (see [ApprovalLogic.prune]). */
    fun prune(ctx: Context, now: Long = System.currentTimeMillis()) =
        update(ctx) { ApprovalLogic.prune(it, now) }

    /** Atomically reads the list, applies [transform], and writes it back if it changed. */
    fun update(ctx: Context, transform: (List<ApprovalEntry>) -> List<ApprovalEntry>) {
        synchronized(lock) {
            val current = read(ctx)
            val updated = transform(current)
            if (updated != current) write(ctx, updated)
        }
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

    private fun read(ctx: Context): List<ApprovalEntry> {
        val json = prefs(ctx).getString(PREFS_KEY, "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val result = mutableListOf<ApprovalEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val number = obj.optString("number")
            if (number.isEmpty()) continue
            val state = when (obj.optString("state", "PENDING")) {
                "APPROVED" -> ApprovalState.APPROVED
                "BLOCKED" -> ApprovalState.BLOCKED
                else -> ApprovalState.PENDING
            }
            result.add(ApprovalEntry(number, state, obj.optLong("firstSeen"), obj.optLong("lastSeen")))
        }
        return result
    }

    private fun write(ctx: Context, entries: List<ApprovalEntry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("number", it.number)
                    .put("state", it.state.name)
                    .put("firstSeen", it.firstSeen)
                    .put("lastSeen", it.lastSeen)
            )
        }
        prefs(ctx).edit().putString(PREFS_KEY, array.toString()).apply()
    }
}
