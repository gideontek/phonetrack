package com.gideontek.phonetrack.protocol

import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.PinHasher
import com.gideontek.phonetrack.PinResult
import com.gideontek.phonetrack.PinStore
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Stored data from older versions, and the PIN store's lockout, with real prefs. */
class MigrationTest {
    @get:Rule
    val rules = Scenario.rules()

    private val context get() = Scenario().context

    /** Any incoming message runs the migration first, even while listening is off. */
    private fun anyMessage() = Scenario().send("hello", from = "+15550009999")

    private fun legacyApprovals() = JSONArray()
        .put(JSONObject().put("number", "(555) 000-1111").put("state", "APPROVED"))
        .put(JSONObject().put("number", "555-000-1111").put("state", "BLOCKED"))
        .put(JSONObject().put("number", "PhoneTrk").put("state", "PENDING"))
        .toString()

    @Test
    fun oldNumbersAreNormalizedMergedAndStampedOnTheFirstMessage() {
        TestState.prefs.edit().putString("approvals_list", legacyApprovals())
            .putString("subscriptions_list", JSONArray().put(JSONObject()
                .put("number", "555-000-2222").put("freqMinutes", 15).put("expiresAt", System.currentTimeMillis() + 3_600_000L)).toString())
            .commit()
        anyMessage()
        val entry = TestState.approvals().single()
        assertEquals("5550001111", entry.number)
        assertEquals("the stricter state wins", ApprovalState.BLOCKED, entry.state)
        assertTrue("old entries get a timestamp so they are not pruned", entry.firstSeen > 0 && entry.lastSeen > 0)
        assertEquals("5550002222", TestState.subscriptions().single().number)
        assertEquals(2, TestState.prefs.getInt("prefs_schema_version", 0))
    }

    @Test
    fun runningItAgainChangesNothing() {
        TestState.prefs.edit().putString("approvals_list", legacyApprovals()).commit()
        anyMessage()
        val once = TestState.approvalsJson()
        anyMessage()
        assertEquals(once, TestState.approvalsJson())
    }

    @Test
    fun aPlaintextPinIsHashedAndStillWorks() {
        TestState.prefs.edit().putString("settings_pin", "4321").commit()
        assertTrue("a legacy PIN counts as set before migrating", PinStore.isSet(context))
        anyMessage()
        assertFalse(TestState.prefs.contains("settings_pin"))
        assertTrue(TestState.prefs.getString("settings_pin_hash", "")!!.startsWith("v1:"))
        assertEquals(PinResult.Success, PinStore.verify(context, "4321"))
        assertTrue(PinStore.verify(context, "0000") is PinResult.Wrong)
    }

    @Test
    fun anExistingHashBeatsALeftoverPlaintextPin() {
        TestState.prefs.edit().putString("settings_pin", "1111").putString("settings_pin_hash", PinHasher.hash("9999")).commit()
        anyMessage()
        assertFalse(TestState.prefs.contains("settings_pin"))
        assertEquals(PinResult.Success, PinStore.verify(context, "9999"))
        assertTrue(PinStore.verify(context, "1111") is PinResult.Wrong)
    }

    @Test
    fun fiveWrongPinsLockUnlockingAndTheLockoutEscalates() {
        PinStore.set(context, "1234")
        val t0 = 1_000_000_000_000L
        repeat(4) { assertTrue(PinStore.verify(context, "0000", t0) is PinResult.Wrong) }
        val locked = PinStore.verify(context, "0000", t0) as PinResult.Locked
        assertEquals(60_000L, locked.remainingMs)
        assertTrue("the right PIN is refused while locked", PinStore.verify(context, "1234", t0 + 1_000) is PinResult.Locked)
        // After the minute the next round starts; five more wrong PINs lock for five minutes.
        val t1 = t0 + 61_000
        repeat(4) { assertTrue(PinStore.verify(context, "0000", t1) is PinResult.Wrong) }
        assertEquals(300_000L, (PinStore.verify(context, "0000", t1) as PinResult.Locked).remainingMs)
    }

    @Test
    fun theRightPinResetsTheLockoutLevel() {
        PinStore.set(context, "1234")
        val t0 = 1_000_000_000_000L
        repeat(5) { PinStore.verify(context, "0000", t0) }
        val t1 = t0 + 61_000
        assertEquals(PinResult.Success, PinStore.verify(context, "1234", t1))
        repeat(4) { PinStore.verify(context, "0000", t1) }
        assertEquals("level reset: a minute again, not five", 60_000L, (PinStore.verify(context, "0000", t1) as PinResult.Locked).remainingMs)
    }
}
