package com.gideontek.phonetrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings most owners never need. Today that is how the map link in a reply is written; the samples
 * underneath are built by the same [SmsComposer] that composes real replies, so what is shown is what is sent.
 * Controls are disabled while settings are PIN-locked, like everywhere else. Back returns to Settings.
 */
@Composable
fun AdvancedScreen(
    vm: HomeViewModel,
    pinDialogs: PinDialogState,
    onBack: () -> Unit,
) {
    val isLocked by vm.isLocked.collectAsState()
    val options by vm.replyOptions.collectAsState()
    val link = options.link.normalized()
    val setLink = { new: MapLinkOptions -> vm.setReplyOptions(options.copy(link = new)) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title row: [back][Advanced]----[lock]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text("Advanced", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.weight(1f))
                LockButton(vm, pinDialogs, onLocked = onBack)
            }

            SettingsSection("Map link") {
                if (!options.osm) {
                    Text(
                        "The map link is switched off under Replies, so these settings don't change anything yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                    RowDivider()
                }
                ToggleRow(
                    label = "Short address (osm.org)",
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
                        "${MapLinkSummary.precision(link.zoom)} at zoom ${link.zoom} (more zoomed out, less precise).",
                    checked = link.encoded,
                    enabled = !isLocked,
                    onCheckedChange = { setLink(link.copy(encoded = it)) }
                )
                RowDivider()
                StepperRow(
                    label = "Map zoom",
                    supporting = "Higher is closer: 8 is a region, 12 a district, 16 a street. Zoom 13 and 16 give " +
                        "the shortest compact codes.",
                    valueText = link.zoom.toString(),
                    lessDescription = "Zoom out",
                    moreDescription = "Zoom in",
                    lessEnabled = !isLocked && link.zoom > MapLinkOptions.MIN_ZOOM,
                    moreEnabled = !isLocked && link.zoom < MapLinkOptions.MAX_ZOOM,
                    onLess = { setLink(link.copy(zoom = link.zoom - 1)) },
                    onMore = { setLink(link.copy(zoom = link.zoom + 1)) }
                )
                RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { setLink(MapLinkOptions.DEFAULT) },
                        enabled = !isLocked && link != MapLinkOptions.DEFAULT
                    ) { Text("Reset to defaults") }
                }
            }

            val shown = options.normalized()
            SettingsSection("Samples") {
                Text(
                    "Built with your reply contents and the settings above, for a made-up fix in San Francisco.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
                SampleBlock(
                    title = "Location reply",
                    messages = SmsComposer.composeLocation(SAMPLE_FIX, shown)
                )
                RowDivider()
                SampleBlock(
                    title = "Subscription update (moved 395 m north-east)",
                    messages = SmsComposer.composeLocation(SAMPLE_FIX, shown, SAMPLE_PREV_LAT, SAMPLE_PREV_LON)
                )
                Text(
                    "Updates after the first include the movement arrow. Arrows aren't in the standard 160-character " +
                        "alphabet, so that text drops to 70 characters per SMS, and a link that doesn't fit goes in a " +
                        "second message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}

/** One sample: how many SMS it is, then each message with its encoding and length. */
@Composable
private fun SampleBlock(title: String, messages: List<String>) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        val total = messages.sumOf { SmsLength.info(it).smsCount }
        Text(
            "$total SMS in total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        messages.forEachIndexed { index, text ->
            val info = SmsLength.info(text)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("SMS ${index + 1} of ${messages.size}", style = MaterialTheme.typography.labelSmall)
                    Text(text, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${if (info.encoding == SmsLength.Encoding.GSM7) "GSM-7" else "UCS-2"} · " +
                            "${info.units} of ${info.limit} characters" +
                            if (info.smsCount > 1) " · sent as ${info.smsCount} parts" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
