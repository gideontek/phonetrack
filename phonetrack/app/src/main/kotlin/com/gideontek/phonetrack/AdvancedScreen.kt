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
 * Settings most owners never need: how the map link in a reply is written, how a subscription update shows
 * movement, and a short note on SMS length. Each sample
 * sits under the options it shows and is built by the same [SmsComposer] that composes real replies, so what is shown is what is sent.
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
    val movement = options.movement
    val shown = options.normalized()
    val setMovement = { new: MovementOptions -> vm.setReplyOptions(options.copy(movement = new)) }

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
                    messages = SmsComposer.composeLocation(SAMPLE_FIX, shown)
                )
            }

            SettingsSection("Subscription updates") {
                ToggleRow(
                    label = "Show movement",
                    supporting = "Adds the direction and distance since the previous update. Switch it " +
                        "off for shorter updates; the map link already shows where the phone is.",
                    checked = movement.show,
                    enabled = !isLocked,
                    onCheckedChange = { setMovement(movement.copy(show = it)) }
                )
                RowDivider()
                ToggleRow(
                    label = "Compass letters instead of arrows",
                    supporting = "Writes the direction as N, NE, E, SE, S, SW, W or NW instead of an arrow (\u21D7). " +
                        "The letters are in the standard alphabet, so an update can use the 160-character limit " +
                        "instead of 70, which keeps more updates to one SMS.",
                    checked = movement.cardinal,
                    enabled = !isLocked && movement.show,
                    onCheckedChange = { setMovement(movement.copy(cardinal = it)) }
                )
                RowDivider()
                SampleBlock(
                    title = if (movement.show) "Subscription update (moved 395 m north-east)" else "Subscription update (movement hidden)",
                    note = when {
                        !movement.show -> "Movement is hidden, so an update is the same as a location reply."
                        movement.cardinal -> "Updates after the first include the direction and distance. Compass " +
                            "letters are in the standard alphabet, so the text keeps the 160-character limit."
                        else -> "Updates after the first include the movement arrow. Arrows aren't in the standard " +
                            "alphabet, so that text drops to 70 characters per SMS, and a link that doesn't fit goes " +
                            "in a second message."
                    },
                    messages = SmsComposer.composeLocation(SAMPLE_FIX, shown, SAMPLE_PREV_LAT, SAMPLE_PREV_LON)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = { setLink(MapLinkOptions.DEFAULT); setMovement(MovementOptions.DEFAULT) },
                    enabled = !isLocked && (link != MapLinkOptions.DEFAULT || movement != MovementOptions.DEFAULT)
                ) { Text("Reset to defaults") }
            }

            SettingsSection("How long a text can be") {
                Text(
                    "A text message is stored in one of two encodings, and each sample above says which one it uses.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
                Text(
                    "GSM-7 is the standard SMS alphabet: unaccented letters, digits and everyday punctuation. One SMS " +
                        "holds 160 characters. The characters [ ] { } \\ ^ ~ | and \u20AC count as 2.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                )
                Text(
                    "UCS-2 is used for the whole text as soon as one character is outside GSM-7, such as an arrow, an " +
                        "emoji or most non-Latin letters. One SMS then holds only 70 characters.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                )
                Text(
                    "A longer text is split into parts of 153 (GSM-7) or 67 (UCS-2) characters each, and each part is " +
                        "billed as one SMS. A reply that needs 2 or 3 parts can take longer to arrive and costs more " +
                        "on plans that count messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}

/** One sample: how many SMS it is, then each message with its encoding and length. */
@Composable
private fun SampleBlock(title: String, note: String, messages: List<String>) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
