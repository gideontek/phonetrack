package com.gideontek.phonetrack

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Everything configurable lives here. Back (system or arrow) returns to Main. */
@Composable
fun SettingsScreen(
    vm: HomeViewModel,
    permissions: PermissionsState,
    pinDialogs: PinDialogState,
    onOpenFaq: () -> Unit,
    scroll: ScrollState,
    repliesExpanded: Boolean,
    onRepliesExpandedChange: (Boolean) -> Unit,
    onOpenMapLink: () -> Unit,
    onOpenMovement: () -> Unit,
    onBack: () -> Unit,
    onOpenEventLog: () -> Unit,
) {
    val enabled by vm.enabled.collectAsState()
    val keyword by vm.keyword.collectAsState()
    val isLocked by vm.isLocked.collectAsState()
    val pinSet by vm.pinSet.collectAsState()
    val replyOptions by vm.replyOptions.collectAsState()
    val rateLimit by vm.rateLimitPerHour.collectAsState()
    val maxSubscriptions by vm.maxSubscriptions.collectAsState()
    val lowBattery by vm.lowBatteryPct.collectAsState()
    val eventLogEnabled by vm.eventLogEnabled.collectAsState()
    val eventLog by vm.eventLog.collectAsState()
    var confirmEventLogOff by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title row: [back][Settings]----[lock]
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
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.weight(1f))
                // Locking from here leaves Settings: its controls would just turn off.
                LockButton(vm, pinDialogs, onLocked = onBack)
            }

            SettingsSection("General") {
                ToggleRow(
                    label = "Listening",
                    supporting = "Reply to approved numbers that text your keyword",
                    checked = enabled,
                    enabled = !isLocked,
                    onCheckedChange = { vm.setEnabled(it) }
                )
                RowDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { vm.setKeyword(it) },
                        label = { Text("Keyword") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLocked
                    )
                    Text(
                        "One word. New numbers that text it appear under Needs your decision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RowDivider()
                StepperRow(
                    label = "Low-battery alert",
                    supporting = "Texts people tracking you once when the battery runs low",
                    valueText = LimitSteps.lowBatteryText(lowBattery),
                    lessDescription = "Lower battery alert level",
                    moreDescription = "Higher battery alert level",
                    lessEnabled = !isLocked && lowBattery > LimitSteps.LOW_BATTERY_STEPS.first(),
                    moreEnabled = !isLocked && lowBattery < LimitSteps.LOW_BATTERY_STEPS.last(),
                    onLess = { vm.setLowBatteryPct(LimitSteps.lowBatteryDown(lowBattery)) },
                    onMore = { vm.setLowBatteryPct(LimitSteps.lowBatteryUp(lowBattery)) }
                )
            }

            ReplySettingsCard(
                options = replyOptions,
                isLocked = isLocked,
                expanded = repliesExpanded,
                onExpandedChange = onRepliesExpandedChange,
                onCustomizeLink = onOpenMapLink,
                onCustomizeMovement = onOpenMovement,
                onChange = { vm.setReplyOptions(it) }
            )

            SettingsSection("Limits") {
                StepperRow(
                    label = "Max requests per hour",
                    supporting = "Per approved number",
                    valueText = rateLimit.toString(),
                    lessDescription = "Fewer requests per hour",
                    moreDescription = "More requests per hour",
                    lessEnabled = !isLocked && rateLimit > LimitSteps.RATE_MIN,
                    moreEnabled = !isLocked && rateLimit < LimitSteps.RATE_MAX,
                    onLess = { vm.setRateLimitPerHour(LimitSteps.rateDown(rateLimit)) },
                    onMore = { vm.setRateLimitPerHour(LimitSteps.rateUp(rateLimit)) }
                )
                RowDivider()
                StepperRow(
                    label = "Max subscriptions",
                    supporting = "Total running at once",
                    valueText = maxSubscriptions.toString(),
                    lessDescription = "Fewer subscriptions",
                    moreDescription = "More subscriptions",
                    lessEnabled = !isLocked && maxSubscriptions > LimitSteps.SUBS_MIN,
                    moreEnabled = !isLocked && maxSubscriptions < LimitSteps.SUBS_MAX,
                    onLess = { vm.setMaxSubscriptions(LimitSteps.subscriptionsDown(maxSubscriptions)) },
                    onMore = { vm.setMaxSubscriptions(LimitSteps.subscriptionsUp(maxSubscriptions)) }
                )
            }

            PermissionsSection(permissions, isLocked)

            SettingsSection("Security") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .defaultMinSize(minHeight = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PIN", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (pinSet) "Set. Asked when you approve, block or change settings."
                            else "Not set. Anyone holding the phone can change settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (pinSet) {
                        TextButton(onClick = { pinDialogs.showSet = true }, enabled = !isLocked) {
                            Text("Change")
                        }
                    } else {
                        OutlinedButton(onClick = { pinDialogs.showSet = true }) { Text("Set PIN") }
                    }
                }
                if (pinSet && !isLocked) {
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                vm.removePin()
                                Toast.makeText(context, "PIN removed", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .defaultMinSize(minHeight = 56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Remove PIN",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                RowDivider()
                Text(
                    PinLockout.scheduleText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            SettingsSection("Diagnostics") {
                ToggleRow(
                    label = "Event log",
                    supporting = "Records timing and reasons for subscription updates. " +
                        "No phone numbers or locations. Kept 48 hours.",
                    checked = eventLogEnabled,
                    enabled = !isLocked,
                    onCheckedChange = { on ->
                        if (!on && eventLog.isNotEmpty()) confirmEventLogOff = true
                        else vm.setEventLogEnabled(on)
                    }
                )
                if (eventLogEnabled || eventLog.isNotEmpty()) {
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLocked, role = Role.Button, onClick = onOpenEventLog)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .defaultMinSize(minHeight = 56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("View event log", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (eventLog.size == 1) "1 event" else "${eventLog.size} events",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsSection("Reset") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLocked, role = Role.Button) { confirmReset = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .defaultMinSize(minHeight = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Restore default settings",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            SettingsSection("Help") {
                NavigationRow("Troubleshooting & FAQ", null, onOpenFaq)
            }

            AboutSection()
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restore default settings?") },
            text = {
                Text(
                    "Keyword, reply contents, map link, movement and limits go back to their defaults. " +
                        "Your PIN, listening, approved numbers and subscriptions are not changed."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetSettings()
                    confirmReset = false
                    Toast.makeText(context, "Defaults restored", Toast.LENGTH_SHORT).show()
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }

    if (confirmEventLogOff) {
        AlertDialog(
            onDismissRequest = { confirmEventLogOff = false },
            title = { Text("Turn off event log?") },
            text = { Text("Turning it off deletes the recorded events.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEventLogOff = false
                    vm.setEventLogEnabled(false)
                }) { Text("Turn off") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEventLogOff = false }) { Text("Keep on") }
            }
        )
    }
}

@Composable
fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    SettingsSection("About") {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "PhoneTrack SMS ${BuildConfig.VERSION_NAME} · GPL-3.0",
                style = MaterialTheme.typography.bodyLarge
            )
            TextButton(
                onClick = { uriHandler.openUri("https://github.com/gideontek/phonetrack") },
                // Pull the button's own padding back so the text lines up with the line above.
                modifier = Modifier.offset(x = (-12).dp)
            ) { Text("github.com/gideontek/phonetrack") }
        }
    }
}
