package com.gideontek.phonetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingNotificationLogicTest {
    private val now = 1_000_000_000L
    private val window = SmsLimits.PENDING_ACTION_WINDOW_MS

    @Test fun `a fresh single request offers Block and Approve and send`() {
        assertEquals(
            listOf(PendingButton.BLOCK, PendingButton.APPROVE_SEND),
            PendingNotificationLogic.buttons(1, false, now - 60_000L, now)
        )
    }

    @Test fun `a stale single request offers Block and Approve`() {
        assertEquals(
            listOf(PendingButton.BLOCK, PendingButton.APPROVE),
            PendingNotificationLogic.buttons(1, false, now - window - 1, now)
        )
    }

    @Test fun `several pending numbers get no buttons`() {
        assertEquals(emptyList<PendingButton>(), PendingNotificationLogic.buttons(2, false, now, now))
    }

    @Test fun `no pending numbers get no buttons`() {
        assertEquals(emptyList<PendingButton>(), PendingNotificationLogic.buttons(0, false, now, now))
    }

    @Test fun `a set PIN removes every button`() {
        assertEquals(emptyList<PendingButton>(), PendingNotificationLogic.buttons(1, true, now, now))
    }

    @Test fun `the window is inclusive at its edge`() {
        assertTrue(PendingNotificationLogic.isFresh(now - window, now))
        assertFalse(PendingNotificationLogic.isFresh(now - window - 1, now))
    }

    @Test fun `a request time in the future counts as fresh`() {
        assertTrue(PendingNotificationLogic.isFresh(now + 5_000L, now))
    }

    @Test fun `a repeat re-posts only after the interval`() {
        val interval = SmsLimits.PENDING_REPOST_INTERVAL_MS
        assertFalse(PendingNotificationLogic.shouldRepost(now - interval + 1, now))
        assertTrue(PendingNotificationLogic.shouldRepost(now - interval, now))
    }
}
