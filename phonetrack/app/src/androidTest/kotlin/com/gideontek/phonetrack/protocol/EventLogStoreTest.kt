package com.gideontek.phonetrack.protocol

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.Bucket
import com.gideontek.phonetrack.EnvInfo
import com.gideontek.phonetrack.EventLog
import com.gideontek.phonetrack.EventEntry
import com.gideontek.phonetrack.EventLogStore
import com.gideontek.phonetrack.EventType
import com.gideontek.phonetrack.Outcome
import com.gideontek.phonetrack.OutcomeKind
import com.gideontek.phonetrack.Reason
import com.gideontek.phonetrack.support.ResetStateRule
import com.gideontek.phonetrack.support.TestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.concurrent.thread

/** The JSON and prefs plumbing of the event log (the rules themselves are JVM-tested). */
class EventLogStoreTest {
    @get:Rule
    val reset = ResetStateRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val full = EventEntry(
        t = System.currentTimeMillis(), type = EventType.TICK,
        who = "Contact-A", n = 3, due = 2, lateS = 7, reason = Reason.TIMEOUT, fixS = 4, acc = Bucket.M10_50,
        parts = 2, err = "SecurityException", freqMin = 15, distM = 200, hours = 4,
        outcomes = listOf(
            Outcome("Contact-A", OutcomeKind.SENT),
            Outcome("Contact-B", OutcomeKind.NOT_MOVED, Bucket.M50_100, 200),
        ),
        env = EnvInfo(true, false, true, false, true, "Google sdk_gphone"),
    )

    @Test
    fun everyFieldSurvivesAStoreAndRead() {
        EventLogStore.setEnabled(context, true)
        EventLogStore.append(context, full, durable = true)
        assertEquals(listOf(full), EventLogStore.read(context))
    }

    @Test
    fun appendsAreIgnoredWhileTheLogIsOff() {
        EventLogStore.append(context, full, durable = true)
        assertTrue(EventLogStore.read(context).isEmpty())
        assertFalse(TestState.prefs.contains("event_log"))
    }

    @Test
    fun corruptStoredJsonReadsAsEmptyAndTheLogKeepsWorking() {
        EventLogStore.setEnabled(context, true)
        TestState.prefs.edit().putString("event_log", "{not json").commit()
        assertTrue(EventLogStore.read(context).isEmpty())
        EventLogStore.append(context, full, durable = true)
        assertEquals(1, EventLogStore.read(context).size)
    }

    @Test
    fun aliasesAreStableAndPersist() {
        EventLogStore.setEnabled(context, true)
        assertEquals("Contact-A", EventLogStore.alias(context, "5551234567"))
        assertEquals("Contact-B", EventLogStore.alias(context, "5559876543"))
        assertEquals("Contact-A", EventLogStore.alias(context, "5551234567"))
        assertTrue(TestState.prefs.getString("event_log_aliases", "")!!.contains("5551234567"))
    }

    @Test
    fun turningTheLogOffClearsEntriesAndAliases() {
        EventLogStore.setEnabled(context, true)
        EventLogStore.append(context, full, durable = true)
        EventLogStore.alias(context, "5551234567")
        EventLogStore.setEnabled(context, false)
        assertFalse(EventLogStore.isEnabled(context))
        assertFalse(TestState.prefs.contains("event_log"))
        assertFalse(TestState.prefs.contains("event_log_aliases"))
    }

    @Test
    fun clearEmptiesTheLogButLeavesItOn() {
        EventLogStore.setEnabled(context, true)
        EventLogStore.append(context, full, durable = true)
        EventLogStore.clear(context)
        assertTrue(EventLogStore.isEnabled(context))
        assertTrue(EventLogStore.read(context).isEmpty())
    }

