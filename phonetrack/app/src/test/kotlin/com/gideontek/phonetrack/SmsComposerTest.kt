package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SmsComposerTest {

    // -------------------------------------------------------------------------
    // composeOneShotLocation
    // -------------------------------------------------------------------------

    @Test
    fun `composeOneShotLocation returns 3 messages`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertEquals(3, msgs.size)
    }

    @Test
    fun `composeOneShotLocation msg0 contains battery percent`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertTrue(msgs[0].contains("85%"))
    }

    @Test
    fun `composeOneShotLocation msg1 is geo URI`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertTrue(msgs[1].startsWith("geo:"))
    }

    @Test
    fun `composeOneShotLocation msg2 is OSM URL`() {
        val msgs = SmsComposer.composeOneShotLocation(51.5, -0.1, 10, 85)
        assertTrue(msgs[2].contains("openstreetmap.org"))
    }

    // -------------------------------------------------------------------------
    // composeSubscriptionLocation
    // -------------------------------------------------------------------------

    @Test
    fun `composeSubscriptionLocation with prev fix returns 3 messages`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 51.4, -0.1)
        assertEquals(3, msgs.size)
    }

    @Test
    fun `composeSubscriptionLocation with prev fix has delta arrow in msg0`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 51.4, -0.1)
        val arrows = listOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")
        assertTrue(arrows.any { msgs[0].contains(it) })
    }

    @Test
    fun `composeSubscriptionLocation without prev fix has no delta in msg0`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 0.0, 0.0)
        val arrows = listOf("⇑", "⇗", "⇒", "⇘", "⇓", "⇙", "⇐", "⇖")
        assertFalse(arrows.any { msgs[0].contains(it) })
    }

    @Test
    fun `composeSubscriptionLocation msg1 is geo URI`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 0.0, 0.0)
        assertTrue(msgs[1].startsWith("geo:"))
    }

    @Test
    fun `composeSubscriptionLocation msg2 is OSM URL`() {
        val msgs = SmsComposer.composeSubscriptionLocation(51.5, -0.1, 10, 0.0, 0.0)
        assertTrue(msgs[2].contains("openstreetmap.org"))
    }

    // -------------------------------------------------------------------------
    // Single-message composers
    // -------------------------------------------------------------------------

    @Test
    fun `composeSubscriptionExpired returns correct text`() {
        val msgs = SmsComposer.composeSubscriptionExpired()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("subscription has ended"))
    }

    @Test
    fun `composeSubscriptionCancelled returns correct text`() {
        val msgs = SmsComposer.composeSubscriptionCancelled()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("subscription has been cancelled"))
    }

    @Test
    fun `composeUsageHint includes keyword`() {
        val msgs = SmsComposer.composeUsageHint("mytrack")
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("mytrack"))
    }

    @Test
    fun `composePermissionError returns correct text`() {
        val msgs = SmsComposer.composePermissionError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("permission"))
    }

    @Test
    fun `composeServicesDisabledError returns correct text`() {
        val msgs = SmsComposer.composeServicesDisabledError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("services disabled"))
    }

    @Test
    fun `composeTimeoutError returns correct text`() {
        val msgs = SmsComposer.composeTimeoutError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("timeout"))
    }

    @Test
    fun `composeNoProviderError returns correct text`() {
        val msgs = SmsComposer.composeNoProviderError()
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("provider"))
    }
}
