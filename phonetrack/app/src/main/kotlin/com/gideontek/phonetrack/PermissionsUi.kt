package com.gideontek.phonetrack

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionDot(granted: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = if (granted) {
            Modifier.size(12.dp).background(color, CircleShape)
        } else {
            Modifier.size(12.dp).border(1.5.dp, color, CircleShape)
        }
    )
}

@Composable
fun PermissionsCard(
    smsGranted: Boolean,
    locationGranted: Boolean,
    bgLocationGranted: Boolean,
    notificationsGranted: Boolean,
    isLocked: Boolean,
    onSmsRequest: () -> Unit,
    onLocationRequest: () -> Unit,
    onBgLocationRequest: () -> Unit,
    onNotificationsRequest: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse permissions" else "Expand permissions"
                )
                Text(
                    "Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PermissionDot(smsGranted)
                    PermissionDot(locationGranted)
                    PermissionDot(bgLocationGranted)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionDot(notificationsGranted)
                    }
                }
            }

            // Expanded body
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Grant permissions in order. Background location must be requested after location.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onSmsRequest,
                        enabled = !smsGranted && !isLocked,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (smsGranted) "1. SMS Permissions (granted)" else "1. Grant SMS Permissions") }
                    Button(
                        onClick = onLocationRequest,
                        enabled = !locationGranted && !isLocked,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (locationGranted) "2. Location Permissions (granted)" else "2. Grant Location Permissions") }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Button(
                            onClick = onBgLocationRequest,
                            enabled = !bgLocationGranted && !isLocked,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (bgLocationGranted) "3. Background Location (granted)" else "3. Grant Background Location") }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = onNotificationsRequest,
                            enabled = !notificationsGranted && !isLocked,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (notificationsGranted) "4. Notifications (granted)" else "4. Grant Notifications") }
                    }
                }
            }
        }
    }
}
