package com.gideontek.phonetrack.protocol

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.Bucket
import com.gideontek.phonetrack.EnvInfo
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
}
