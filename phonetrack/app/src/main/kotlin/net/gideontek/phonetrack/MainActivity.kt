package net.gideontek.phonetrack

import android.Manifest
import android.app.Application
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class ApprovalState { DEFAULT, APPROVED, BLOCKED }

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

    private val _autoStartOnBoot = MutableStateFlow(prefs.getBoolean("auto_start_on_boot", false))
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private fun storedPin() = prefs.getString("settings_pin", "") ?: ""

    private val _pinSet = MutableStateFlow(storedPin().isNotEmpty())
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    // Starts locked whenever a PIN has been set; resets on every process start.
    private val _isLocked = MutableStateFlow(storedPin().isNotEmpty())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _blockAll = MutableStateFlow(prefs.getBoolean("block_all", false))
    val blockAll: StateFlow<Boolean> = _blockAll.asStateFlow()

    private val _approvalsList = MutableStateFlow(parseApprovalsList())
    val approvalsList: StateFlow<List<Pair<String, ApprovalState>>> = _approvalsList.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sms_enabled" -> _enabled.value = prefs.getBoolean("sms_enabled", false)
            "sms_keyword" -> _keyword.value =
                prefs.getString("sms_keyword", "phonetrack") ?: "phonetrack"
            "auto_start_on_boot" -> _autoStartOnBoot.value =
                prefs.getBoolean("auto_start_on_boot", false)
            "settings_pin" -> _pinSet.value = storedPin().isNotEmpty()
            "block_all" -> _blockAll.value = prefs.getBoolean("block_all", false)
            "approvals_list" -> _approvalsList.value = parseApprovalsList()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun parseApprovalsList(): List<Pair<String, ApprovalState>> {
        val json = prefs.getString("approvals_list", "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val result = mutableListOf<Pair<String, ApprovalState>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val number = obj.optString("number")
            val state = when (obj.optString("state", "DEFAULT")) {
                "APPROVED" -> ApprovalState.APPROVED
                "BLOCKED" -> ApprovalState.BLOCKED
                else -> ApprovalState.DEFAULT
            }
            if (number.isNotEmpty()) result.add(number to state)
        }
        return result
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean("sms_enabled", value).apply()
    }

    fun setKeyword(value: String) {
        _keyword.value = value
        prefs.edit().putString("sms_keyword", value).apply()
    }

    fun setAutoStartOnBoot(value: Boolean) {
        _autoStartOnBoot.value = value
        prefs.edit().putBoolean("auto_start_on_boot", value).apply()
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

    fun setBlockAll(value: Boolean) {
        _blockAll.value = value
        prefs.edit().putBoolean("block_all", value).apply()
    }

    fun setNumberState(number: String, state: ApprovalState) {
        val json = prefs.getString("approvals_list", "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("number") == number) {
                obj.put("state", state.name)
                array.put(i, obj)
                break
            }
        }
        prefs.edit().putString("approvals_list", array.toString()).apply()
        _approvalsList.value = parseApprovalsList()
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
    val autoStartOnBoot by vm.autoStartOnBoot.collectAsState()
    val isLocked by vm.isLocked.collectAsState()
    val pinSet by vm.pinSet.collectAsState()
    val blockAll by vm.blockAll.collectAsState()
    val approvalsList by vm.approvalsList.collectAsState()

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

    // Permission states — initialized on composition, updated after each grant attempt
    // and re-checked on every ON_RESUME (covers revocation from system Settings).
    var smsGranted by remember { mutableStateOf(checkSms()) }
    var locationGranted by remember { mutableStateOf(checkLocation()) }
    var bgLocationGranted by remember { mutableStateOf(checkBgLocation()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = checkSms()
                locationGranted = checkLocation()
                bgLocationGranted = checkBgLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // PIN dialog state
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }

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
                        else -> { vm.setPin(pinInput); showSetPinDialog = false }
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
            // Title row: [PhoneTrack]----[Remove PIN?][lock icon]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PhoneTrack", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.weight(1f))
                if (pinSet && !isLocked) {
                    TextButton(onClick = {
                        vm.removePin()
                        Toast.makeText(context, "PIN removed", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Remove PIN", style = MaterialTheme.typography.bodySmall)
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

            // Start on boot toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Start on Boot", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = autoStartOnBoot,
                    onCheckedChange = { vm.setAutoStartOnBoot(it) },
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
                    Text("Start on boot: ${if (autoStartOnBoot) "ON" else "OFF"}")
                    Text("Keyword: \"$keyword\"")
                    Text(
                        "Send \"$keyword\" as the first word of an SMS to trigger a location reply.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Approvals card
            ApprovalsCard(
                blockAll = blockAll,
                approvalsList = approvalsList,
                isLocked = isLocked,
                onBlockAllChange = { vm.setBlockAll(it) },
                onNumberStateChange = { number, state -> vm.setNumberState(number, state) },
                onSendLocation = { number ->
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, SmsLocationService::class.java).putExtra("sender", number)
                    )
                }
            )
        }
    }
}

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
    isLocked: Boolean,
    onSmsRequest: () -> Unit,
    onLocationRequest: () -> Unit,
    onBgLocationRequest: () -> Unit
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
                }
            }
        }
    }
}

@Composable
fun ApprovalsCard(
    blockAll: Boolean,
    approvalsList: List<Pair<String, ApprovalState>>,
    isLocked: Boolean,
    onBlockAllChange: (Boolean) -> Unit,
    onNumberStateChange: (String, ApprovalState) -> Unit,
    onSendLocation: (String) -> Unit
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
                Text("Block all", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 4.dp))
                Switch(
                    checked = blockAll,
                    onCheckedChange = onBlockAllChange,
                    enabled = !isLocked
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
                                onStateChange = { newState -> onNumberStateChange(number, newState) },
                                onSendLocation = { onSendLocation(number) }
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
    onStateChange: (ApprovalState) -> Unit,
    onSendLocation: () -> Unit
) {
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
                        val newState = if (currentState.value != ApprovalState.APPROVED)
                            ApprovalState.APPROVED else ApprovalState.DEFAULT
                        currentOnStateChange.value(newState)
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        val newState = if (currentState.value != ApprovalState.BLOCKED)
                            ApprovalState.BLOCKED else ApprovalState.DEFAULT
                        currentOnStateChange.value(newState)
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
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
                else -> Color.Transparent
            }
            val label = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> "Approve"
                SwipeToDismissBoxValue.EndToStart -> "Block"
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
                androidx.compose.material3.Surface(
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
                    IconButton(onClick = onSendLocation) {
                        Icon(
                            imageVector = Icons.Filled.ShareLocation,
                            contentDescription = "Send location to $number",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
        ApprovalState.DEFAULT  -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "DEFAULT"
        )
    }
    androidx.compose.material3.Surface(
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
