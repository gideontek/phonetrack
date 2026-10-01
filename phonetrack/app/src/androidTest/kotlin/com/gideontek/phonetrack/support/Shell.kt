package com.gideontek.phonetrack.support

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Runs shell commands as the `shell` user through UiAutomation.
 *
 * [run] works on every API level but does not interpret quotes, pipes or redirects (the command
 * is split on spaces and executed directly), so keep arguments free of spaces. [sh] feeds the
 * command to a real `sh` on stdin, which needs API 30+.
 */
object Shell {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    fun run(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .use { it.readBytes().toString(Charsets.UTF_8) }

    fun sh(command: String): String {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { "Shell.sh needs API 30+" }
        val (stdout, stdin) = automation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write((command + "\n").toByteArray()) }
        return ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
