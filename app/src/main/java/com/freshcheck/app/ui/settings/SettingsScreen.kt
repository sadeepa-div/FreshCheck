package com.freshcheck.app.ui.settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.history.HistoryActions
import com.freshcheck.app.ui.result.VisualAssessmentNotice
import com.freshcheck.app.integration.rememberUiCompletionGate

enum class ThemePreference(val label: String) { System("Follow system"), Light("Light mode"), Dark("Dark mode") }
/** Member 3 may persist theme and reminder preferences behind these UI callbacks.
 * Permissions and scheduler capabilities must be supplied explicitly; session preferences imply neither.
 */
data class NotificationSettingsUi(val permissionExplanation: String = "Permission and reminder service are not connected.",
    val serviceReady: Boolean = false)
@Composable
fun SettingsScreen(theme: ThemePreference, onTheme: (ThemePreference) -> Unit, onBack: () -> Unit,
    onPage: (String) -> Unit, onHistory: () -> Unit, historyActions: HistoryActions = HistoryActions(),
    notification: NotificationSettingsUi = NotificationSettingsUi()) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val completions = rememberUiCompletionGate()
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        TextButton(onBack) { Text("← Profile") }
        SectionTitle("Settings", "Make FreshCheck feel like home.")
        Panel {
            SectionTitle("Appearance")
            ThemePreference.entries.forEach { preference ->
                FilterChip(theme == preference, { onTheme(preference) }, label = { Text(preference.label) })
            }
            Text("Applies to the whole app. Session-only selection; no DataStore persistence is connected.")
        }
        Panel {
            SectionTitle("Reminder notifications")
            Text(notification.permissionExplanation)
            Row { Text("Enable reminders", Modifier.weight(1f)); Switch(false, null, enabled = false) }
            Row { Text("Re-check reminders", Modifier.weight(1f)); Switch(false, null, enabled = false) }
            OutlinedButton({}, enabled = false) { Text("Reminder preferences · pending") }
            Text("Controls stay disabled until permission and Member 3's reminder service are connected. No notifications are scheduled.")
        }
        Panel {
            SectionTitle("Privacy & data")
            TextButton({ onPage("Privacy") }) { Text("Privacy and on-device AI") }
            TextButton(onHistory) { Text("Review individual scan records") }
            OutlinedButton({ confirm = true }, enabled = historyActions.canClear && !busy) { Text("Clear local history") }
            Text(if (historyActions.canClear) "Clear saved scan history after confirmation. Food items and outcome records are retained."
                else "Clearing history is disabled until a real repository is connected. No simulated deletion is presented as successful.")
        }
        TextButton({ onPage("About") }) { Text("About FreshCheck") }
        Text(VisualAssessmentNotice, style = MaterialTheme.typography.bodySmall)
    }
    if (confirm) AlertDialog(onDismissRequest = { if (!busy) confirm = false }, title = { Text("Clear local history?") },
        text = { Text("All scan history will be removed by the connected repository. This cannot be undone.") },
        confirmButton = { TextButton({
            if (!busy) {
            busy = true
            historyActions.clear(completions.once { success ->
                busy = false; confirm = false
                message = if (success) "Repository confirmed history was cleared." else "Clearing history was not confirmed."
            })
            }
        }, enabled = !busy) { Text("Clear history") } },
        dismissButton = { TextButton({ confirm = false }, enabled = !busy) { Text("Cancel") } })
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("History") },
        text = { Text(it) }, confirmButton = { TextButton({ message = null }) { Text("OK") } }) }
}
@Composable
fun InformationScreen(page: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "Unavailable" }
    Page {
        TextButton(onBack) { Text("← Settings") }
        SectionTitle(if (page == "About") "About FreshCheck" else "Privacy & responsible AI")
        Panel {
            if (page == "About") {
                Text("FreshCheck", style = MaterialTheme.typography.headlineLarge)
                Text("AI-assisted fruit and vegetable visible-condition assessment.")
                Text("EC5208 · Mobile Application Development\nUniversity project · 3 team members\nVersion $version")
            } else {
                Text("Offline core features need no login. Saved inventory, scan history and outcomes use the local Room database. Sample exploration stays separate.")
                Text("On-device inference is planned; the real model is not connected in this UI. No real image analysis is performed by the demo.")
                Text("Camera capture is not connected here. Saved scans retain local image URI references; durable image access and file retention still require scanner integration.")
                Text("Theme and display name are session preferences, restored across rotation where possible, with no account or permanent preference store.")
            }
            Text(VisualAssessmentNotice)
        }
    }
}
