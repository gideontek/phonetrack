package com.gideontek.phonetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LowBatteryLogicTest {

    private val t = 5

    /** Feeds readings (percent, charging) through [LowBatteryLogic.step]; returns the alert flags. */
    private fun run(vararg readings: Pair<Int, Boolean>, threshold: Int = t): List<Boolean> {
        var armed: Boolean? = null
        return readings.map { (percent, charging) ->
            val s = LowBatteryLogic.step(armed, percent, charging, threshold)
            armed = s.armed
            s.alert
        }
    }

    @Test fun firstReadingNeverAlerts() {
        assertEquals(listOf(false), run(3 to false))
        assertEquals(listOf(false), run(80 to false))
    }

    @Test fun alertsOnceWhenCrossingThreshold() {
        assertEquals(listOf(false, false, true, false, false), run(50 to false, 6 to false, 5 to false, 4 to false, 3 to false))
    }

    @Test fun alertsAtExactlyTheThreshold() {
        assertEquals(listOf(false, true), run(6 to false, 5 to false))
    }

    @Test fun reArmsWhenLevelRisesAboveThreshold() {
        assertEquals(
            listOf(false, true, false, true),
            run(50 to false, 4 to false, 20 to false, 4 to false)
        )
    }

    @Test fun staysSilentWhileCharging() {
        assertEquals(listOf(false, false, false), run(50 to false, 4 to true, 3 to true))
    }

    @Test fun reArmsWhenChargingStarts() {
        // Low and unplugged alerts, then plugged in at 4% re-arms, then unplugged still at 4% alerts again.
        assertEquals(
            listOf(false, true, false, true),
            run(50 to false, 4 to false, 4 to true, 4 to false)
        )
    }

    @Test fun startingLowDoesNotAlertButAlertsAfterARecharge() {
        assertEquals(listOf(false, false, false, true), run(3 to false, 2 to false, 50 to false, 4 to false))
    }

    @Test fun unknownLevelIsIgnoredAndKeepsState() {
        assertEquals(listOf(false, false, true), run(50 to false, -1 to false, 4 to false))
        val s = LowBatteryLogic.step(true, 101, false, t)
        assertEquals(true, s.armed)
        assertFalse(s.alert)
    }

    @Test fun thresholdZeroIsOff() {
        assertEquals(listOf(false, false, false), run(50 to false, 1 to false, 0 to false, threshold = 0))
    }

    @Test fun turningItOnWhileLowDoesNotAlertImmediately() {
        // Off at 3%: the state is forgotten, so the first reading once it is on only sets the state.
        val off = LowBatteryLogic.step(true, 3, false, 0)
        assertNull(off.armed)
        val s = LowBatteryLogic.step(off.armed, 3, false, 5)
        assertFalse(s.alert)
        assertEquals(false, s.armed)
    }
}