    @Test
    fun entriesOlderThan48HoursAreDroppedOnRead() {
        EventLogStore.setEnabled(context, true)
        val old = full.copy(t = System.currentTimeMillis() - 49 * 3_600_000L)
        TestState.prefs.edit().putString(
            "event_log", org.json.JSONArray().put(EventLogStore.toJson(old)).toString()
        ).commit()
        assertTrue(EventLogStore.read(context).isEmpty())
    }

    @Test
    fun anEntryJustUnder48HoursOldSurvivesARead() {
        EventLogStore.setEnabled(context, true)
        val recent = full.copy(t = System.currentTimeMillis() - 47 * 3_600_000L)
        TestState.prefs.edit().putString(
            "event_log", org.json.JSONArray().put(EventLogStore.toJson(recent)).toString()
        ).commit()
        assertEquals(listOf(recent), EventLogStore.read(context))
    }

    @Test
    fun aliasStoresNothingWhileTheLogIsOff() {
        assertEquals("", EventLogStore.alias(context, "5551234567"))
        assertFalse(TestState.prefs.contains("event_log_aliases"))
    }

    @Test
    fun facadeCallsAreNoOpsWhileTheLogIsOff() {
        EventLog.cancelled(context, "+15551234567")
        EventLog.fixFailed(context, Reason.TIMEOUT)
        assertFalse(TestState.prefs.contains("event_log"))
        assertFalse(TestState.prefs.contains("event_log_aliases"))
    }

    @Test
    fun aCorruptToggleNeverMakesTheFacadeThrow() {
        TestState.prefs.edit().putString("event_log_enabled", "oops").commit()
        EventLog.cancelled(context, "+15551234567")
        assertFalse(EventLog.isEnabled(context))
        EventLog.recordTick(context, EventLog.tickReport(context, 0))
    }

    @Test
    fun explicitJsonNullsReadAsMissing() {
        EventLogStore.setEnabled(context, true)
        val row = """[{"t":${System.currentTimeMillis()},"e":"CANCELLED","who":null,"n":null}]"""
        TestState.prefs.edit().putString("event_log", row).commit()
        val read = EventLogStore.read(context).single()
        assertEquals(null, read.who)
        assertEquals(null, read.n)
    }

    @Test
    fun unknownValuesAreToleratedOnRead() {
        EventLogStore.setEnabled(context, true)
        val now = System.currentTimeMillis()
        val rows = """[{"t":$now,"e":"FROM_THE_FUTURE"},
            {"t":$now,"e":"FIX_FAIL","reason":"made-up","acc":"huge"}]"""
        TestState.prefs.edit().putString("event_log", rows).commit()
        val read = EventLogStore.read(context).single()
        assertEquals(EventType.FIX_FAIL, read.type)
        assertEquals(null, read.reason)
        assertEquals(null, read.acc)
    }

    @Test
    fun envIsNotRecordedAgainWhenNothingChanged_enablingAlreadyRecordedOne() {
        EventLog.setEnabled(context, true)
        EventLog.env(context)
        EventLog.env(context)
        assertEquals(1, EventLogStore.read(context).count { it.type == EventType.ENV })
    }

    @Test
    fun clearingThroughTheFacadeLeavesOnlyAFreshEnvSnapshot() {
        EventLog.setEnabled(context, true)
        EventLog.cancelled(context, "+15551234567")
        EventLog.clear(context)
        assertEquals(listOf(EventType.ENV), EventLogStore.read(context).map { it.type })
    }

    @Test
    fun clearingThroughTheFacadeDoesNothingWhileTheLogIsOff() {
        EventLog.clear(context)
        assertFalse(TestState.prefs.contains("event_log"))
    }

    @Test
    fun concurrentAppendsAreAllKept() {
        EventLogStore.setEnabled(context, true)
        val base = System.currentTimeMillis() - 10_000
        val workers = (0 until 2).map { w ->
            thread {
                repeat(50) { i -> EventLogStore.append(context, full.copy(t = base + w * 100L + i), durable = false) }
            }
        }
        workers.forEach { it.join() }
        assertEquals(100, EventLogStore.read(context).size)
    }
}
