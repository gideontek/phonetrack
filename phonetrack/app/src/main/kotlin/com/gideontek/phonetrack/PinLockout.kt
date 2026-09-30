package com.gideontek.phonetrack

/**
 * Persisted failed-attempt state for the settings PIN: [failures] wrong guesses in the current
 * round, the epoch-ms instant until which unlocking is refused, and how many rounds have locked
 * us out so far ([level], which sets the next lockout's length and resets on a correct PIN).
 */
data class PinLockState(val failures: Int = 0, val lockedUntil: Long = 0L, val level: Int = 0)

/**
 * Pure lockout rules. [ATTEMPTS_PER_ROUND] wrong PINs in a row lock unlocking for 1, then 5,
 * then 15, then 60 minutes (60 thereafter); a correct PIN resets everything. Uses the wall
 * clock: someone able to change the phone's clock could skip a lockout, which is accepted for
 * a casual-access threat model. A clock moved backwards can't lock the owner out for long,
 * because the remaining time is capped at [MAX_LOCK_MS].
 */
object PinLockout {
    const val ATTEMPTS_PER_ROUND = 5
    private val LOCK_MS = longArrayOf(60_000L, 300_000L, 900_000L, 3_600_000L)
    val MAX_LOCK_MS = LOCK_MS.last()

    /** Length of the lockout triggered at [level] (1-based); levels past the table reuse the longest. */
    fun durationMs(level: Int): Long = LOCK_MS[(level - 1).coerceIn(0, LOCK_MS.lastIndex)]

    fun remainingMs(state: PinLockState, now: Long): Long =
        (state.lockedUntil - now).coerceIn(0L, MAX_LOCK_MS)

    fun isLocked(state: PinLockState, now: Long): Boolean = remainingMs(state, now) > 0L

    /** Wrong guesses left in this round before the next lockout. */
    fun attemptsLeft(state: PinLockState): Int = ATTEMPTS_PER_ROUND - state.failures

    /** State after a wrong PIN: the round's last failure starts a lockout and a new round. */
    fun onFailure(state: PinLockState, now: Long): PinLockState {
        val failures = state.failures + 1
        if (failures < ATTEMPTS_PER_ROUND) return state.copy(failures = failures)
        val level = state.level + 1
        return PinLockState(failures = 0, lockedUntil = now + durationMs(level), level = level)
    }

    /** State after the correct PIN. */
    fun onSuccess(): PinLockState = PinLockState()

    /** "1 min", "5 min", ... rounded up so a lockout never reads as shorter than it is. */
    fun formatRemaining(ms: Long): String {
        val minutes = maxOf(1L, (ms + 59_999L) / 60_000L)
        return if (minutes == 1L) "1 minute" else "$minutes minutes"
    }
}
