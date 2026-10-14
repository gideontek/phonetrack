package com.gideontek.phonetrack

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

/** Top-level destinations. A few screens need no navigation library; Back goes one level up (Advanced to Settings to Main). */
enum class Screen { Main, Settings, Advanced }

/**
 * Owns everything the screens share: the view model, permission state (with its resume hook)
 * and the PIN dialogs. Created once here so none of it depends on which screen is showing.
 */
@Composable
fun AppHost(vm: HomeViewModel = viewModel()) {
    val permissions = rememberPermissionsState()
    val pinDialogs = rememberPinDialogState()
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }

    BackHandler(enabled = screen != Screen.Main) {
        screen = if (screen == Screen.Advanced) Screen.Settings else Screen.Main
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
                onOpenAdvanced = { screen = Screen.Advanced },
                onBack = { screen = Screen.Main },
            )
            Screen.Advanced -> AdvancedScreen(
                vm = vm,
                pinDialogs = pinDialogs,
                onBack = { screen = Screen.Settings },
            )
        }
    }

    PinDialogs(vm, pinDialogs)
}
