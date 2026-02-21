package net.gideontek.phonetrack

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
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

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sms_enabled" -> _enabled.value = prefs.getBoolean("sms_enabled", false)
            "sms_keyword" -> _keyword.value =
                prefs.getString("sms_keyword", "phonetrack") ?: "phonetrack"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean("sms_enabled", value).apply()
    }

    fun setKeyword(value: String) {
        _keyword.value = value
        prefs.edit().putString("sms_keyword", value).apply()
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

    // Step 3 — background location (must be requested separately on Android 11+)
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result reflected via status card */ }

    // Step 2 — fine + coarse location; on success trigger step 3
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // Step 1 — SMS permissions
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permission result reflected via status card */ }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("PhoneTrack", style = MaterialTheme.typography.headlineMedium)

            // Enable / disable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SMS Listening", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { vm.setEnabled(it) })
            }

            // Keyword field
            OutlinedTextField(
                value = keyword,
                onValueChange = { vm.setKeyword(it) },
                label = { Text("Keyword (first word of incoming SMS)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Permission rationale + buttons
            Text(
                "Grant permissions in order. Background location must be requested after location.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    smsLauncher.launch(
                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("1. Grant SMS Permissions") }

            Button(
                onClick = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("2. Grant Location Permissions") }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Button(
                    onClick = {
                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("3. Grant Background Location") }
            }

            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Current Settings", style = MaterialTheme.typography.titleSmall)
                    Text("Listening: ${if (enabled) "ON" else "OFF"}")
                    Text("Keyword: \"$keyword\"")
                    Text(
                        "Send \"$keyword\" as the first word of an SMS to trigger a location reply.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
