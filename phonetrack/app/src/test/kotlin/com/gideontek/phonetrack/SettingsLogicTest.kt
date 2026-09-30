package com.gideontek.phonetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {

    @Test fun rateStepsByOneBelowTen() {
        assertEquals(2, LimitSteps.rateUp(1))
        assertEquals(9, LimitSteps.rateUp(8))
        assertEquals(10, LimitSteps.rateUp(9))
        assertEquals(8, LimitSteps.rateDown(9))
        assertEquals(9, LimitSteps.rateDown(10))
    }

    @Test fun rateStepsByFiveFromTen() {
        assertEquals(15, LimitSteps.rateUp(10))
        assertEquals(25, LimitSteps.rateUp(20))
        assertEquals(10, LimitSteps.rateDown(15))
        assertEquals(15, LimitSteps.rateDown(20))
    }

    @Test fun rateDownFromOffGridValueLandsOnTenNotBelow() {
        assertEquals(10, LimitSteps.rateDown(13))
        assertEquals(18, LimitSteps.rateUp(13))
    }

    @Test fun rateClampsAtBounds() {
        assertEquals(1, LimitSteps.rateDown(1))
        assertEquals(100, LimitSteps.rateUp(100))
        assertEquals(100, LimitSteps.rateUp(98))
        assertEquals(100, LimitSteps.clampRate(5_000))
        assertEquals(1, LimitSteps.clampRate(-3))
    }

    @Test fun rateUpThenDownReturnsOnGrid() {
        for (v in listOf(1, 5, 9, 10, 15, 20, 50, 95)) {
            assertEquals(v, LimitSteps.rateDown(LimitSteps.rateUp(v)))
        }
    }

    @Test fun subscriptionsStepAndClamp() {
        assertEquals(11, LimitSteps.subscriptionsUp(10))
        assertEquals(9, LimitSteps.subscriptionsDown(10))
        assertEquals(1, LimitSteps.subscriptionsDown(1))
        assertEquals(20, LimitSteps.subscriptionsUp(20))
        assertEquals(20, LimitSteps.clampSubscriptions(99))
        assertEquals(1, LimitSteps.clampSubscriptions(0))
    }

    @Test fun summaryForDefault() =
        assertEquals("Coordinates · Accuracy · Battery · Map link · 1\u00A0SMS", ReplySummary.text(ReplyOptions.DEFAULT))

    @Test fun summaryForLinkOnly() =
        assertEquals("Map link · 1\u00A0SMS", ReplySummary.text(ReplyOptions.LINK_ONLY))

    @Test fun summaryForEmptyOptionsShowsWhatIsActuallySent() =
        assertEquals("Map link · 1\u00A0SMS", ReplySummary.text(ReplyOptions()))

    @Test fun summaryForEverythingIsMoreThanOneSms() {
        val all = ReplyOptions(coords = true, accuracy = true, battery = true, time = true, geo = true, osm = true)
        val text = ReplySummary.text(all)
        assertTrue(text, text.startsWith("Coordinates · Accuracy · Battery · Time of fix · geo: link · Map link · "))
        assertTrue(text, !text.endsWith("· 1\u00A0SMS"))
    }

    @Test fun lockoutScheduleText() =
        assertEquals(
            "5 wrong PINs in a row lock unlocking for 1 minute, then 5, 15 and 60 minutes.",
            PinLockout.scheduleText()
        )
}
