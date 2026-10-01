package com.gideontek.phonetrack.support

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeNoException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Makes the GPS provider report a fixed position for the duration of a test, so replies carry
 * known coordinates. A fresh fix is pushed every 300 ms because `requestSingleUpdate` only sees
 * locations reported after it registers. If the device refuses mock locations the test is skipped
 * with the reason rather than passing without having checked anything.
 */
class MockLocationRule(
    private val lat: Double = 37.7749,
    private val lon: Double = -122.4194,
    private val accuracy: Float = 5f,
    /** False: the providers are replaced by empty ones, so no fix is cached (for the "nothing saved" case). */
    private val reportFixes: Boolean = true
) : TestRule {

    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!reportFixes) {
                // Switching location off drops every cached fix, including the passive provider's.
                Shell.run("cmd location set-location-enabled false")
                Shell.run("settings put secure location_mode 0")
                SystemClock.sleep(500)
            }
            Shell.run("appops set ${context.packageName} android:mock_location allow")
            // Location must be on for the provider to be usable; ignore failures on images without the command.
            Shell.run("cmd location set-location-enabled true")
            Shell.run("settings put secure location_mode 3")
            val providers = if (reportFixes) listOf(LocationManager.GPS_PROVIDER)
            else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            val added = mutableListOf<String>()
            try {
                for (name in providers) {
                    try {
                        lm.addTestProvider(name, false, false, false, false, true, true, true,
                            Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
                        lm.setTestProviderEnabled(name, true)
                        added += name
                    } catch (e: SecurityException) {
                        throw e
                    } catch (_: IllegalArgumentException) {
                        // a provider that cannot be mocked (passive on some images) keeps its real state
                    }
                }
            } catch (e: SecurityException) {
                assumeNoException("device refused mock locations; reply content cannot be checked", e)
            }
            val running = java.util.concurrent.atomic.AtomicBoolean(true)
            val pusher = Thread {
                while (running.get()) {
                    try {
                        if (reportFixes) lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix())
                    } catch (_: Exception) {
                        // provider removed while shutting down
                    }
                    SystemClock.sleep(300)
                }
            }.apply { isDaemon = true; start() }
            try {
                base.evaluate()
            } finally {
                running.set(false)
                pusher.join(1_000)
                added.forEach { try { lm.removeTestProvider(it) } catch (_: Exception) {} }
                if (!reportFixes) {
                    Shell.run("cmd location set-location-enabled true")
                    Shell.run("settings put secure location_mode 3")
                }
            }
        }

        private fun fix() = Location(LocationManager.GPS_PROVIDER).also {
            it.latitude = lat
            it.longitude = lon
            it.accuracy = accuracy
            it.time = System.currentTimeMillis()
            it.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }
}
