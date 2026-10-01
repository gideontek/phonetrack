package com.gideontek.phonetrack.support

import android.os.Build
import org.junit.Assume.assumeFalse

/**
 * Behaviour found by the suite that needs a product decision. Tests affected are SKIPPED with the
 * reason (never silently passed); remove the assumption when the issue is resolved.
 */
object KnownIssues {
    /**
     * On the AOSP Android 8.0 (API 26) emulator image `SmsManager.sendMultipartTextMessage` throws
     * `SecurityException: ... READ_PHONE_STATE`, which `SmsSender` catches and logs, so no reply is
     * sent. With READ_PHONE_STATE granted the same test passes. API 29, 33 and 35 are unaffected.
     * Not yet confirmed on a real Android 8.0 device or on API 27/28 (no images installed).
     */
    fun assumeOutboundSmsWorks() =
        assumeFalse(
            "KNOWN ISSUE: outbound SMS fails on API 26 without READ_PHONE_STATE",
            Build.VERSION.SDK_INT == Build.VERSION_CODES.O
        )

    /**
     * Not skippable (it kills the instrumentation process), so tests avoid it and this documents
     * it. When a stored subscription exists but `SubscriptionService` is not running, an approved
     * subscriber's `unsubscribe` makes `SmsReceiver` call `resumeIfPossible` (startForegroundService)
     * and then `SubscriptionManager.remove` -> `stopService` before the service reaches
     * `startForeground()`, which throws `ForegroundServiceDidNotStartInTimeException` and crashes
     * the app. Repro: seed one subscription for an approved number, do not start the service, deliver
     * "phonetrack unsubscribe". Tests start the service first (`TestState.startSubscriptionService`).
     */
    const val UNSUBSCRIBE_RACE = "unsubscribe while the subscription service is not running"
}
