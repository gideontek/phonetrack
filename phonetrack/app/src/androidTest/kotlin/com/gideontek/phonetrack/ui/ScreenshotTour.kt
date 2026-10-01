package com.gideontek.phonetrack.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.gideontek.phonetrack.support.RichData
import com.gideontek.phonetrack.support.Shell
import com.gideontek.phonetrack.support.UiScenario
import com.gideontek.phonetrack.support.clickVisible
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Saves screenshots of Main and Settings (top, scrolled, and with Reply contents open) in light and dark at 1.0x and 1.3x text
 * to `/sdcard/phonetrack-screens/` (`run-e2e.sh` pulls them into `build/e2e-report/screens/`), for a person to look through before a release.
 * Nothing is compared: the test only fails if the screens cannot be shown.
 */
class ScreenshotTour {
    @get:Rule
    val ui = UiScenario()
    private val compose get() = ui.compose

    private fun save(name: String) {
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().getExternalFilesDir(null), "screens")
        dir.mkdirs()
        val file = File(dir, "$name.png")
        FileOutputStream(file).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Gradle uninstalls the app after the run, taking its files with it: keep a copy on shared storage.
        Shell.run("mkdir -p $SHARED")
        Shell.run("cp ${file.absolutePath} $SHARED/$name.png")
    }

    private companion object {
        const val SHARED = "/sdcard/phonetrack-screens"
    }

    private fun tour(tag: String, dark: Boolean, scale: Float) {
        ui.setDarkMode(dark)
        ui.setFontScale(scale)
        ui.launch { RichData.seed() }
        save("main-$tag-1")
        compose.onNodeWithText("Approved and blocked numbers").clickVisible()
        compose.onNodeWithText("2 approved", substring = true).performScrollTo()
        compose.waitForIdle()
        save("main-$tag-2")
        compose.onNodeWithContentDescription("Settings").clickVisible()
        compose.onNodeWithText("General").performScrollTo()
        save("settings-$tag-1")
        compose.onNodeWithText("Reply contents").clickVisible()
        compose.onNodeWithText("Preview").performScrollTo()
        compose.waitForIdle()
        save("settings-$tag-2")
        compose.onNodeWithText("About").performScrollTo()
        compose.waitForIdle()
        save("settings-$tag-3")
    }

    @Test fun lightNormal() = tour("light-1.0x", dark = false, scale = 1.0f)
    @Test fun darkNormal() = tour("dark-1.0x", dark = true, scale = 1.0f)
    @Test fun lightLargeText() = tour("light-1.3x", dark = false, scale = 1.3f)
    @Test fun darkLargeText() = tour("dark-1.3x", dark = true, scale = 1.3f)
}
