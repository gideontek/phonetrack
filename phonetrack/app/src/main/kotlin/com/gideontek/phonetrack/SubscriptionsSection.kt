package com.gideontek.phonetrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** People currently receiving periodic location updates from this phone. */
@Composable
fun SubscriptionsSection(
    subscriptions: List<SubscriptionView>,
    locationServicesEnabled: Boolean,
    onSendNow: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Active subscriptions", style = MaterialTheme.typography.titleMedium)
            CountLabel(subscriptions.size.toString())
        }
        for (sub in subscriptions) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NumberAvatar()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.number, style = MaterialTheme.typography.titleMedium)
                            Text(
                                sub.cadence,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { sub.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Time left: ${sub.leftText}" }
                    )
                    Text(
                        "${sub.leftText} ${sub.totalText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onSendNow(sub.number) },
                            enabled = locationServicesEnabled
                        ) { Text("Send now") }
                        TextButton(onClick = { onCancel(sub.number) }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
