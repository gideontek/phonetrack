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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onBack: () -> Unit,
) {
    val enabled by vm.enabled.collectAsState()
    val keyword by vm.keyword.collectAsState()
    val isLocked by vm.isLocked.collectAsState()
    val pinSet by vm.pinSet.collectAsState()
    val replyOptions by vm.replyOptions.collectAsState()
    val rateLimit by vm.rateLimitPerHour.collectAsState()
    val maxSubscriptions by vm.maxSubscriptions.collectAsState()

    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
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
                    label = "SMS listening",
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
                        label = { Text("Keyword (first word of an incoming SMS)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLocked
                    )
                    Text(
                        "One word, no spaces. Anyone who texts it is added to your decision list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ReplySettingsCard(
                options = replyOptions,
                isLocked = isLocked,
                onChange = { vm.setReplyOptions(it) }
            )

            SettingsSection("Limits") {
                StepperRow(
                    label = "Commands per number",
                    supporting = "Per hour; the rest are ignored",
                    valueText = rateLimit.toString(),
                    lessDescription = "Fewer commands per hour",
                    moreDescription = "More commands per hour",
                    lessEnabled = !isLocked && rateLimit > LimitSteps.RATE_MIN,
                    moreEnabled = !isLocked && rateLimit < LimitSteps.RATE_MAX,
                    onLess = { vm.setRateLimitPerHour(LimitSteps.rateDown(rateLimit)) },
                    onMore = { vm.setRateLimitPerHour(LimitSteps.rateUp(rateLimit)) }
                )
                RowDivider()
                StepperRow(
                    label = "Active subscriptions",
                    supporting = "Most running at once. Lowering it never cancels existing ones.",
                    valueText = maxSubscriptions.toString(),
                    lessDescription = "Fewer active subscriptions",
                    moreDescription = "More active subscriptions",
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
                                Toast.makeText(context, "PIN Removed", Toast.LENGTH_SHORT).show()
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

            AboutSection()
        }
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
