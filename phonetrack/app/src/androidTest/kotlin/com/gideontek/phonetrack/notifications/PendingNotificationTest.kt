package com.gideontek.phonetrack.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.ApprovalState
import com.gideontek.phonetrack.ApprovalStore
import com.gideontek.phonetrack.PendingActionReceiver
import com.gideontek.phonetrack.PendingAlerts
import com.gideontek.phonetrack.PendingStaleReceiver
import com.gideontek.phonetrack.PinStore
import com.gideontek.phonetrack.SmsLimits
import com.gideontek.phonetrack.support.Notifications
import com.gideontek.phonetrack.support.Scenario
import com.gideontek.phonetrack.support.TestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The "new number is waiting" notification: when it posts, what it offers, how it ages and clears. */
class PendingNotificationTest {
    @get:Rule
    val rules = Scenario.rules(mockLocation = true)

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val id = PendingAlerts.NOTIFICATION_ID
    private val stranger = "+15550001111"
    private val other = "+15550002222"
    private val now get() = System.currentTimeMillis()
    private val minute = 60_000L

    private fun staleAlarmScheduled(): Boolean = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, PendingStaleReceiver::class.java).setAction(PendingAlerts.ACTION_STALE),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    ) != null

    private fun state(number: String) = ApprovalStore.stateFor(ctx, number)

    @Test
    fun anUnknownNumberPostsOneNotificationWithTheFreshButtons() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        val n = Notifications.await(id)!!
        assertEquals("New location request", n.title)
        assertEquals("New location request from $stranger. Tap to review", n.text)
        assertEquals(listOf("Block", "Approve & send"), n.actions)
        assertEquals(SmsLimits.PENDING_NOTIFICATION_TIMEOUT_MS, n.timeoutMs)
        assertTrue("a fresh version schedules the stale alarm", staleAlarmScheduled())
    }

    @Test
    fun theLockScreenVersionHidesTheNumberAndHasNoButtons() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        val n = Notifications.raw(id)!!.notification
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("New location request", n.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(n.publicVersion.actions)
    }

    @Test
    fun aRepeatInsideAMinuteChangesNothing() {
        TestState.enableListening()
        val s = Scenario()
        s.send("phonetrack", from = stranger)
        val first = Notifications.await(id)!!
        repeat(2) { s.send("phonetrack", from = stranger) }
        assertEquals(first.requestedAt, Notifications.withId(id).single().requestedAt)
    }

    @Test
    fun aRepeatAfterAMinuteRepostsQuietlyWithAFreshTime() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        val later = now + 61_000L
        PendingAlerts.onContact(ctx, isNew = false, now = later)
        val n = Notifications.withId(id).single()
        assertEquals(later, n.requestedAt)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test
    fun aDismissedNotificationIsNotBroughtBackByARepeat() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
        PendingAlerts.onContact(ctx, isNew = false, now = now + 61_000L)
        assertTrue(Notifications.withId(id).isEmpty())
    }

    @Test
    fun aSecondNumberTurnsItIntoACountWithoutButtons() {
        TestState.enableListening()
        val s = Scenario()
        s.send("phonetrack", from = stranger)
        Notifications.await(id)
        s.send("phonetrack", from = other)
        val n = Notifications.withId(id).single()
        assertEquals("Location requests waiting", n.title)
        assertEquals("2 numbers waiting for approval", n.text)
        assertEquals(emptyList<String>(), n.actions)
        assertFalse(staleAlarmScheduled())
    }

    @Test
    fun decidingEveryNumberClearsIt() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        ApprovalStore.setState(ctx, stranger, ApprovalState.APPROVED)
        PendingAlerts.refresh(ctx)
        assertTrue(Notifications.awaitGone(id))
        assertFalse(staleAlarmScheduled())
    }

    @Test
    fun theNumberLeftAfterOneIsDecidedGetsStaleButtonsFromItsLastSeen() {
        TestState.enableListening()
        TestState.seedApprovals(Triple(stranger, "PENDING", now - 30 * minute))
        Scenario().send("phonetrack", from = other)
        Notifications.await(id)
        ApprovalStore.setState(ctx, other, ApprovalState.BLOCKED)
        PendingAlerts.refresh(ctx)
        val n = Notifications.withId(id).single()
        assertTrue(n.text, n.text.contains(stranger))
        assertEquals(listOf("Block", "Approve"), n.actions)
    }

    @Test
    fun theButtonsGoStaleOnceTheWindowHasPassed() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        assertEquals(listOf("Block", "Approve & send"), Notifications.await(id)!!.actions)
        PendingAlerts.refresh(ctx, now = now + SmsLimits.PENDING_ACTION_WINDOW_MS + minute)
        assertEquals(listOf("Block", "Approve"), Notifications.withId(id).single().actions)
        assertFalse("the stale version cancels the alarm", staleAlarmScheduled())
    }

    @Test
    fun aRequestThatIsAlreadyStaleStartsWithStaleButtons() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        PendingAlerts.onContact(ctx, isNew = true, requestedAt = now - 11 * minute)
        assertEquals(listOf("Block", "Approve"), Notifications.withId(id).single().actions)
    }

    @Test
    fun aPinRemovesTheButtons() {
        PinStore.set(ctx, "1234")
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        assertEquals(emptyList<String>(), Notifications.await(id)!!.actions)
    }

    @Test
    fun blockBlocksTheNumberAndClearsTheNotification() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        Notifications.press(id, "Block")
        assertTrue(Notifications.awaitGone(id))
        assertEquals(ApprovalState.BLOCKED, state(stranger))
    }

    @Test
    fun approveOnAStaleRequestApprovesWithoutReplying() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        PendingAlerts.onContact(ctx, isNew = true, requestedAt = now - 11 * minute)
        val sentBefore = TestState.lastSendAt()
        Notifications.press(id, "Approve")
        assertTrue(Notifications.awaitGone(id))
        assertEquals(ApprovalState.APPROVED, state(stranger))
        assertEquals("approving a stale request texts nobody", sentBefore, TestState.lastSendAt())
    }

    @Test
    fun approveAndSendOnAFreshRequestRepliesWithALocation() {
        val s = Scenario()
        TestState.enableListening()
        s.send("phonetrack") // the own number is unknown, so it becomes pending and its reply is observable
        Notifications.await(id)
        Notifications.press(id, "Approve & send")
        assertTrue(Notifications.awaitGone(id))
        assertEquals(ApprovalState.APPROVED, state(s.me))
        val reply = s.replies().single()
        assertTrue(reply, reply.contains("openstreetmap") || reply.contains("osm.org"))
    }

    @Test
    fun approveAndSendAfterTheWindowOnlyApproves() {
        val s = Scenario()
        TestState.enableListening()
        s.send("phonetrack")
        Notifications.await(id)
        val sentBefore = TestState.lastSendAt()
        // Doze delayed the stale alarm: the fresh "Approve & send" is still on screen, but its request is old.
        ctx.sendBroadcast(
            Intent(ctx, PendingActionReceiver::class.java)
                .setAction(PendingAlerts.ACTION_APPROVE_SEND)
                .putExtra(PendingAlerts.EXTRA_NUMBER, s.me)
                .putExtra(PendingAlerts.EXTRA_REQUESTED_AT, now - 11 * minute)
        )
        assertTrue(Notifications.awaitGone(id))
        assertEquals(ApprovalState.APPROVED, state(s.me))
        s.assertSilent()
        assertEquals(sentBefore, TestState.lastSendAt())
    }

    @Test
    fun aButtonPressedAfterTheNumberWasDecidedElsewhereChangesNothing() {
        TestState.enableListening()
        Scenario().send("phonetrack", from = stranger)
        Notifications.await(id)
        ApprovalStore.setState(ctx, stranger, ApprovalState.APPROVED) // decided in the app
        Notifications.press(id, "Block")
        assertTrue(Notifications.awaitGone(id))
        assertEquals(ApprovalState.APPROVED, state(stranger))
    }
}
