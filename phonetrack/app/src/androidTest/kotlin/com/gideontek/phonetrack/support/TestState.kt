package com.gideontek.phonetrack.support

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.ApprovalEntry
import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.PinStore
import com.gideontek.phonetrack.ApprovalStore
import com.gideontek.phonetrack.SmsLocationService
import com.gideontek.phonetrack.Subscription
import com.gideontek.phonetrack.SubscriptionManager
import com.gideontek.phonetrack.SubscriptionService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Known starting state for a test, and helpers to seed stored data with explicit timestamps. */
object TestState {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    val prefs get() = context.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

    /** Stops the services, clears every stored preference and cancels notifications. */
    fun reset() {
        context.stopService(Intent(context, SubscriptionService::class.java))
        context.stopService(Intent(context, SmsLocationService::class.java))
        prefs.edit().clear().commit()
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    fun enableListening(keyword: String = "phonetrack") {
        prefs.edit().putBoolean("sms_enabled", true).putString("sms_keyword", keyword).commit()
    }

    /** Overwrites the approvals list. Entries are (number, state, lastSeen epoch ms). */
    fun seedApprovals(vararg entries: Triple<String, String, Long>) {
        val array = JSONArray()
        for ((number, state, lastSeen) in entries) {
            array.put(
                JSONObject().put("number", number).put("state", state)
                    .put("firstSeen", lastSeen).put("lastSeen", lastSeen)
            )
        }
        prefs.edit().putString("approvals_list", array.toString()).commit()
    }

    /** Seeds stored subscriptions (numbers normalized as the receiver stores them). */
    fun seedSubscriptions(vararg subs: Subscription) {
        val array = JSONArray()
        for (s in subs) {
            array.put(
                JSONObject().put("number", s.number).put("distMeters", s.distMeters)
                    .put("freqMinutes", s.freqMinutes).put("durationHours", s.durationHours)
                    .put("subscribedAt", s.subscribedAt).put("expiresAt", s.expiresAt)
                    .put("lastLat", s.lastLat).put("lastLon", s.lastLon).put("lastSentAt", s.lastSentAt)
            )
        }
        prefs.edit().putString("subscriptions_list", array.toString()).commit()
    }

    /** A subscription of [number] with the given ends-in time, for seeding. */
    fun subscription(
        number: String,
        expiresInMs: Long = 3_600_000L,
        dist: Int = 200,
        freq: Int = 15,
        hours: Int = 4,
        now: Long = System.currentTimeMillis()
    ) = Subscription(number, dist, freq, hours, now, now + expiresInMs, 0.0, 0.0, now)

    fun subscriptions(): List<Subscription> = SubscriptionManager.getAll(context)

    fun approvals(): List<ApprovalEntry> = ApprovalStore.getAll(context)

    /** Overwrites `rate_state`: key -> (window start, count). */
    fun seedRate(key: String, start: Long, count: Int) {
        prefs.edit().putString(
            "rate_state",
            JSONObject().put(key, JSONObject().put("start", start).put("count", count).put("noticed", false)).toString()
        ).commit()
    }

    /** Last time anything was texted (0 = nothing sent), readable for numbers the loopback cannot see. */
    fun lastSendAt(): Long = prefs.getLong("last_send_at", 0L)

    /** Seeds a PIN (hashed, as the app stores it). The next launch starts locked. */
    fun seedPin(pin: String) = PinStore.set(context, pin)

    /** Pending numbers that asked [askedMinutesAgo] minutes ago (plus 20 s, so the "N min ago" text is stable for a while). */
    fun seedPending(vararg numbers: String, askedMinutesAgo: Long = 12) {
        val at = System.currentTimeMillis() - askedMinutesAgo * 60_000L - 20_000L
        seedApprovals(*numbers.map { Triple(it, "PENDING", at) }.toTypedArray())
    }

    fun approvalState(number: String): ApprovalState? = approvals().firstOrNull { it.number == number }?.state

    fun approvalsJson(): String = prefs.getString("approvals_list", "[]") ?: "[]"
}

/** Runs [TestState.reset] before each test, before any activity rule inside it starts. */
class ResetStateRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            TestState.reset()
            base.evaluate()
        }
    }
}
