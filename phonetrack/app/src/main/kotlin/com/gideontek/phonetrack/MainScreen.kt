package com.gideontek.phonetrack

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * Day-to-day screen: status, numbers waiting for a decision, active subscriptions and the
 * approved/blocked list. The PIN lock guards only approval changes, the Listening switch and
 * Settings; sending a location or cancelling a subscription is always allowed.
 */
@Composable
fun MainScreen(
    vm: HomeViewModel,
    permissions: PermissionsState,
    pinDialogs: PinDialogState,
    onOpenSettings: () -> Unit,
) {
    val enabled by vm.enabled.collectAsState()
    val approvalEntries by vm.approvalEntries.collectAsState()
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

    // Slow clock for the "asked 12min ago" / "2h left" text and for hiding subscriptions
    // that have just expired.
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clock = System.currentTimeMillis()
        }
    }
    val clockNow = maxOf(clock, now)

    val known = KnownNumbers.split(approvalEntries)
    val liveSubscriptions = SubscriptionView.active(subscriptions, clockNow)
    val summary = StatusSummary.from(
        enabled = enabled,
        smsGranted = permissions.smsGranted,
        locationGranted = permissions.locationGranted,
        bgLocationGranted = permissions.bgLocationGranted,
        locationServicesEnabled = permissions.locationServicesEnabled
    )
    // Respect the system "remove animations" setting.
    val animate = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) != 0f

    val sendLocation = { number: String ->
        ContextCompat.startForegroundService(
            context,
            Intent(context, SmsLocationService::class.java).putExtra("sender", number)
        )
        Toast.makeText(context, "Location Shared", Toast.LENGTH_SHORT).show()
    }

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title row: [PhoneTrack]----[debug][lock][settings]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PhoneTrack",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
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
                IconButton(onClick = { pinDialogs.guard(vm) { onOpenSettings() } }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings"
                    )
                }
            }

            StatusCard(
                summary = summary,
                streamState = streamState,
                subscriptionCount = liveSubscriptions.size,
                enabled = enabled,
                onEnabledChange = { value -> pinDialogs.guard(vm) { vm.setEnabled(value) } },
                locationServicesEnabled = permissions.locationServicesEnabled,
                lastReceiveAt = lastReceiveAt,
                now = clockNow,
                animate = animate,
                onFix = { fix ->
                    when (fix) {
                        StatusFix.RequestSms -> permissions.requestSms()
                        StatusFix.RequestLocation -> permissions.requestLocation()
                        StatusFix.RequestBgLocation -> permissions.requestBgLocation()
                        StatusFix.OpenLocationSettings ->
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }
            )

            if (known.pending.isNotEmpty()) {
                DecisionsSection(
                    pending = known.pending,
                    now = clockNow,
                    onApprove = { number ->
                        pinDialogs.guard(vm) { vm.setNumberState(number, ApprovalState.APPROVED) }
                    },
                    onBlock = { number ->
                        pinDialogs.guard(vm) { vm.setNumberState(number, ApprovalState.BLOCKED) }
                    }
                )
            }

            if (liveSubscriptions.isNotEmpty()) {
                SubscriptionsSection(
                    subscriptions = liveSubscriptions,
                    locationServicesEnabled = permissions.locationServicesEnabled,
                    onSendNow = sendLocation,
                    onCancel = { number -> vm.cancelSubscription(number) }
                )
            }

            if (known.approved.isNotEmpty() || known.blocked.isNotEmpty()) {
                KnownNumbersSection(
                    approved = known.approved,
                    blocked = known.blocked,
                    locationServicesEnabled = permissions.locationServicesEnabled,
                    onApprove = { number ->
                        pinDialogs.guard(vm) { vm.setNumberState(number, ApprovalState.APPROVED) }
                    },
                    onBlock = { number ->
                        pinDialogs.guard(vm) { vm.setNumberState(number, ApprovalState.BLOCKED) }
                    },
                    onSendLocation = sendLocation
                )
            } else if (known.pending.isEmpty()) {
                Text(
                    "No requests yet. Anyone who texts your keyword shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
