package com.gideontek.phonetrack

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Questions owners and requesters ask, troubleshooting first. Read-only, so it has no lock button.
 * Back returns to Settings. Numbers come from [SmsLimits] so an answer can't drift from the rules.
 */
@Composable
fun FaqScreen(onBack: () -> Unit) {
    SubScreen(title = "Troubleshooting & FAQ", onBack = onBack) {
        FaqEntry(
            "Why did my first text get no reply?",
            "A number that texts the keyword for the first time waits for the phone's owner to approve it, and gets " +
                "nothing back until then. The owner sees it under “Needs your decision” on the main " +
                "screen and taps Approve. After that, every text gets a reply."
        )
        FaqEntry(
            "I'm approved but still get no reply. Why?",
            "Check these on the phone being tracked:",
            "• RCS chats are on. Android hands RCS messages only to the default messaging app, so " +
                "PhoneTrack never sees them. In Google Messages, turn off RCS chats (Settings → RCS " +
                "chats), or turn RCS off for that one conversation.",
            "• Listening is off. The switch is on the main screen.",
            "• The keyword is wrong, or the text is longer than ${SmsLimits.MAX_BODY} characters. Such " +
                "texts are ignored. Any other word after the keyword gets the help reply, not a location.",
            "• The number has already sent ${SmsLimits.DEFAULT_RATE_LIMIT_PER_HOUR} commands this hour " +
                "(the default; Settings → Limits changes it). It gets one “Too many requests” " +
                "reply, then nothing until the hour is up.",
            "• The number is blocked."
        )
        FaqEntry(
            "What does “Location permission not granted” mean?",
            "The phone being tracked needs location set to “Allow all the time”, so a text can start " +
                "a reply while the app is closed. Its owner gets a notification and a red Fix banner on the " +
                "main screen, and Settings → Permissions has the Grant buttons. A subscription accepted " +
                "in the meantime starts once the permission is granted."
        )
        FaqEntry(
            "What does “Location unavailable” mean?",
            "“(services disabled)”: location services are off on the phone. It shows a notification " +
                "with a 60-second countdown, and if the owner turns location services back on in that time " +
                "the reply is sent automatically.",
            "“(timeout)”: the phone found no fix in time, which is common indoors or underground. " +
                "Text the keyword and “last” to get the last position the phone already has."
        )
        FaqEntry(
            "What is the difference between a request and “last”?",
            "Texting the keyword asks for a fresh GPS fix. That takes a moment and can fail indoors.",
            "Texting the keyword and “last” replies with the most recent location the phone already " +
                "has, without turning the GPS on. The reply says how long it has been since the last location " +
                "fix, for example “Last known (12m ago)”, so the position may be out of date. If nothing is saved, the phone says so."
        )
        FaqEntry(
            "How do automatic updates work?",
            "Text the keyword and “subscribe” to start them, and “unsubscribe” to stop. Three " +
                "options are available, all optional:",
            "• --dist M: send only if the phone moved at least M metres since the last update " +
                "(${SmsLimits.MIN_DIST}–${SmsLimits.MAX_DIST}, default ${SmsLimits.DEFAULT_DIST}; " +
                "0 sends regardless of movement).",
            "• --freq N: send at most every N minutes " +
                "(${SmsLimits.MIN_FREQ}–${SmsLimits.MAX_FREQ}, default ${SmsLimits.DEFAULT_FREQ}).",
            "• --time H: stop after H hours " +
                "(${SmsLimits.MIN_TIME}–${SmsLimits.MAX_TIME}, default ${SmsLimits.DEFAULT_TIME}).",
            "An option that is unknown, repeated or out of range is rejected with a message, never adjusted. " +
                "Each update shows an arrow and the distance moved since the previous one, such as " +
                "⇗120m. The owner can also cancel a subscription from the main screen."
        )
        FaqEntry(
            "How secure is it?",
            "The phone answers only numbers its owner has approved, and it never sends its location " +
                "unprompted, apart from what the owner chooses to send from the app.",
            "But SMS is not encrypted, and a sender's number can be faked. A determined attacker could send a " +
                "text that appears to come from an approved number, so approve only people you trust.",
            "A PIN (Settings → Security) stops someone else holding the phone from changing settings or " +
                "approvals. Approved numbers still get replies while the app is locked."
        )
    }
}

/** One question as a section titled with the question, its answer paragraphs in the card. */
@Composable
private fun FaqEntry(question: String, vararg paragraphs: String) {
    SettingsSection(question) {
        paragraphs.forEachIndexed { i, text ->
            FaqText(
                text,
                top = if (i == 0) 16 else 12,
                bottom = if (i == paragraphs.lastIndex) 16 else 0
            )
        }
    }
}

@Composable
private fun FaqText(text: String, top: Int, bottom: Int) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, top = top.dp, end = 16.dp, bottom = bottom.dp)
    )
}
