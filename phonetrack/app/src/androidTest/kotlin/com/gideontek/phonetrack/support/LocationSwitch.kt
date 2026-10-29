package com.gideontek.phonetrack.support

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue

/** The system location switch, for tests that need location services on or off. */
object LocationSwitch {
    /**
     * Turns the switch and waits for the system itself to report it. Skips the test (an assumption,
     * not a failure) where the device ignores the shell toggle, as API 27 images do.
     */
    fun set(on: Boolean) {
        Shell.run("cmd location set-location-enabled $on")
        Shell.run("settings put secure location_mode ${if (on) 3 else 0}")
        val lm = ApplicationProvider.getApplicationContext<Context>().getSystemService(LocationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        fun reported() = if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        while (reported() != on && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
        assumeTrue("this device did not apply the location setting", reported() == on)
    }
}
