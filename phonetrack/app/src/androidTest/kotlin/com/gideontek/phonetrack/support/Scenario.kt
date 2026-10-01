package com.gideontek.phonetrack.support

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * One conversation with the receiver: the emulator's own number is the sender, so its replies can
 * be read back. Create it inside the test (after [TestState.reset]) so the baseline is taken
 * against a quiet inbox; each call to [replies] / [assertSilent] moves the baseline forward.
 */
class Scenario {
    val context: Context = ApplicationProvider.getApplicationContext()
    val me: String = Loopback.ownNumber
    private var baseline = Loopback.lastId()
    private var sendMark = TestState.lastSendAt()

    fun send(body: String, from: String = me) = SyntheticSms.deliver(context, from, body)

    /** Listening on, and the own number in the given approval state (APPROVED, PENDING, BLOCKED). */
    fun ready(state: String = "APPROVED", keyword: String = "phonetrack"): Scenario {
        TestState.enableListening(keyword)
        TestState.seedApprovals(Triple(me, state, System.currentTimeMillis()))
        return this
    }

    /**
     * Waits until at least [expect] replies have arrived (or the timeout), lets the inbox settle so
     * any further parts are included, and returns every reply since the last call.
     */
    fun replies(expect: Int = 1, timeoutMs: Long = 12_000): List<String> {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline && Loopback.replies(baseline).size < expect) {
            android.os.SystemClock.sleep(300)
        }
        val settled = Loopback.lastId()
        val out = Loopback.replies(baseline)
        baseline = settled
        sendMark = TestState.lastSendAt()
        return out
    }

    fun ask(body: String, from: String = me, expect: Int = 1): List<String> {
        send(body, from)
        return replies(expect)
    }

    /** Asserts nothing was texted back within [windowMs]. */
    fun assertSilent(windowMs: Long = 3_000, message: String = "expected no reply") {
        // The app stamps `last_send_at` synchronously when it texts, so this catches a send the
        // loopback inbox would only show later than the window below.
        assertEquals("$message (the app sent an SMS)", sendMark, TestState.lastSendAt())
        val quiet = Loopback.staysQuiet(baseline, windowMs) { true }
        val seen = if (quiet) emptyList() else Loopback.bodies(baseline)
        assertTrue("$message, got $seen", quiet)
        baseline = Loopback.lastId()
        sendMark = TestState.lastSendAt()
    }

    companion object {
        /** Everything the receiver and services need, granted up front. */
        fun permissions(): TestRule = GrantPermissionRule.grant(
            Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            *(if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else emptyArray()),
            *(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
        )

        /** Permissions, then a clean state, then (optionally) a mocked GPS fix. */
        fun rules(mockLocation: Boolean = false, reportFixes: Boolean = true): RuleChain {
            var chain = RuleChain.outerRule(permissions()).around(ResetStateRule())
            if (mockLocation) chain = chain.around(MockLocationRule(reportFixes = reportFixes))
            return chain
        }
    }
}

fun assertOneReply(replies: List<String>): String {
    assertEquals("expected exactly one reply, got $replies", 1, replies.size)
    return replies[0]
}
