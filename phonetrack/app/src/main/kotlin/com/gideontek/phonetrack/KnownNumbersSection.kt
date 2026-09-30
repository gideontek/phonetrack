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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Approved and blocked numbers, collapsed by default. */
@Composable
fun KnownNumbersSection(
    approved: List<ApprovalEntry>,
    blocked: List<ApprovalEntry>,
    locationServicesEnabled: Boolean,
    onApprove: (String) -> Unit,
    onBlock: (String) -> Unit,
    onSendLocation: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val summary = "${approved.size} approved · ${blocked.size} blocked"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = if (expanded) "Hide approved and blocked numbers"
                                       else "Show approved and blocked numbers",
                        role = Role.Button,
                        onClick = { expanded = !expanded }
                    )
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .defaultMinSize(minHeight = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Approved and blocked numbers", style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary,
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
                for (entry in approved) {
                    HorizontalDivider()
                    KnownRow(entry.number, "Approved") {
                        TextButton(
                            onClick = { onSendLocation(entry.number) },
                            enabled = locationServicesEnabled
                        ) { Text("Send location") }
                        TextButton(onClick = { onBlock(entry.number) }) {
                            Text("Block", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                for (entry in blocked) {
                    HorizontalDivider()
                    KnownRow(entry.number, "Blocked") {
                        TextButton(onClick = { onApprove(entry.number) }) { Text("Approve") }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnownRow(number: String, stateLabel: String, actions: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberAvatar()
            Column {
                Text(number, style = MaterialTheme.typography.titleSmall)
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) { actions() }
    }
}
