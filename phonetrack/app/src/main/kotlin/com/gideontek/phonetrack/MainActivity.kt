package com.gideontek.phonetrack

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.location.LocationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun isLocationServicesEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
    } else {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                HomeScreen()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean("sms_enabled", false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _keyword = MutableStateFlow(
        prefs.getString("sms_keyword", "phonetrack") ?: "phonetrack"
    )
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private fun storedPin() = prefs.getString("settings_pin", "") ?: ""

    private val _pinSet = MutableStateFlow(storedPin().isNotEmpty())
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    // Starts locked whenever a PIN has been set; resets on every process start.
    private val _isLocked = MutableStateFlow(storedPin().isNotEmpty())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _approvalsList = MutableStateFlow(parseApprovalsList())
    val approvalsList: StateFlow<List<Pair<String, ApprovalState>>> = _approvalsList.asStateFlow()

    private val _subscriptions = MutableStateFlow(SubscriptionManager.getAll(app))
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    // Timestamps of the most recent inbound SMS request / outbound SMS reply, written by
    // SmsReceiver and SmsSender respectively. Drives the live StreamStatusIndicator state
    // via StreamActivityLogic — see HomeScreen.
    private val _lastReceiveAt = MutableStateFlow(prefs.getLong("last_receive_at", 0L))
    val lastReceiveAt: StateFlow<Long> = _lastReceiveAt.asStateFlow()

    private val _lastSendAt = MutableStateFlow(prefs.getLong("last_send_at", 0L))
    val lastSendAt: StateFlow<Long> = _lastSendAt.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sms_enabled" -> _enabled.value = prefs.getBoolean("sms_enabled", false)
            "sms_keyword" -> _keyword.value =
                prefs.getString("sms_keyword", "phonetrack") ?: "phonetrack"
            "settings_pin" -> _pinSet.value = storedPin().isNotEmpty()
            "approvals_list" -> _approvalsList.value = parseApprovalsList()
            "subscriptions_list" -> _subscriptions.value = SubscriptionManager.getAll(app)
            "last_receive_at" -> _lastReceiveAt.value = prefs.getLong("last_receive_at", 0L)
            "last_send_at" -> _lastSendAt.value = prefs.getLong("last_send_at", 0L)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        // Registered first so the migration's writes refresh the flows above.
        PrefsMigration.run(app)
        // The "start on boot" setting was removed: the listener state is persisted and simply
        // restored after a reboot. Drop the stale key left behind by older versions.
        if (prefs.contains("auto_start_on_boot")) {
            prefs.edit().remove("auto_start_on_boot").apply()
        }
    }

    private fun parseApprovalsList(): List<Pair<String, ApprovalState>> =
        ApprovalStore.getAll(getApplication())
            .map { it.number to it.state }
            .sortedBy { (_, state) -> ApprovalLogic.sortKey(state) }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean("sms_enabled", value).apply()
    }

    fun setKeyword(value: String) {
        val clean = SmsLimits.sanitizeKeyword(value)
        _keyword.value = clean
        prefs.edit().putString("sms_keyword", clean).apply()
    }

    /** Save a new PIN and leave the session unlocked. */
    fun setPin(pin: String) {
        prefs.edit().putString("settings_pin", pin).apply()
        _pinSet.value = true
        _isLocked.value = false
    }

    /** Returns true and unlocks if [entered] matches the stored PIN. */
    fun unlock(entered: String): Boolean {
        return if (entered == storedPin()) {
            _isLocked.value = false
            true
        } else false
    }

    fun lock() {
        _isLocked.value = true
    }

    /** Clears the stored PIN, leaving settings permanently unlocked until a new PIN is set. */
    fun removePin() {
        prefs.edit().remove("settings_pin").apply()
        _pinSet.value = false
        _isLocked.value = false
    }

    fun setNumberState(number: String, state: ApprovalState) {
        ApprovalStore.setState(getApplication(), number, state)
        _approvalsList.value = parseApprovalsList()
    }

    /**
     * Debug-only: simulates a receive/send event pair so the real [StreamActivityLogic]
     * window (and decay) can be previewed without a real SMS. Writes through the same
     * prefs keys SmsReceiver/SmsSender use, so the UI reacts exactly as it would in
     * production — see the debug cycle button in HomeScreen (BuildConfig.DEBUG only).
     */
    fun debugSimulateStream(target: StreamState) {
        val now = System.currentTimeMillis()
        val past = 0L
        prefs.edit()
            .putLong("last_receive_at", if (target.receivesInbound) now else past)
            .putLong("last_send_at", if (target.sendsOutbound) now else past)
            .apply()
    }

    fun cancelSubscription(number: String) {
        val ctx = getApplication<Application>()
        SmsSender.sendSubscriptionCancelled(ctx, number)
        SubscriptionManager.remove(ctx, number)
        _subscriptions.value = SubscriptionManager.getAll(ctx)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}

