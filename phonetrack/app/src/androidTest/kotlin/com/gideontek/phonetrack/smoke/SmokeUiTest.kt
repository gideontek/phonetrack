package com.gideontek.phonetrack.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gideontek.phonetrack.MainActivity
import com.gideontek.phonetrack.support.ResetStateRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Layer 3 smoke: the real activity renders Main, opens Settings and comes back. */
class SmokeUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ResetStateRule()).around(compose)

    @Test
    fun mainOpensSettingsAndBackReturns() {
        compose.onNodeWithText("PhoneTrack").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("General").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
