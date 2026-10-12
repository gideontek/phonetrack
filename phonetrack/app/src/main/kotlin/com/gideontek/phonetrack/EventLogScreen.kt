package com.gideontek.phonetrack

import android.os.Build
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.ZoneId

/** The recorded events as plain text, newest first, with Copy and Clear. Back returns to Settings. */
@Composable
fun EventLogScreen(
    vm: HomeViewModel,
    pinDialogs: PinDialogState,
    onBack: () -> Unit,
) {
    val enabled by vm.eventLogEnabled.collectAsState()
    val entries by vm.eventLog.collectAsState()
    val isLocked by vm.isLocked.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val text = remember(entries) {
        EventLogLogic.render(entries, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, ZoneId.systemDefault())
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Event log", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.weight(1f))
                if (!isLocked && entries.isNotEmpty()) {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        // Android 13+ shows its own "copied" confirmation.
                        if (Build.VERSION.SDK_INT < 33) {
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy event log")
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear event log")
                    }
                }
            }

            when {
                isLocked -> {
                    Text(
                        "Unlock to view the event log.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = { pinDialogs.showUnlock = true }) { Text("Unlock") }
                }
                !enabled -> Text("Event log is off.", style = MaterialTheme.typography.bodyLarge)
                entries.isEmpty() -> Text("No events yet", style = MaterialTheme.typography.bodyLarge)
                else -> SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the event log?") },
            text = { Text("The recorded events are deleted. The log stays on.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearEventLog()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}
