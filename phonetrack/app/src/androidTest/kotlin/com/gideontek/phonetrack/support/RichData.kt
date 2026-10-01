package com.gideontek.phonetrack.support

/** A representative, fully populated state: two pending numbers, approved and blocked ones, subscriptions. */
object RichData {
    fun seed(pin: Boolean = false) {
        val now = System.currentTimeMillis()
        TestState.enableListening()
        TestState.seedApprovals(
            Triple("+15550101001", "PENDING", now - 12 * 60_000L),
            Triple("+447911123456", "PENDING", now - 2 * 3_600_000L),
            Triple("+15550101002", "APPROVED", now),
            Triple("+15550101003", "APPROVED", now),
            Triple("+15550101004", "BLOCKED", now)
        )
        TestState.seedSubscriptions(
            TestState.subscription("+15550101002", expiresInMs = 2 * 3_600_000L + 10 * 60_000L + 30_000L),
            TestState.subscription("+15550101003", expiresInMs = 14 * 60_000L + 30_000L, dist = 0, freq = 5, hours = 1)
        )
        TestState.prefs.edit().putLong("last_receive_at", now - 12 * 60_000L).commit()
        if (pin) TestState.seedPin("1234")
    }
}
