package com.gideontek.phonetrack

import android.os.Build
import androidx.compose.runtime.Composable

/** The permissions the app needs, one row each with its state. Rows for newer Android versions are omitted on older ones. */
@Composable
fun PermissionsSection(permissions: PermissionsState, isLocked: Boolean) {
    SettingsSection("Permissions") {
        PermissionRow(
            label = "SMS",
            supporting = "Receive requests and send replies",
            granted = permissions.smsGranted,
            grantEnabled = !isLocked,
            onGrant = permissions::requestSms
        )
        RowDivider()
        PermissionRow(
            label = "Location",
            supporting = "Find where this phone is",
            granted = permissions.locationGranted,
            grantEnabled = !isLocked,
            onGrant = permissions::requestLocation
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RowDivider()
            PermissionRow(
                label = "Background location",
                supporting = "Needed so a text can start a location reply. Grant it after Location.",
                granted = permissions.bgLocationGranted,
                grantEnabled = !isLocked,
                onGrant = permissions::requestBgLocation
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RowDivider()
            PermissionRow(
                label = "Notifications",
                supporting = "Tell you when a reply can't be sent",
                granted = permissions.notificationsGranted,
                grantEnabled = !isLocked,
                onGrant = permissions::requestNotifications
            )
        }
    }
}
