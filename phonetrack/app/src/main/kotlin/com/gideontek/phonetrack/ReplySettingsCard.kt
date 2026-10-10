package com.gideontek.phonetrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp


/**
 * The "Replies" section: one row summarising what a location reply contains; tapping it reveals a
 * switch per part and a live preview built by the same [SmsComposer] code that composes the real
 * messages. The last switch that is on can't be turned off (a reply can't be empty); everything
 * is disabled while settings are PIN-locked.
 */
@Composable
fun ReplySettingsCard(
    options: ReplyOptions,
    isLocked: Boolean,
    onChange: (ReplyOptions) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val locationCount = options.locationCount

    @Composable
    fun Part(label: String, checked: Boolean, isLocation: Boolean, update: (Boolean) -> ReplyOptions) {
        ToggleRow(
            label = label,
            supporting = null,
            checked = checked,
            // The last of Coordinates / geo: link / OpenStreetMap link must stay on.
            enabled = !isLocked && (!checked || !isLocation || locationCount > 1),
            onCheckedChange = { onChange(update(it)) }
        )
    }

    SettingsSection("Replies") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "Hide reply contents" else "Show reply contents",
                    role = Role.Button,
                    onClick = { expanded = !expanded }
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .defaultMinSize(minHeight = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reply contents", style = MaterialTheme.typography.bodyLarge)
                Text(
                    ReplySummary.text(options),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }

        if (expanded) {
            RowDivider()
            Part("Coordinates", options.coords, isLocation = true) { options.copy(coords = it) }
            Part("Accuracy", options.accuracy, isLocation = false) { options.copy(accuracy = it) }
            Part("Battery", options.battery, isLocation = false) { options.copy(battery = it) }
            Part("Time of fix (UTC)", options.time, isLocation = false) { options.copy(time = it) }
            Part("geo: link", options.geo, isLocation = true) { options.copy(geo = it) }
            Part("OpenStreetMap link", options.osm, isLocation = true) { options.copy(osm = it) }
            RowDivider()
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Preview", style = MaterialTheme.typography.labelLarge)
                val messages = SmsComposer.composeLocation(SAMPLE_FIX, options.normalized())
                messages.forEachIndexed { index, text ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "SMS ${index + 1} of ${messages.size}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(
                    "At least one of Coordinates, geo: link and OpenStreetMap link must stay on. Subscription updates also show how far you moved " +
                        "since the last one, and \"last\" shows how old its fix is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
