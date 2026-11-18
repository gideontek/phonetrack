package com.gideontek.phonetrack

/**
 * When the low-battery alert fires. Pure: the service keeps the [Step.armed] state between
 * battery readings and feeds each new reading to [step].
 */
object LowBatteryLogic {

    /**
     * [armed] is the state to keep for the next reading (null = forget it, so the next reading
     * only sets the state again); [alert] says to send the alert now.
     */
    data class Step(val armed: Boolean?, val alert: Boolean)

    /**
     * One battery reading. [armed] is the state kept from the previous reading, null before the
     * first one. The first reading only sets the state, so a phone that is already low when the
     * service starts does not alert. After that the alert fires once, when the level is at or
     * below [threshold] and the phone is not charging; it re-arms when the level is above
     * [threshold] again or the phone starts charging. A level outside 0..100 (unknown) never
     * alerts and leaves the state as it was. A [threshold] of 0 (off) never alerts and forgets
     * the state, so turning the alert on while the battery is low does not fire at once.
     */
    fun step(armed: Boolean?, percent: Int, charging: Boolean, threshold: Int): Step {
        if (percent !in 0..100) return Step(armed, alert = false)
        if (threshold <= 0) return Step(armed = null, alert = false)
        val low = percent <= threshold && !charging
        return when {
            armed == null -> Step(armed = !low, alert = false)
            !low -> Step(armed = true, alert = false)
            armed -> Step(armed = false, alert = true)
            else -> Step(armed = false, alert = false)
        }
    }
}
