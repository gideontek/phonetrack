package com.gideontek.phonetrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A made-up fix used only for the live preview in [ReplySettingsCard]. */
private val SAMPLE_FIX = LocationFix(
    lat = 37.7749,
    lon = -122.4194,
    accuracyM = 5,
    timeMs = 1_760_000_000_000L,
    batteryPct = 85,
    charging = false
)

/**
 * Collapsible "Reply contents" card: a switch per part of a location reply, and a live preview
 * built by the same [SmsComposer] code that composes the real messages. The last switch that is
 * on can't be turned off (a reply can't be empty); everything is disabled while settings are
 * PIN-locked, like the other settings.
 */
@Composable
fun ReplySettingsCard(
    options: ReplyOptions,
    isLocked: Boolean,
    onChange: (ReplyOptions) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val onCount = options.count

    @Composable
    fun Part(label: String, checked: Boolean, update: (Boolean) -> ReplyOptions) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = checked,
                onCheckedChange = { onChange(update(it)) },
                enabled = !isLocked && (!checked || onCount > 1)
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse reply contents" else "Expand reply contents"
                )
                Text(
                    "Reply contents",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "What a location reply contains. At least one must stay on.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Part("Coordinates", options.coords) { options.copy(coords = it) }
                    Part("Accuracy", options.accuracy) { options.copy(accuracy = it) }
                    Part("Battery", options.battery) { options.copy(battery = it) }
                    Part("Time of fix (UTC)", options.time) { options.copy(time = it) }
                    Part("geo: link", options.geo) { options.copy(geo = it) }
                    Part("OpenStreetMap link", options.osm) { options.copy(osm = it) }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Preview", style = MaterialTheme.typography.labelLarge)
                    val messages = SmsComposer.composeLocation(SAMPLE_FIX, options)
                    messages.forEachIndexed { index, text ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                        "Periodic updates also show how far you moved since the last one, and " +
                            "\"last\" shows how old its fix is.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
