package com.gideontek.phonetrack

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Day-to-day screen. Interim layout from the scaffold split: the redesign (status card,
 * decision buttons, subscription list) replaces the body later.
 */
@Composable
fun MainScreen(
    vm: HomeViewModel,
    permissions: PermissionsState,
    pinDialogs: PinDialogState,
    onOpenSettings: () -> Unit,
) {
    val enabled by vm.enabled.collectAsState()
    val keyword by vm.keyword.collectAsState()
    val isLocked by vm.isLocked.collectAsState()
    val pinSet by vm.pinSet.collectAsState()
    val approvalsList by vm.approvalsList.collectAsState()
    val subscriptions by vm.subscriptions.collectAsState()
    val lastReceiveAt by vm.lastReceiveAt.collectAsState()
    val lastSendAt by vm.lastSendAt.collectAsState()

    val context = LocalContext.current

    // Live stream state — reflects real, recent send/receive SMS activity, plus any
    // active subscription (see StreamActivityLogic). `now` re-ticks once per second only
    // while a recency window is active and no subscription is running, so the indicator
    // decays back to Inactive without polling forever. While a subscription is active,
    // sending stays on continuously and no ticking is needed — the `subscriptions` list
    // itself already triggers recomposition the moment it ends.
    val hasActiveSubscription = subscriptions.isNotEmpty()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastReceiveAt, lastSendAt, hasActiveSubscription) {
        now = System.currentTimeMillis()
        if (!hasActiveSubscription) {
            while (StreamActivityLogic.currentState(lastReceiveAt, lastSendAt, now, false) != StreamState.Inactive) {
                delay(1000)
                now = System.currentTimeMillis()
            }
        }
    }
    val streamState = StreamActivityLogic.currentState(lastReceiveAt, lastSendAt, now, hasActiveSubscription)

    // Debug-only: cycles debugSimulateStream() through all 4 states so the real
    // StreamActivityLogic window/decay can be previewed without a real SMS.
    var debugStateIndex by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title row: [PhoneTrack SMS]----[debug][lock][settings]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PhoneTrack SMS", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.weight(1f))
                if (BuildConfig.DEBUG) {
                    IconButton(onClick = {
                        debugStateIndex = (debugStateIndex + 1) % StreamState.values().size
                        vm.debugSimulateStream(StreamState.values()[debugStateIndex])
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Cycle stream state (debug)"
                        )
                    }
                }
                LockButton(vm, pinDialogs)
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings"
                    )
                }
            }

            StreamStatusIndicator(
                state = streamState,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (subscriptions.isNotEmpty()) subscriptions.size.toString() else "·",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = streamState.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Enable / disable toggle
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

            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (pinSet) "Current Settings (${if (isLocked) "locked" else "unlocked"})"
                        else "Current Settings",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("Listening: ${if (enabled) "ON" else "OFF"}")
                    Text("Keyword: \"$keyword\"")
                    Text(
                        "Location services: ${if (permissions.locationServicesEnabled) "ON" else "OFF"}",
                        color = if (permissions.locationServicesEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Send \"$keyword\" as the first word of an SMS to trigger a location reply.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Approvals card
            ApprovalsCard(
                approvalsList = approvalsList,
                subscriptions = subscriptions,
                isLocked = isLocked,
                locationServicesEnabled = permissions.locationServicesEnabled,
                onNumberStateChange = { number, state -> vm.setNumberState(number, state) },
                onSendLocation = { number ->
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, SmsLocationService::class.java).putExtra("sender", number)
                    )
                    Toast.makeText(context, "Location Shared", Toast.LENGTH_SHORT).show()
                },
                onCancelSubscription = { number -> vm.cancelSubscription(number) }
            )
        }
    }
}
