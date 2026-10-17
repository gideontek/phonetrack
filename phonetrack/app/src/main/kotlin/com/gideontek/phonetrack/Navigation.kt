package com.gideontek.phonetrack

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Top-level destinations. A few screens need no navigation library; Back goes one level up (the screens
 * opened from Settings go to Settings, Settings to Main).
 */
enum class Screen { Main, Settings, MapLink, Movement }

/**
 * Owns everything the screens share: the view model, permission state (with its resume hook)
 * and the PIN dialogs. Created once here so none of it depends on which screen is showing.
 */
@Composable
fun AppHost(vm: HomeViewModel = viewModel()) {
    val permissions = rememberPermissionsState()
    val pinDialogs = rememberPinDialogState()
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    // Kept here, not in Settings, so coming back from a screen opened from Settings finds it as it was left.
    val settingsScroll = rememberScrollState()
    var repliesExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = screen != Screen.Main) {
        screen = if (screen == Screen.Settings) Screen.Main else Screen.Settings
    }

    Crossfade(targetState = screen, label = "screen") { target ->
        when (target) {
            Screen.Main -> MainScreen(
                vm = vm,
                permissions = permissions,
                pinDialogs = pinDialogs,
                onOpenSettings = { screen = Screen.Settings },
            )
            Screen.Settings -> SettingsScreen(
                vm = vm,
                permissions = permissions,
                pinDialogs = pinDialogs,
                scroll = settingsScroll,
                repliesExpanded = repliesExpanded,
                onRepliesExpandedChange = { repliesExpanded = it },
                onOpenMapLink = { screen = Screen.MapLink },
                onOpenMovement = { screen = Screen.Movement },
                onBack = { screen = Screen.Main },
            )
            Screen.MapLink -> MapLinkScreen(
                vm = vm,
                pinDialogs = pinDialogs,
                onBack = { screen = Screen.Settings },
            )
            Screen.Movement -> MovementScreen(
                vm = vm,
                pinDialogs = pinDialogs,
                onBack = { screen = Screen.Settings },
            )
        }
    }

    PinDialogs(vm, pinDialogs)
}
