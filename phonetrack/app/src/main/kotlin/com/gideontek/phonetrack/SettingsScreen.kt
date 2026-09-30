package com.gideontek.phonetrack

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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

    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title row: [back][Settings]----[Remove PIN?][lock]
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
                if (pinSet && !isLocked) {
                    TextButton(onClick = {
                        vm.removePin()
                        Toast.makeText(context, "PIN Removed", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Remove PIN", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LockButton(vm, pinDialogs)
            }

            // Enable / disable toggle (same setting as the switch on Main)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SMS Listening", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = enabled,
                    onCheckedChange = { vm.setEnabled(it) },
                    enabled = !isLocked
                )
            }

            // Keyword field
            OutlinedTextField(
                value = keyword,
                onValueChange = { vm.setKeyword(it) },
                label = { Text("Keyword (first word of incoming SMS)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked
            )

            PermissionsCard(
                smsGranted = permissions.smsGranted,
                locationGranted = permissions.locationGranted,
                bgLocationGranted = permissions.bgLocationGranted,
                notificationsGranted = permissions.notificationsGranted,
                isLocked = isLocked,
                onSmsRequest = permissions::requestSms,
                onLocationRequest = permissions::requestLocation,
                onBgLocationRequest = permissions::requestBgLocation,
                onNotificationsRequest = permissions::requestNotifications
            )

            ReplySettingsCard(
                options = replyOptions,
                isLocked = isLocked,
                onChange = { vm.setReplyOptions(it) }
            )

            AboutCard()
        }
    }
}

@Composable
fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    val style = MaterialTheme.typography.bodySmall
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("PhoneTrack SMS ${BuildConfig.VERSION_NAME} · GPL-3.0", style = style)
        Text(
            "github.com/gideontek/phonetrack",
            style = style,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://github.com/gideontek/phonetrack")
            }
        )
    }
}
