package com.gideontek.phonetrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Round placeholder avatar; contact initials replace the icon when names are supported. */
@Composable
fun NumberAvatar(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null)
        }
    }
}

/**
 * Numbers waiting for a decision. Tapping a row reveals Approve and Block buttons; there is no
 * swipe. A lone pending row starts expanded.
 */
@Composable
fun DecisionsSection(
    pending: List<ApprovalEntry>,
    now: Long,
    onApprove: (String) -> Unit,
    onBlock: (String) -> Unit
) {
    // Numbers whose expanded state the user flipped. A lone row is expanded unless flipped.
    var flipped by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
    ) { mutableStateOf(emptyList<String>()) }
    val loneRow = pending.size == 1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Needs your decision", style = MaterialTheme.typography.titleMedium)
            CountLabel("${pending.size} waiting")
        }
        Text(
            "Tap a number to approve or block it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                for (entry in pending) {
                    val isFlipped = entry.number in flipped
                    PendingRow(
                        entry = entry,
                        now = now,
                        expanded = loneRow != isFlipped,
                        onToggle = {
                            flipped = if (isFlipped) flipped - entry.number else flipped + entry.number
                        },
                        onApprove = { onApprove(entry.number) },
                        onBlock = { onBlock(entry.number) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingRow(
    entry: ApprovalEntry,
    now: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    onApprove: () -> Unit,
    onBlock: () -> Unit
) {
    val firstButton = remember { FocusRequester() }
    // Only a tap moves focus into the buttons; a row that starts expanded must not grab it.
    var focusOnExpand by remember { mutableStateOf(false) }
    LaunchedEffect(focusOnExpand) {
        if (focusOnExpand) {
            firstButton.requestFocus()
            focusOnExpand = false
        }
    }
    val asked = if (entry.lastSeen > 0L) "asked ${RelativeTime.ago(now, entry.lastSeen)}" else "asked earlier"

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "Hide approve and block options"
                                   else "Show approve and block options",
                    role = Role.Button,
                    onClick = {
                        if (!expanded) focusOnExpand = true
                        onToggle()
                    }
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                    customActions = listOf(
                        CustomAccessibilityAction("Approve") { onApprove(); true },
                        CustomAccessibilityAction("Block") { onBlock(); true }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .sizeAtLeast48(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberAvatar()
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.number, style = MaterialTheme.typography.titleMedium)
                Text(
                    asked,
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
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This number texted your keyword. Approving lets it request your location whenever it likes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onBlock,
                        modifier = Modifier.focusRequester(firstButton)
                    ) { Text("Block", color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onApprove) { Text("Approve") }
                }
            }
        }
    }
}

private fun Modifier.sizeAtLeast48(): Modifier =
    this.then(Modifier.defaultMinSize(minHeight = 48.dp))
