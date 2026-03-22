package com.gideontek.phonetrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ApprovalsCard(
    approvalsList: List<Pair<String, ApprovalState>>,
    subscriptions: List<Subscription>,
    isLocked: Boolean,
    locationServicesEnabled: Boolean,
    onNumberStateChange: (String, ApprovalState) -> Unit,
    onSendLocation: (String) -> Unit,
    onCancelSubscription: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row (always visible) — click to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse approvals" else "Expand approvals"
                )
                Text(
                    "Approvals",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
            }

            // Expanded body
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (approvalsList.isEmpty()) {
                    Text(
                        "No requests received yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for ((number, state) in approvalsList) {
                            ApprovalRow(
                                number = number,
                                state = state,
                                isLocked = isLocked,
                                locationServicesEnabled = locationServicesEnabled,
                                activeSubscription = subscriptions.find { it.number == number },
                                onStateChange = { newState -> onNumberStateChange(number, newState) },
                                onSendLocation = { onSendLocation(number) },
                                onCancelSubscription = { onCancelSubscription(number) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApprovalRow(
    number: String,
    state: ApprovalState,
    isLocked: Boolean,
    locationServicesEnabled: Boolean,
    activeSubscription: Subscription?,
    onStateChange: (ApprovalState) -> Unit,
    onSendLocation: () -> Unit,
    onCancelSubscription: () -> Unit
) {
    var showSubDialog by remember { mutableStateOf(false) }

    if (showSubDialog && activeSubscription != null) {
        val minutesLeft = ((activeSubscription.expiresAt - System.currentTimeMillis()) / 60_000L)
            .coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { showSubDialog = false },
            title = { Text("Active Subscription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Distance threshold: ${activeSubscription.distMeters} m")
                    Text("Frequency: every ${activeSubscription.freqMinutes} min")
                    Text("Duration: ${activeSubscription.durationHours} hr total")
                    Text("Expires in: $minutesLeft min")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSubDialog = false; onCancelSubscription() },
                    enabled = !isLocked
                ) { Text("Cancel Subscription") }
            },
            dismissButton = {
                TextButton(onClick = { showSubDialog = false }) { Text("Close") }
            }
        )
    }

    // rememberSwipeToDismissBoxState captures the lambda once via rememberSaveable,
    // so we use rememberUpdatedState to always read the latest values inside it.
    val currentState = rememberUpdatedState(state)
    val currentIsLocked = rememberUpdatedState(isLocked)
    val currentOnStateChange = rememberUpdatedState(onStateChange)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (!currentIsLocked.value) {
                when (dismissValue) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        val newState = when (currentState.value) {
                            ApprovalState.PENDING  -> ApprovalState.APPROVED
                            ApprovalState.BLOCKED  -> ApprovalState.APPROVED
                            ApprovalState.APPROVED -> null  // no-op
                        }
                        newState?.let { currentOnStateChange.value(it) }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        val newState = when (currentState.value) {
                            ApprovalState.PENDING  -> ApprovalState.BLOCKED
                            ApprovalState.APPROVED -> ApprovalState.BLOCKED
                            ApprovalState.BLOCKED  -> null  // no-op
                        }
                        newState?.let { currentOnStateChange.value(it) }
                    }
                    else -> Unit
                }
            }
            false // always spring back to centre
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isLocked,
        enableDismissFromEndToStart = !isLocked,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd ->
                    if (state != ApprovalState.APPROVED) Color(0xFF4CAF50) else Color.Transparent
                SwipeToDismissBoxValue.EndToStart ->
                    if (state != ApprovalState.BLOCKED) Color(0xFFF44336) else Color.Transparent
                else -> Color.Transparent
            }
            val label = when (direction) {
                SwipeToDismissBoxValue.StartToEnd ->
                    if (state != ApprovalState.APPROVED) "Approve" else ""
                SwipeToDismissBoxValue.EndToStart ->
                    if (state != ApprovalState.BLOCKED) "Block" else ""
                else -> ""
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment
            ) {
                Surface(
                    color = bgColor,
                    modifier = Modifier.fillMaxSize()
                ) {}
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    number,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state == ApprovalState.APPROVED) {
                    IconButton(
                        onClick = onSendLocation,
                        enabled = locationServicesEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShareLocation,
                            contentDescription = "Send location to $number",
                            tint = if (locationServicesEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    if (activeSubscription != null) {
                        val remainingFraction = ((activeSubscription.expiresAt - System.currentTimeMillis()).toFloat() /
                            (activeSubscription.durationHours * 3_600_000L).toFloat()).coerceIn(0f, 1f)
                        val subIcon = when {
                            remainingFraction >= 0.75f -> Icons.Filled.SignalWifi4Bar
                            remainingFraction >= 0.50f -> Icons.Filled.NetworkWifi3Bar
                            remainingFraction >= 0.25f -> Icons.Filled.NetworkWifi2Bar
                            else -> Icons.Filled.NetworkWifi1Bar
                        }
                        IconButton(onClick = { showSubDialog = true }) {
                            Icon(
                                imageVector = subIcon,
                                contentDescription = "View subscription for $number",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                StateBadge(state)
            }
        }
    }
}

@Composable
fun StateBadge(state: ApprovalState) {
    val (bgColor, textColor, label) = when (state) {
        ApprovalState.APPROVED -> Triple(Color(0xFF4CAF50), Color.White, "APPROVED")
        ApprovalState.BLOCKED  -> Triple(Color(0xFFF44336), Color.White, "BLOCKED")
        ApprovalState.PENDING  -> Triple(Color(0xFFFF9800), Color.White, "PENDING")
    }
    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
