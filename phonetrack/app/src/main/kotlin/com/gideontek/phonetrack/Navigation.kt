package com.gideontek.phonetrack

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

/** Top-level destinations. Three screens need no navigation library; Back returns to Main, or from the FAQ to Settings. */
enum class Screen { Main, Settings, Faq }

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
        screen = if (screen == Screen.Faq) Screen.Settings else Screen.Main
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
                onOpenFaq = { screen = Screen.Faq },
                onBack = { screen = Screen.Main },
            )
            Screen.Faq -> FaqScreen(onBack = { screen = Screen.Settings })
        }
    }

    PinDialogs(vm, pinDialogs)
}
