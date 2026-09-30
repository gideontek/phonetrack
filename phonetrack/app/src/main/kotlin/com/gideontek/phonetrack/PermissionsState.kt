package com.gideontek.phonetrack

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal fun isLocationServicesEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
    } else {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

/**
 * Permission and location-services state shared by every screen, plus the launchers that
 * change it. Created once at the top of the UI (see [AppHost]) so the resume hook keeps
 * running whichever screen is showing.
 */
@Stable
class PermissionsState internal constructor(
    smsGranted: Boolean,
    locationGranted: Boolean,
    bgLocationGranted: Boolean,
    notificationsGranted: Boolean,
    locationServicesEnabled: Boolean,
) {
    var smsGranted by mutableStateOf(smsGranted)
        internal set
    var locationGranted by mutableStateOf(locationGranted)
        internal set
    var bgLocationGranted by mutableStateOf(bgLocationGranted)
        internal set
    var notificationsGranted by mutableStateOf(notificationsGranted)
        internal set
    var locationServicesEnabled by mutableStateOf(locationServicesEnabled)
        internal set

    internal var launchSms: () -> Unit = {}
    internal var launchLocation: () -> Unit = {}
    internal var launchBgLocation: () -> Unit = {}
    internal var launchNotifications: () -> Unit = {}

    fun requestSms() = launchSms()
    fun requestLocation() = launchLocation()
    fun requestBgLocation() = launchBgLocation()
    fun requestNotifications() = launchNotifications()
}

@Composable
fun rememberPermissionsState(): PermissionsState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun granted(vararg perms: String) = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun checkSms() = granted(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
    fun checkLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun checkBgLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun checkNotifications() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        granted(Manifest.permission.POST_NOTIFICATIONS)

    // Initialized on composition, updated after each grant attempt and re-checked on every
    // ON_RESUME (covers revocation from system Settings).
    val state = remember {
        PermissionsState(
            smsGranted = checkSms(),
            locationGranted = checkLocation(),
            bgLocationGranted = checkBgLocation(),
            notificationsGranted = checkNotifications(),
            locationServicesEnabled = isLocationServicesEnabled(context),
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.smsGranted = checkSms()
                state.locationGranted = checkLocation()
                state.bgLocationGranted = checkBgLocation()
                state.notificationsGranted = checkNotifications()
                state.locationServicesEnabled = isLocationServicesEnabled(context)
                // Subscriptions stored while a permission was missing start working as soon
                // as the owner has resolved it and come back here.
                SubscriptionManager.resumeIfPossible(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                state.locationServicesEnabled = isLocationServicesEnabled(context)
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Step 3 — background location (must be requested separately on Android 11+)
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        state.bgLocationGranted = checkBgLocation()
        SubscriptionManager.resumeIfPossible(context)
    }

    // Step 2 — fine + coarse location; on success trigger step 3
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        state.locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (state.locationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // Step 1 — SMS permissions
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { state.smsGranted = checkSms() }

    // Step 4 — POST_NOTIFICATIONS (Android 13+; needed for the location-services-off alert)
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { state.notificationsGranted = checkNotifications() }

    state.launchSms = {
        smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS))
    }
    state.launchLocation = {
        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    state.launchBgLocation = {
        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    state.launchNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return state
}
