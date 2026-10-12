package com.gideontek.phonetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class EventLogLogicTest {

    private val hour = 3_600_000L
    private fun entry(t: Long, type: EventType = EventType.TICK) = EventEntry(t, type)

    // --- retention -----------------------------------------------------------------------

    @Test fun `the cap drops the oldest entries first`() {
        var log = emptyList<EventEntry>()
        repeat(305) { log = EventLogLogic.append(log, entry(it.toLong())) }
        assertEquals(300, log.size)
        assertEquals(5L, log.first().t)
        assertEquals(304L, log.last().t)
    }

    @Test fun `entries 48 hours old or older are pruned`() {
        val now = 100 * hour
        val log = listOf(entry(now - 49 * hour), entry(now - 48 * hour), entry(now - 47 * hour), entry(now))
        assertEquals(listOf(now - 47 * hour, now), EventLogLogic.prune(log, now).map { it.t })
    }

    @Test fun `append prunes by the new entry's time`() {
        val log = listOf(entry(0L), entry(2 * hour))
        val out = EventLogLogic.append(log, entry(49 * hour))
        assertEquals(listOf(2 * hour, 49 * hour), out.map { it.t })
    }

    // --- aliases -------------------------------------------------------------------------

    @Test fun `alias names run A to Z then AA`() {
        assertEquals("Contact-A", EventLogLogic.aliasName(0))
        assertEquals("Contact-B", EventLogLogic.aliasName(1))
        assertEquals("Contact-Z", EventLogLogic.aliasName(25))
        assertEquals("Contact-AA", EventLogLogic.aliasName(26))
        assertEquals("Contact-AB", EventLogLogic.aliasName(27))
    }

    @Test fun `a key keeps its alias and a new key gets the next one`() {
        val (a, m1) = EventLogLogic.assignAlias(emptyMap(), "5551234567")
        val (b, m2) = EventLogLogic.assignAlias(m1, "5559876543")
        val (again, m3) = EventLogLogic.assignAlias(m2, "5551234567")
        assertEquals("Contact-A", a)
        assertEquals("Contact-B", b)
        assertEquals("Contact-A", again)
        assertEquals(m2, m3)
    }

    // --- buckets and small helpers --------------------------------------------------------

    @Test fun `bucket boundaries`() {
        assertEquals(Bucket.UNDER_10, EventLogLogic.bucket(9.9f))
        assertEquals(Bucket.M10_50, EventLogLogic.bucket(10f))
        assertEquals(Bucket.M10_50, EventLogLogic.bucket(49.9f))
        assertEquals(Bucket.M50_100, EventLogLogic.bucket(50f))
        assertEquals(Bucket.M100_200, EventLogLogic.bucket(100f))
        assertEquals(Bucket.M200_500, EventLogLogic.bucket(200f))
        assertEquals(Bucket.M500_1K, EventLogLogic.bucket(500f))
        assertEquals(Bucket.K1_5, EventLogLogic.bucket(1_000f))
        assertEquals(Bucket.OVER_5K, EventLogLogic.bucket(5_000f))
    }

    @Test fun `lateness is whole seconds and never negative`() {
        assertEquals(2, EventLogLogic.lateSeconds(now = 12_500, expectedAt = 10_000))
        assertEquals(0, EventLogLogic.lateSeconds(now = 9_000, expectedAt = 10_000))
    }

    @Test fun `safeText keeps letters digits and a few separators and caps the length`() {
        assertEquals("abcd", EventLogLogic.safeText("a:b/c@d", 10))
        assertEquals("Samsung SM-S918B", EventLogLogic.safeText("Samsung SM-S918B", 40))
        assertEquals("abc", EventLogLogic.safeText("abcdef", 3))
    }

    // --- the per-tick accumulator ----------------------------------------------------------

    private val aliases = mutableMapOf<String, String>()
    private fun aliasOf(number: String): String {
        val (name, map) = EventLogLogic.assignAlias(aliases, number)
        aliases.clear(); aliases.putAll(map)
        return name
    }

    @Test fun `a tick report yields one entry and then nothing`() {
        val report = TickReport(startedAt = 12_500, expectedAt = 10_000, aliasOf = ::aliasOf)
        report.subs = 2; report.due = 1
        assertNotNull(report.finish(now = 13_000))
        assertNull(report.finish(now = 14_000))
    }

    @Test fun `a tick report carries lateness fix and outcomes`() {
        aliases.clear()
        val report = TickReport(startedAt = 12_500, expectedAt = 10_000, aliasOf = ::aliasOf)
        report.subs = 2; report.due = 2
        report.fix(seconds = 4, accuracyM = 5f)
        report.sent("5551111111")
        report.notMoved("5552222222", movedMeters = 70f, needM = 200)
        val e = report.finish(now = 13_000)!!
        assertEquals(EventType.TICK, e.type)
        assertEquals(13_000L, e.t)
        assertEquals(2, e.lateS)
        assertEquals(4, e.fixS)
        assertEquals(Bucket.UNDER_10, e.acc)
        assertEquals(
            listOf(
                Outcome("Contact-A", OutcomeKind.SENT),
                Outcome("Contact-B", OutcomeKind.NOT_MOVED, Bucket.M50_100, 200),
            ),
            e.outcomes
        )
    }

    @Test fun `listener off marks every due subscriber`() {
        aliases.clear()
        val report = TickReport(startedAt = 1_000, expectedAt = 1_000, aliasOf = ::aliasOf)
        report.listenerOff(listOf("5551111111", "5552222222"))
        assertEquals(
            listOf(OutcomeKind.LISTENER_OFF, OutcomeKind.LISTENER_OFF),
            report.finish(now = 1_000)!!.outcomes.map { it.kind }
        )
    }

    // --- rendering -------------------------------------------------------------------------

    @Test fun `the rendered log is the header then the newest event first`() {
        val log = listOf(
            EventEntry(0, EventType.SERVICE_START, reason = Reason.REQUESTED),
            EventEntry(1_000, EventType.SUBSCRIBED, who = "Contact-A", freqMin = 15, distM = 200, hours = 4),
            EventEntry(
                61_000, EventType.TICK, n = 2, due = 1, lateS = 2, fixS = 4, acc = Bucket.UNDER_10,
                outcomes = listOf(
                    Outcome("Contact-A", OutcomeKind.SENT),
                    Outcome("Contact-B", OutcomeKind.NOT_MOVED, Bucket.M50_100, 200),
                )
            ),
            EventEntry(62_000, EventType.FIX_FAIL, reason = Reason.TIMEOUT),
        )
        val expected = listOf(
            "PhoneTrack event log",
            "Recorded on this phone only. No phone numbers, locations or message text are included; senders appear as Contact-A, Contact-B, ...",
            "App 1.1 · Android API 35 · keeps the last 48 hours (up to 300 events)",
            "Times: UTC · first 1970-01-01 00:00:00 · last 1970-01-01 00:01:02",
            "",
            "01-01 00:01:02  no location fix (timeout)",
            "01-01 00:01:01  tick (late 2s) · 2 subs, 1 due · Contact-A sent · Contact-B skipped: moved 50-100m, needs 200m · fix 4s, <10m",
            "01-01 00:00:01  Contact-A subscribed: every 15 min, only if moved 200 m or more, for 4 h",
            "01-01 00:00:00  service started (requested)",
        ).joinToString("\n")
        assertEquals(expected, EventLogLogic.render(log, "1.1", 35, ZoneOffset.UTC))
    }

    @Test fun `the other event kinds read plainly`() {
        val log = listOf(
            EventEntry(1_000, EventType.SERVICE_STOP, reason = Reason.NO_SUBS),
            EventEntry(2_000, EventType.SERVICE_STOP),
            EventEntry(3_000, EventType.BOOT_RESUME, n = 2),
            EventEntry(4_000, EventType.BOOT_RESUME, n = 2, reason = Reason.CANNOT_START),
            EventEntry(5_000, EventType.REPLACED, who = "Contact-A", freqMin = 1, distM = 0, hours = 1),
            EventEntry(6_000, EventType.EXPIRED, who = "Contact-A"),
            EventEntry(7_000, EventType.CANCELLED, who = "Contact-A"),
            EventEntry(8_000, EventType.SUBSCRIBE_REJECTED, reason = Reason.LIMIT),
            EventEntry(9_000, EventType.SEND_FAIL, who = "Contact-B", parts = 2, err = "SecurityException"),
            EventEntry(
                10_000, EventType.ENV,
                env = EnvInfo(true, true, false, true, false, "Samsung SM-S918B")
            ),
        )
        val text = EventLogLogic.render(log, "1.1", 35, ZoneOffset.UTC)
        for (line in listOf(
            "service stopped (no-subs)",
            "service stopped\n",
            "boot: resumed 2 subscription(s)",
            "boot: could not resume 2 subscription(s) (cannot-start)",
            "Contact-A replaced subscription: every 1 min, regardless of movement, for 1 h",
            "Contact-A subscription expired",
            "Contact-A unsubscribed",
            "subscribe rejected (limit)",
            "Contact-B send failed (SecurityException, 2 part(s))",
            "env: fine-location yes, background-location yes, notifications no, location-services yes, " +
                "battery-unrestricted no, device Samsung SM-S918B",
        )) {
            assertTrue("missing '$line' in:\n$text", text.contains(line))
        }
    }

    @Test fun `an empty log renders the header and says so`() {
        val text = EventLogLogic.render(emptyList(), "1.1", 35, ZoneOffset.UTC)
        assertTrue(text.startsWith("PhoneTrack event log\n"))
        assertTrue(text.endsWith("No events recorded."))
    }

    // --- privacy ----------------------------------------------------------------------------

    @Test fun `a full tick never puts a number or a coordinate in the log`() {
        aliases.clear()
        val number = "+15551234567"
        val report = TickReport(startedAt = 2_000, expectedAt = 1_000, aliasOf = ::aliasOf)
        report.subs = 1; report.due = 1
        report.fix(seconds = 3, accuracyM = 8f)
        report.notMoved(number, movedMeters = 12.34f, needM = 200)
        val entries = listOf(
            report.finish(now = 3_000)!!,
            EventEntry(4_000, EventType.SUBSCRIBED, who = aliasOf(number), freqMin = 15, distM = 200, hours = 4),
        )
        val everything = EventLogLogic.render(entries, "1.1", 35, ZoneOffset.UTC) + entries.toString()
        for (secret in listOf("5551234567", "15551234567", "37.77", "-122.41", "12.34")) {
            assertFalse("'$secret' leaked into: $everything", everything.contains(secret))
        }
        assertTrue(everything.contains("Contact-A"))
    }
}
