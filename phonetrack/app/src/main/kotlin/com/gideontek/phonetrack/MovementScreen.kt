package com.gideontek.phonetrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How a subscription update writes the direction it moved (opened from "Customize" on the movement row in
 * Replies; whether movement is shown at all is the switch there). The sample is built by the real
 * [SmsComposer]. Controls are disabled while settings are PIN-locked. Back returns to Settings.
 */
@Composable
fun MovementScreen(
    vm: HomeViewModel,
    pinDialogs: PinDialogState,
    onBack: () -> Unit,
) {
    val isLocked by vm.isLocked.collectAsState()
    val options by vm.replyOptions.collectAsState()
    val movement = options.movement
    val setMovement = { new: MovementOptions -> vm.setReplyOptions(options.copy(movement = new)) }

    SubScreen(
        title = "Movement in updates",
        onBack = onBack,
        trailing = { LockButton(vm, pinDialogs, onLocked = onBack) }
    ) {
        SettingsSection("Direction") {
            if (!movement.show) {
                Text(
                    "Movement is switched off under Replies, so updates don't show a direction yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                RowDivider()
            }
            ToggleRow(
                label = "Compass letters instead of arrows",
                supporting = "Writes the direction as N, NE, E, SE, S, SW, W or NW instead of an arrow (\u21D7). " +
                    "The letters are in the standard alphabet, so an update can use the 160-character limit " +
                    "instead of 70, which keeps more updates to one SMS.",
                checked = movement.cardinal,
                enabled = !isLocked,
                onCheckedChange = { setMovement(movement.copy(cardinal = it)) }
            )
            RowDivider()
            SampleBlock(
                title = if (movement.show) "Subscription update (moved 395 m north-east)"
                else "Subscription update (movement hidden)",
                note = when {
                    !movement.show -> "Movement is hidden, so an update is the same as a location reply."
                    movement.cardinal -> "Updates after the first include the direction and distance. Compass " +
                        "letters are in the standard alphabet, so the text keeps the 160-character limit."
                    else -> "Updates after the first include the movement arrow. Arrows aren't in the standard " +
                        "alphabet, so that text drops to 70 characters per SMS, and a link that doesn't fit goes " +
                        "in a second message."
                },
                messages = SmsComposer.composeLocation(
                    SAMPLE_FIX, options.normalized(), SAMPLE_PREV_LAT, SAMPLE_PREV_LON
                )
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = { setMovement(movement.copy(cardinal = MovementOptions.DEFAULT.cardinal)) },
                enabled = !isLocked && movement.cardinal != MovementOptions.DEFAULT.cardinal
            ) { Text("Reset to defaults") }
        }
    }
}
