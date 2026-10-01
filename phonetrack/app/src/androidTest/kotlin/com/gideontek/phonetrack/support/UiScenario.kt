package com.gideontek.phonetrack.support

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.gideontek.phonetrack.MainActivity
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The real [MainActivity] under a Compose test rule. Permissions are granted and state is reset
 * first; the test then calls [launch] with whatever it needs seeded, so the activity starts from
 * exactly that state. Display settings a test changes through [Shell] (dark mode, font scale,
 * location mode) are put back afterwards.
 */
class UiScenario(mockLocation: Boolean = false, grant: Boolean = true) : TestRule {
    val compose: ComposeTestRule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private val chain: RuleChain = Scenario.rules(mockLocation, grant = grant).around(compose).around(Cleanup())

    override fun apply(base: Statement, description: Description): Statement = chain.apply(base, description)

    /**
     * Seeds state, sets location services on (or off, for the banner tests) and starts the activity.
     * If the device does not apply the location setting the test is skipped, with that reason.
     */
    fun launch(locationServices: Boolean = true, seed: () -> Unit = {}) {
        seed()
        setLocationServices(locationServices)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /** Turns the system location switch and waits for the system itself to report it. */
    fun setLocationServices(on: Boolean) {
        Shell.run("cmd location set-location-enabled $on")
        Shell.run("settings put secure location_mode ${if (on) 3 else 0}")
        val lm = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.location.LocationManager::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        fun reported() = if (android.os.Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        while (reported() != on && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(200)
        org.junit.Assume.assumeTrue("this device did not apply the location setting", reported() == on)
    }

    fun recreate() {
        scenario!!.recreate()
        compose.waitForIdle()
    }

    fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** True once the activity is no longer in front: finished, or (Android 12+ root activity) moved to the back. */
    val leftForeground: Boolean get() = scenario!!.state < androidx.lifecycle.Lifecycle.State.RESUMED

    fun setDarkMode(on: Boolean) { Shell.run("cmd uimode night ${if (on) "yes" else "no"}") }

    fun setFontScale(scale: Float) { Shell.run("settings put system font_scale $scale") }

    /** Waits (idling the UI) until [condition] holds, e.g. for a stored value to change. */
    fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) = compose.waitUntil(timeoutMs, condition)

    private inner class Cleanup : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } finally {
                    scenario?.close()
                    Shell.run("cmd uimode night auto")
                    Shell.run("settings put system font_scale 1.0")
                    Shell.run("cmd location set-location-enabled true")
                    Shell.run("settings put secure location_mode 3")
                }
            }
        }
    }
}

/** Scrolls the node into view first: Main and Settings are long scrolling columns. */
fun SemanticsNodeInteraction.clickVisible(): SemanticsNodeInteraction = performScrollTo().performClick()

/** How many nodes show [text]; 0 means it is not on screen at all. */
fun ComposeTestRule.countWithText(text: String, substring: Boolean = false): Int =
    onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

/** The application context, for reading stores the UI writes to. */
fun ComposeTestRule.activityContext(): android.content.Context =
    androidx.test.core.app.ApplicationProvider.getApplicationContext()
