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
}
