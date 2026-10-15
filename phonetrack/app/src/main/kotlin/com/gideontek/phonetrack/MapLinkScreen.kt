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
 * How the OpenStreetMap link in a reply is written (opened from "Customize" on the OpenStreetMap link in
 * Replies). The sample is built by the same [SmsComposer] that composes real replies, so what is shown is
 * what is sent. Controls are disabled while settings are PIN-locked. Back returns to Settings.
 */
@Composable
fun MapLinkScreen(
    vm: HomeViewModel,
    pinDialogs: PinDialogState,
    onBack: () -> Unit,
) {
    val isLocked by vm.isLocked.collectAsState()
    val options by vm.replyOptions.collectAsState()
    val link = options.link.normalized()
    val setLink = { new: MapLinkOptions -> vm.setReplyOptions(options.copy(link = new)) }

    SubScreen(
        title = "OpenStreetMap link",
        onBack = onBack,
        trailing = { LockButton(vm, pinDialogs, onLocked = onBack) }
    ) {
        SettingsSection("Link style") {
            if (!options.osm) {
                Text(
                    "The OpenStreetMap link is switched off under Replies, so these settings don't change anything yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                RowDivider()
            }
            ToggleRow(
                label = "Use osm.org instead of openstreetmap.org",
                supporting = "Saves 14 characters. osm.org is OpenStreetMap's own short address and forwards to " +
                    "openstreetmap.org, so the link opens the same map.",
                checked = link.shortDomain,
                enabled = !isLocked,
                onCheckedChange = { setLink(link.copy(shortDomain = it)) }
            )
            RowDivider()
            ToggleRow(
                label = "Compact code instead of coordinates",
                supporting = "Writes the position as a short code (osm.org/go/TZHvSR7N?m), about 50 characters " +
                    "shorter, which can keep a reply to 1 SMS instead of 2 or 3. The map and marker open the same, " +
                    "but the coordinates can't be read from the link, and the pin can be off by up to " +
                    "${MapLinkSummary.precision(link.zoom)} at zoom ${link.zoom} (more zoomed out, less precise). " +
                    "Keep the coordinates if a message may be passed on to a dispatcher or rescue team: they can " +
                    "read out and enter latitude and longitude, but not a code, and a code needs a web browser. " +
                    "The Coordinates and geo: link options under Replies also send the coordinates.",
                checked = link.encoded,
                enabled = !isLocked,
                onCheckedChange = { setLink(link.copy(encoded = it)) }
            )
            RowDivider()
            StepperRow(
                label = "Map zoom level",
                supporting = "Higher is zoomed in. 8 is a region, 12 a district, 16 a street. Levels 10, 13 and 16 " +
                    "give the shortest compact codes.",
                valueText = link.zoom.toString(),
                lessDescription = "Zoom out",
                moreDescription = "Zoom in",
                lessEnabled = !isLocked && link.zoom > MapLinkOptions.MIN_ZOOM,
                moreEnabled = !isLocked && link.zoom < MapLinkOptions.MAX_ZOOM,
                onLess = { setLink(link.copy(zoom = link.zoom - 1)) },
                onMore = { setLink(link.copy(zoom = link.zoom + 1)) }
            )
            RowDivider()
            SampleBlock(
                title = "Location reply",
                note = "Built with your reply contents and the settings above, for a made-up fix in San Francisco.",
                messages = SmsComposer.composeLocation(SAMPLE_FIX, options.normalized())
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = { setLink(MapLinkOptions.DEFAULT) },
                enabled = !isLocked && link != MapLinkOptions.DEFAULT
            ) { Text("Reset to defaults") }
        }
    }
}
