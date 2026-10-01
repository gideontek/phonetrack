package com.gideontek.phonetrack.support

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider

/** The app's own notifications as the owner would see them. */
object Notifications {
    data class Posted(val id: Int, val title: String, val text: String)

    fun active(): List<Posted> {
        val nm = ApplicationProvider.getApplicationContext<Context>().getSystemService(NotificationManager::class.java)
        return nm.activeNotifications.map {
            val extras = it.notification.extras
            Posted(
                it.id,
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            )
        }
    }

    fun withId(id: Int): List<Posted> = active().filter { it.id == id }

    /** Waits up to [timeoutMs] for a notification with [id]. */
    fun await(id: Int, timeoutMs: Long = 8_000): Posted? {
        val end = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < end) {
            withId(id).firstOrNull()?.let { return it }
            android.os.SystemClock.sleep(250)
        }
        return null
    }

    /** True if a service of this app with the given class name is running. */
    @Suppress("DEPRECATION")
    fun serviceRunning(className: String): Boolean {
        val am = ApplicationProvider.getApplicationContext<Context>().getSystemService(android.app.ActivityManager::class.java)
        return am.getRunningServices(100).any { it.service.className == className }
    }
}
