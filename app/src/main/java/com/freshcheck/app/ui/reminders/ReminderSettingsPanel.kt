package com.freshcheck.app.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.freshcheck.app.reminders.*
import com.freshcheck.app.ui.components.*

@Composable
fun ReminderSettingsPanel(state: ReminderUiData, actions: ReminderActions) {
    Panel {
        SectionTitle("Reminder notifications")
        Row {
            Text("Enable reminder notifications", Modifier.weight(1f))
            Switch(state.preferences.notifications, {
                actions.setPreferences(state.preferences.copy(notifications = it))
            }, enabled = state.ready && !state.busy)
        }
        Row {
            Text("Enable re-check reminders", Modifier.weight(1f))
            Switch(state.preferences.rechecks, {
                actions.setPreferences(state.preferences.copy(rechecks = it))
            }, enabled = state.ready && !state.busy)
        }
        Text(if (state.preferences.enabled && state.availability.allowed)
            "Notification delivery is permitted. Each reminder still needs confirmed WorkManager work."
            else "Notifications are not active. Saved reminder definitions are retained.")
        Text("Permission: ${if (state.availability.permission) "granted / not required" else "not granted"}")
        Text("Android app notifications: ${if (state.availability.appEnabled) "enabled" else "disabled"}")
        Text("Reminder channel: ${if (state.availability.channelEnabled) "available" else "disabled / unavailable"}")
        Text(state.availability.explanation)
        if (!state.availability.permission)
            TextButton(actions.requestPermission, enabled = state.ready) { Text("Allow notification permission") }
        TextButton(actions.openSettings) { Text("Open Android notification settings") }
        TextButton(actions.refresh, enabled = !state.busy) { Text("Refresh reminder status") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Times are approximate. Doze, battery settings and Android scheduling can delay delivery.",
            style = MaterialTheme.typography.bodySmall)
    }
}
