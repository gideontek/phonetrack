package com.gideontek.phonetrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Plain-words name of each stream state, shown beside the indicator. */
fun StreamState.label(): String = when (this) {
    StreamState.Inactive -> "Idle"
    StreamState.Sending -> "Sending"
    StreamState.Receiving -> "Receiving"
    StreamState.Both -> "Active"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusCard(
    summary: StatusSummary,
    streamState: StreamState,
    subscriptionCount: Int,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    locationServicesEnabled: Boolean,
    lastReceiveAt: Long,
    now: Long,
    animate: Boolean,
    onFix: (StatusFix) -> Unit
) {
    val detail = buildString {
        append(streamState.label())
        if (subscriptionCount > 0) {
            append(" · $subscriptionCount ${if (subscriptionCount == 1) "subscription" else "subscriptions"} running")
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StreamStatusIndicator(
                    state = streamState,
                    modifier = // Purely visual: the text beside it says the same thing, so screen readers skip it.
                    Modifier.clearAndSetSemantics { },
                    circleRadius = 28.dp,
                    maxWaveRadius = 44.dp,
                    strokeWidth = 2.dp,
                    animate = animate
                ) {
                    Text(
                        text = if (subscriptionCount > 0) subscriptionCount.toString() else "·",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.headline, style = MaterialTheme.typography.titleLarge)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "SMS listening" }
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip("Location services ${if (locationServicesEnabled) "on" else "off"}")
                if (lastReceiveAt > 0L) {
                    InfoChip("Last request ${RelativeTime.ago(now, lastReceiveAt)}")
                }
            }

            if (summary.problem != null && summary.fix != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Text(
                            summary.problem,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { onFix(summary.fix) }) { Text("Fix") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
