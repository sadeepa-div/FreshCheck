package com.freshcheck.app.ui.profile
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.settings.*

@Composable
fun ProfileScreen(name: String, onName: (String) -> Unit, foodCount: Int = 0, scanCount: Int = 0,
    theme: ThemePreference = ThemePreference.System, onTheme: (ThemePreference) -> Unit = {},
    onHistory: () -> Unit = {}, onInsights: () -> Unit = {}, onFood: () -> Unit = {},
    containsSamples: Boolean = false,
    historyActions: com.freshcheck.app.ui.history.HistoryActions = com.freshcheck.app.ui.history.HistoryActions(),
    reminderData: com.freshcheck.app.reminders.ReminderUiData = com.freshcheck.app.reminders.ReminderUiData(),
    reminderActions: com.freshcheck.app.reminders.ReminderActions = com.freshcheck.app.reminders.ReminderActions()) {
    var route by rememberSaveable { mutableStateOf("Profile") }
    fun back() { route = if (route == "About" || route == "Privacy") "Settings" else "Profile" }
    BackHandler(route != "Profile") { back() }
    AnimatedContent(route, label = "Profile pages") { page ->
        when (page) {
            "Settings" -> SettingsScreen(theme, onTheme, ::back, { route = it }, onHistory, historyActions = historyActions,
                reminderData = reminderData, reminderActions = reminderActions)
            "About", "Privacy" -> InformationScreen(page, ::back)
            else -> Page {
                SectionTitle("FreshCheck · Your space", "Offline core features. No login needed.")
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(name.trim().take(2).uppercase().ifBlank { "FC" },
                        Modifier.padding(24.dp), style = MaterialTheme.typography.headlineLarge)
                }
                Text(name.ifBlank { "Hello, food lover" }, style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(name, { onName(it.take(32)) }, Modifier.fillMaxWidth(),
                    label = { Text("Optional display name") }, singleLine = true,
                    supportingText = { Text("Session only · no account or permanent profile storage.") })
                Panel {
                    Text("My Food · $foodCount active items\nScan records · $scanCount")
                    if (containsSamples) Text("Includes labelled demonstration records.")
                }
                ActionButton("Open My Food", onFood, Modifier.fillMaxWidth())
                ActionButton("Scan History", onHistory, Modifier.fillMaxWidth())
                ActionButton("FreshCheck Insights", onInsights, Modifier.fillMaxWidth())
                ActionButton("Settings", { route = "Settings" }, Modifier.fillMaxWidth())
                Text("FreshCheck · EC5208\nMobile Application Development · 3 members",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
