package com.gideontek.phonetrack

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Questions owners ask about what the app sends. Read-only, so it has no lock button. Back returns to Settings. */
@Composable
fun FaqScreen(onBack: () -> Unit) {
    SubScreen(title = "FAQ", onBack = onBack) {
        SettingsSection("How long can a text be?") {
            FaqText(
                "A text message is stored in one of two encodings. The settings samples say which one a message uses.",
                top = 16
            )
            FaqText(
                "GSM-7 is the standard SMS alphabet: unaccented letters, digits and everyday punctuation. One SMS " +
                    "holds 160 characters. The characters [ ] { } \\ ^ ~ | and \u20AC count as 2."
            )
            FaqText(
                "UCS-2 is used for the whole text as soon as one character is outside GSM-7, such as an arrow, an " +
                    "emoji or most non-Latin letters. One SMS then holds only 70 characters."
            )
            FaqText(
                "A longer text is split into parts of 153 (GSM-7) or 67 (UCS-2) characters each, and each part is " +
                    "billed as one SMS. A reply that needs 2 or 3 parts can take longer to arrive and costs more " +
                    "on plans that count messages.",
                bottom = 16
            )
        }
    }
}

@Composable
private fun FaqText(text: String, top: Int = 12, bottom: Int = 0) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, top = top.dp, end = 16.dp, bottom = bottom.dp)
    )
}
