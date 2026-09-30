package com.gideontek.phonetrack

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Which PIN dialog is open. Only the open/closed flags are saved; typed digits never are. */
@Stable
class PinDialogState internal constructor(
    private val showSetState: MutableState<Boolean>,
    private val showUnlockState: MutableState<Boolean>,
) {
    var showSet by showSetState
    var showUnlock by showUnlockState
}

@Composable
fun rememberPinDialogState(): PinDialogState {
    val showSet = rememberSaveable { mutableStateOf(false) }
    val showUnlock = rememberSaveable { mutableStateOf(false) }
    return remember { PinDialogState(showSet, showUnlock) }
}

/** Lock icon: no PIN -> set one, locked -> unlock, unlocked -> lock now. */
@Composable
fun LockButton(vm: HomeViewModel, dialogs: PinDialogState) {
    val context = LocalContext.current
    val isLocked by vm.isLocked.collectAsState()
    val pinSet by vm.pinSet.collectAsState()
    IconButton(onClick = {
        when {
            !pinSet -> dialogs.showSet = true     // no PIN: prompt to set one
            isLocked -> dialogs.showUnlock = true // locked: prompt for PIN
            else -> {                             // unlocked: lock immediately
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

@Composable
fun PinDialogs(vm: HomeViewModel, state: PinDialogState) {
    val context = LocalContext.current

    // Set-PIN dialog — shown when no PIN is set and the lock button is tapped
    if (state.showSet) {
        var pinInput by remember { mutableStateOf("") }
        var pinConfirm by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { state.showSet = false },
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
                            state.showSet = false
                            Toast.makeText(context, "PIN Added", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Set PIN") }
            },
            dismissButton = {
                TextButton(onClick = { state.showSet = false }) { Text("Cancel") }
            }
        )
    }

    // Unlock dialog — shown when settings are locked and the lock button is tapped
    if (state.showUnlock) {
        var pinInput by remember { mutableStateOf("") }
        // Already locked out when the dialog opens? Say so up front.
        var error by remember {
            mutableStateOf(vm.pinLockRemainingMs().let { if (it > 0L) lockedOutMessage(it) else "" })
        }
        AlertDialog(
            onDismissRequest = { state.showUnlock = false },
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
                    when (val result = vm.unlock(pinInput)) {
                        PinResult.Success -> {
                            state.showUnlock = false
                            Toast.makeText(context, "Settings Unlocked", Toast.LENGTH_SHORT).show()
                        }
                        is PinResult.Wrong -> {
                            error = "Incorrect PIN. ${result.attemptsLeft} " +
                                "${if (result.attemptsLeft == 1) "attempt" else "attempts"} left before a lockout."
                            pinInput = ""
                        }
                        is PinResult.Locked -> {
                            error = lockedOutMessage(result.remainingMs)
                            pinInput = ""
                        }
                    }
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { state.showUnlock = false }) { Text("Cancel") }
            }
        )
    }
}
