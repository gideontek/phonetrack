package com.gideontek.phonetrack.support

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Phase tests only make sense after a host script has set the device up (a revoked permission, a
 * reboot, ...), so they run only when started with `-e phase <name>` (see `scripts/e2e/lib.sh`).
 * In a plain `connectedDebugAndroidTest` they are skipped.
 */
object Phase {
    val current: String? get() = InstrumentationRegistry.getArguments().getString("phase")

    fun require(expected: String) =
        assumeTrue("phase test: run it with -e phase $expected (scripts/e2e)", current == expected)
}
