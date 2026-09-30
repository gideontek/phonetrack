package com.gideontek.phonetrack

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Device state that goes into location replies. */
object DeviceStatus {

    data class Battery(val percent: Int, val charging: Boolean)

    /**
     * Battery percent (-1 if the device doesn't report it) and whether it is charging. Uses the
     * sticky ACTION_BATTERY_CHANGED broadcast, which needs no permission and no registered receiver.
     */
    fun battery(ctx: Context): Battery {
        val manager = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 } ?: -1
        val status = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(percent, charging)
    }
}
