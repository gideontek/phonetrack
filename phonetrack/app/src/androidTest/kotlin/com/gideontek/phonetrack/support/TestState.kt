package com.gideontek.phonetrack.support

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.SmsLocationService
import com.gideontek.phonetrack.SubscriptionService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Known starting state for a test, and helpers to seed stored data with explicit timestamps. */
object TestState {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

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
