package com.gideontek.phonetrack.support

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider

/** The app's own notifications as the owner would see them. */
object Notifications {
    data class Posted(
        val id: Int,
        val title: String,
        val text: String,
        /** Titles of the action buttons, in order. */
        val actions: List<String> = emptyList(),
        val flags: Int = 0,
        val timeoutMs: Long = 0,
        /** The pending-request notification's latest-attempt time (extra `requestedAt`), 0 if absent. */
        val requestedAt: Long = 0
    )

    private fun manager() =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(NotificationManager::class.java)

    fun active(): List<Posted> = manager().activeNotifications.map {
        val extras = it.notification.extras
        Posted(
            it.id,
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            it.notification.actions?.map { a -> a.title.toString() }.orEmpty(),
            it.notification.flags,
            it.notification.timeoutAfter,
            extras.getLong("requestedAt")
        )
    }

    fun withId(id: Int): List<Posted> = active().filter { it.id == id }

    /** The raw notification, for its content intent, public version and visibility. */
    fun raw(id: Int): StatusBarNotification? = manager().activeNotifications.firstOrNull { it.id == id }

    /** Taps the notification body. */
    fun tap(id: Int) {
        raw(id)!!.notification.contentIntent.send()
    }

    /** Presses the action button titled [title]. */
    fun press(id: Int, title: String) {
        raw(id)!!.notification.actions.first { it.title.toString() == title }.actionIntent.send()
    }

    /** Waits up to [timeoutMs] for no notification with [id] to remain. */
    fun awaitGone(id: Int, timeoutMs: Long = 8_000): Boolean {
        val end = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < end) {
            if (withId(id).isEmpty()) return true
            android.os.SystemClock.sleep(250)
        }
        return false
    }

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