// ---------------------------------------------------------------------------
// Composable UI
// ---------------------------------------------------------------------------

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val enabled by vm.enabled.collectAsState()
    val keyword by vm.keyword.collectAsState()
    val isLocked by vm.isLocked.collectAsState()
    val pinSet by vm.pinSet.collectAsState()
    val approvalsList by vm.approvalsList.collectAsState()
    val subscriptions by vm.subscriptions.collectAsState()
    val lastReceiveAt by vm.lastReceiveAt.collectAsState()
    val lastSendAt by vm.lastSendAt.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission check helpers
    fun granted(vararg perms: String) = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun checkSms() = granted(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
    fun checkLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun checkBgLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun checkNotifications() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        granted(Manifest.permission.POST_NOTIFICATIONS)

    // Permission states — initialized on composition, updated after each grant attempt
    // and re-checked on every ON_RESUME (covers revocation from system Settings).
    var smsGranted by remember { mutableStateOf(checkSms()) }
    var locationGranted by remember { mutableStateOf(checkLocation()) }
    var bgLocationGranted by remember { mutableStateOf(checkBgLocation()) }
    var notificationsGranted by remember { mutableStateOf(checkNotifications()) }
    var locationServicesEnabled by remember { mutableStateOf(isLocationServicesEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = checkSms()
                locationGranted = checkLocation()
                bgLocationGranted = checkBgLocation()
                notificationsGranted = checkNotifications()
                locationServicesEnabled = isLocationServicesEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                locationServicesEnabled = isLocationServicesEnabled(context)
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Step 3 — background location (must be requested separately on Android 11+)
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { bgLocationGranted = checkBgLocation() }

    // Step 2 — fine + coarse location; on success trigger step 3
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // Step 1 — SMS permissions
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { smsGranted = checkSms() }

    // Step 4 — POST_NOTIFICATIONS (Android 13+; needed for the location-services-off alert)
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsGranted = checkNotifications() }

    // PIN dialog state
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }

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

    // Set-PIN dialog — shown when no PIN is set and the lock button is tapped
    if (showSetPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinConfirm by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("Set PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error.isNotEmpty()) {
                        Text(error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter { c -> c.isDigit() } },
                        label = { Text("New PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { pinConfirm = it.filter { c -> c.isDigit() } },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pinInput.length < 4 -> error = "PIN must be at least 4 digits"
                        pinInput != pinConfirm -> error = "PINs do not match"
                        else -> {
                            vm.setPin(pinInput)
                            showSetPinDialog = false
                            Toast.makeText(context, "PIN Added", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Set PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Unlock dialog — shown when settings are locked and the lock button is tapped
    if (showUnlockDialog) {
        var pinInput by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("Enter PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error.isNotEmpty()) {
                        Text(error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter { c -> c.isDigit() } },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.unlock(pinInput)) {
                        showUnlockDialog = false
                        Toast.makeText(context, "Settings Unlocked", Toast.LENGTH_SHORT).show()
                    } else {
                        error = "Incorrect PIN"
                        pinInput = ""
                    }
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) { Text("Cancel") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title row: [PhoneTrack SMS]----[Remove PIN?][lock icon]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PhoneTrack SMS", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.weight(1f))
                if (pinSet && !isLocked) {
                    TextButton(onClick = {
                        vm.removePin()
                        Toast.makeText(context, "PIN Removed", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Remove PIN", style = MaterialTheme.typography.bodySmall)
                    }
                }
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

                IconButton(onClick = {
                    when {
                        !pinSet -> showSetPinDialog = true   // no PIN: prompt to set one
                        isLocked -> showUnlockDialog = true  // locked: prompt for PIN
                        else -> {                            // unlocked: lock immediately
                            vm.lock()
                            Toast.makeText(context, "Settings Locked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (isLocked) "Unlock settings" else "Lock settings",
                        tint = if (!pinSet) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                               else MaterialTheme.colorScheme.onSurface
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

            // Keyword field
            OutlinedTextField(
                value = keyword,
                onValueChange = { vm.setKeyword(it) },
                label = { Text("Keyword (first word of incoming SMS)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked
            )

            // Permissions card
            PermissionsCard(
                smsGranted = smsGranted,
                locationGranted = locationGranted,
                bgLocationGranted = bgLocationGranted,
                notificationsGranted = notificationsGranted,
                isLocked = isLocked,
                onSmsRequest = {
                    smsLauncher.launch(
                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
                    )
                },
                onLocationRequest = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onBgLocationRequest = {
                    bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                },
                onNotificationsRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

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
                        "Location services: ${if (locationServicesEnabled) "ON" else "OFF"}",
                        color = if (locationServicesEnabled)
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
                locationServicesEnabled = locationServicesEnabled,
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
