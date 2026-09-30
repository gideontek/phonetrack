package com.gideontek.phonetrack

import android.content.Context

sealed class PinResult {
    object Success : PinResult()
    data class Wrong(val attemptsLeft: Int) : PinResult()
    data class Locked(val remainingMs: Long) : PinResult()
}

/**
 * SharedPreferences-backed settings-PIN store: the hash (`settings_pin_hash`) and the lockout
 * state (`pin_fail_count`, `pin_locked_until`, `pin_lock_level`). The rules live in the pure
 * [PinHasher] and [PinLockout]; this only does the prefs plumbing, behind a process-wide lock so
 * concurrent guesses can't dodge the attempt counter.
 */
object PinStore {
    private const val HASH_KEY = "settings_pin_hash"
    private const val LEGACY_KEY = "settings_pin"     // plaintext, replaced by the hash
    private const val FAILS_KEY = "pin_fail_count"
    private const val UNTIL_KEY = "pin_locked_until"
    private const val LEVEL_KEY = "pin_lock_level"
    private val lock = Any()

    /**
     * True if a PIN is set. A legacy plaintext PIN that hasn't been migrated yet counts, so the
     * settings start locked even before [migrateLegacy] has run.
     */
    fun isSet(ctx: Context): Boolean {
        val p = prefs(ctx)
        return !p.getString(HASH_KEY, null).isNullOrEmpty() || !p.getString(LEGACY_KEY, null).isNullOrEmpty()
    }

    /** Stores [pin] (hashed), drops any legacy plaintext, and clears the lockout state. */
    fun set(ctx: Context, pin: String) = synchronized(lock) {
        prefs(ctx).edit()
            .putString(HASH_KEY, PinHasher.hash(pin))
            .remove(LEGACY_KEY)
            .putInt(FAILS_KEY, 0).putLong(UNTIL_KEY, 0L).putInt(LEVEL_KEY, 0)
            .apply()
    }

    fun remove(ctx: Context) = synchronized(lock) {
        prefs(ctx).edit()
            .remove(HASH_KEY).remove(LEGACY_KEY)
            .remove(FAILS_KEY).remove(UNTIL_KEY).remove(LEVEL_KEY)
            .apply()
    }

    /** Milliseconds until unlocking is allowed again, or 0 if it is allowed now. */
    fun lockRemainingMs(ctx: Context, now: Long = System.currentTimeMillis()): Long =
        synchronized(lock) { PinLockout.remainingMs(readState(ctx), now) }

    /**
     * Checks [pin]. While locked out the PIN isn't even checked (and the attempt isn't counted),
     * so guessing during a lockout gains nothing. Right PIN resets the lockout state.
     */
    fun verify(ctx: Context, pin: String, now: Long = System.currentTimeMillis()): PinResult =
        synchronized(lock) {
            val state = readState(ctx)
            if (PinLockout.isLocked(state, now)) return PinResult.Locked(PinLockout.remainingMs(state, now))
            val stored = prefs(ctx).getString(HASH_KEY, null)
            if (stored.isNullOrEmpty()) return PinResult.Success   // no PIN set
            if (PinHasher.verify(pin, stored)) {
                writeState(ctx, PinLockout.onSuccess())
                return PinResult.Success
            }
            val failed = PinLockout.onFailure(state, now)
            writeState(ctx, failed)
            if (PinLockout.isLocked(failed, now)) PinResult.Locked(PinLockout.remainingMs(failed, now))
            else PinResult.Wrong(PinLockout.attemptsLeft(failed))
        }

    /** Hashes a legacy plaintext PIN once and deletes it. The old PIN keeps working. */
    fun migrateLegacy(ctx: Context) = synchronized(lock) {
        val p = prefs(ctx)
        val legacy = p.getString(LEGACY_KEY, null) ?: return@synchronized
        val hash = PinMigration.hashFor(legacy, p.getString(HASH_KEY, null))
        val edit = p.edit().remove(LEGACY_KEY)
        if (hash != null) edit.putString(HASH_KEY, hash)
        edit.apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

    private fun readState(ctx: Context): PinLockState {
        val p = prefs(ctx)
        return PinLockState(p.getInt(FAILS_KEY, 0), p.getLong(UNTIL_KEY, 0L), p.getInt(LEVEL_KEY, 0))
    }

    private fun writeState(ctx: Context, s: PinLockState) {
        prefs(ctx).edit()
            .putInt(FAILS_KEY, s.failures).putLong(UNTIL_KEY, s.lockedUntil).putInt(LEVEL_KEY, s.level)
            .apply()
    }
}
