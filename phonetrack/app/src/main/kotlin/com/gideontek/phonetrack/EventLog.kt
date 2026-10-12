package com.gideontek.phonetrack

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

/**
 * What the rest of the app calls to record an event. Every function returns at once while the log
 * is off (one prefs read; [tickReport] and [recordTick] still build a small in-memory report) and none can throw: a logging bug must never stop an update being sent.
 * The typed parameters are the privacy rule: nothing here accepts a number, a location or text
 * other than a class name, and numbers only ever reach the store as aliases.
 */
object EventLog {

    @Volatile private var lastEnv: EnvInfo? = null

    fun isEnabled(ctx: Context): Boolean =
        try { EventLogStore.isEnabled(ctx) } catch (_: Exception) { false }

    /** Turns the log on or off. Off deletes it; on starts with an environment snapshot. */
    fun setEnabled(ctx: Context, on: Boolean) {
        EventLogStore.setEnabled(ctx, on)
        lastEnv = null
        if (on) env(ctx, force = true)
    }

    /** Empties the log and starts it again with an environment snapshot. No-op while the log is off. */
    fun clear(ctx: Context) {
        try {
            if (!EventLogStore.isEnabled(ctx)) return
            EventLogStore.clear(ctx)
            lastEnv = null
            env(ctx, force = true)
        } catch (_: Exception) {
            // Deliberately ignored: see the class comment.
        }
    }

    private fun alias(ctx: Context, number: String): String =
        try { EventLogStore.alias(ctx, PhoneNumber.rateKey(number)) } catch (_: Exception) { "" }

    private inline fun log(ctx: Context, durable: Boolean = false, build: () -> EventEntry) {
        try {
            if (!EventLogStore.isEnabled(ctx)) return
            EventLogStore.append(ctx, build(), durable)
        } catch (_: Exception) {
            // Deliberately ignored: see the class comment.
        }
    }

    private fun now() = System.currentTimeMillis()

    // --- service and ticks -------------------------------------------------------------------

    /** A report for one tick; [expectedAt] is when the tick was scheduled to run. */
    fun tickReport(ctx: Context, expectedAt: Long, startedAt: Long = now()): TickReport =
        TickReport(startedAt, expectedAt) { n -> if (isEnabled(ctx)) alias(ctx, n) else "" }

    fun recordTick(ctx: Context, report: TickReport) {
        try {
            val entry = report.finish(now()) ?: return
            log(ctx) { entry }
        } catch (_: Exception) {
            // Deliberately ignored: see the class comment.
        }
    }

    fun serviceStarted(ctx: Context, reason: Reason) {
        log(ctx) { EventEntry(now(), EventType.SERVICE_START, reason = reason) }
        env(ctx, force = true)
    }

    fun serviceStopped(ctx: Context, reason: Reason?) =
        log(ctx, durable = true) { EventEntry(now(), EventType.SERVICE_STOP, reason = reason) }

    fun bootResume(ctx: Context, count: Int, reason: Reason? = null) =
        log(ctx, durable = true) { EventEntry(now(), EventType.BOOT_RESUME, n = count, reason = reason) }

    fun fixFailed(ctx: Context, reason: Reason) =
        log(ctx, durable = true) { EventEntry(now(), EventType.FIX_FAIL, reason = reason) }

    // --- subscriptions -----------------------------------------------------------------------

    /** A subscription was created, or [replaced] an existing one for the same number. */
    fun subscribed(ctx: Context, number: String, sub: Subscription, replaced: Boolean) = log(ctx) {
        EventEntry(
            now(), if (replaced) EventType.REPLACED else EventType.SUBSCRIBED, who = alias(ctx, number),
            freqMin = sub.freqMinutes, distM = sub.distMeters, hours = sub.durationHours
        )
    }

    fun expired(ctx: Context, number: String) =
        log(ctx) { EventEntry(now(), EventType.EXPIRED, who = alias(ctx, number)) }

    fun cancelled(ctx: Context, number: String) =
        log(ctx) { EventEntry(now(), EventType.CANCELLED, who = alias(ctx, number)) }

    fun subscribeRejected(ctx: Context, reason: Reason) =
        log(ctx) { EventEntry(now(), EventType.SUBSCRIBE_REJECTED, reason = reason) }

    // --- sending -----------------------------------------------------------------------------

    /** Only the exception's class name is kept: its message can contain the number. */
    fun sendFailed(ctx: Context, number: String, parts: Int, error: Exception) =
        log(ctx, durable = true) {
            EventEntry(
                now(), EventType.SEND_FAIL, who = alias(ctx, number), parts = parts,
                err = EventLogLogic.safeText(error.javaClass.simpleName, 60)
            )
        }

    // --- environment -------------------------------------------------------------------------

    /** Records the permission and device state, if it differs from the last snapshot (or [force]). */
    fun env(ctx: Context, force: Boolean = false) {
        try {
            if (!EventLogStore.isEnabled(ctx)) return
            val current = readEnv(ctx)
            if (!force && current == lastEnv) return
            lastEnv = current
            EventLogStore.append(ctx, EventEntry(now(), EventType.ENV, env = current), durable = false)
        } catch (_: Exception) {
            // Deliberately ignored: see the class comment.
        }
    }

    private fun readEnv(ctx: Context) = EnvInfo(
        fineLocation = LocationPermission.hasFine(ctx),
        backgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED,
        notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
        locationServices = isLocationServicesEnabled(ctx),
        batteryUnrestricted = ctx.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false,
        device = EventLogLogic.safeText("${Build.MANUFACTURER} ${Build.MODEL}", 40),
    )
}
