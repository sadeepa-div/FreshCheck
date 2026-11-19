package com.freshcheck.app.ui.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.freshcheck.app.integration.rememberUiCompletionGate
import com.freshcheck.app.reminders.*
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.myfood.FoodItemUi
import java.time.*
import java.time.format.DateTimeFormatter

/** Explicit user input only: opening a picker never saves a date/time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(food: FoodItemUi, state: ReminderUiData, actions: ReminderActions, onBack: () -> Unit) {
    val rows = state.rows.filter { it.foodId == food.id }
    var selectedId by rememberSaveable(food.id) { mutableStateOf<String?>(null) }
    var creating by rememberSaveable(food.id) { mutableStateOf(false) }
    val selected = rows.firstOrNull { it.id == selectedId }
    var date by rememberSaveable(food.id) { mutableStateOf(food.recheck?.toString() ?: "") }
    var time by rememberSaveable(food.id) { mutableStateOf("") }
    var zone by rememberSaveable(food.id) { mutableStateOf(ZoneId.systemDefault().id) }
    var offset by rememberSaveable(food.id) { mutableStateOf<Int?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val completions = rememberUiCompletionGate()

    LaunchedEffect(rows.map { it.id }, creating) {
        if (!creating && selectedId == null) selectedId = rows.maxByOrNull { it.createdAt }?.id
    }
    LaunchedEffect(selectedId) {
        selected?.let {
            val local = it.dueAt.atZone(ZoneId.systemDefault())
            zone = local.zone.id
            date = local.toLocalDate().toString()
            time = local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            offset = local.offset.totalSeconds
        }
    }
    fun completion(result: ReminderResult) {
        busy = false
        message = result.message
        if (result.saved && result.reminderId != null) { selectedId = result.reminderId; creating = false }
    }
    BackHandler { if (!busy) onBack() }
    Page {
        TextButton(onBack, enabled = !busy) { Text("← Food Details") }
        SectionTitle("Re-check reminder", food.name)
        if (food.isDemo || !ReminderRules.realId(food.id)) {
            Panel { Text("DEMO ONLY · Samples cannot save reminders or schedule notifications.") }
        } else {
            rows.forEach { row ->
                FilterChip(selectedId == row.id && !creating, {
                    selectedId = row.id; creating = false
                }, enabled = !busy, label = { Text("Reminder · ${row.dueAt}") })
            }
            if (rows.isNotEmpty()) TextButton({
                selectedId = null; creating = true; date = food.recheck?.toString() ?: ""; time = ""; offset = null
            }, enabled = !busy) { Text("Create another reminder") }
            selected?.let {
                Text("Stored instant: ${it.dueAt}")
                Text(state.status[it.id] ?: "Loading scheduling status…")
            }
            OutlinedTextField(date, { date = it; offset = null }, Modifier.fillMaxWidth(),
                label = { Text("Re-check date · YYYY-MM-DD") }, singleLine = true, enabled = !busy)
            TextButton({ showDate = true }, enabled = !busy) { Text("Choose date") }
            OutlinedTextField(time, { time = it; offset = null }, Modifier.fillMaxWidth(),
                label = { Text("Notification time · HH:mm") }, singleLine = true, enabled = !busy)
            TextButton({ showTime = true }, enabled = !busy) { Text("Choose time") }
            OutlinedTextField(zone, { zone = it; offset = null }, Modifier.fillMaxWidth(),
                label = { Text("Time zone") }, singleLine = true, enabled = !busy)
            val offsets = runCatching {
                ZoneId.of(zone).rules.getValidOffsets(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)))
            }.getOrDefault(emptyList())
            if (offsets.size > 1) {
                Text("This time occurs twice. Choose the intended UTC offset.")
                offsets.forEach { option ->
                    FilterChip(offset == option.totalSeconds, { offset = option.totalSeconds },
                        label = { Text("UTC${option.id}") }, enabled = !busy)
                }
            }
            Text("This reminder stays at its saved instant when you travel. Review the date/time above before updating.")
            Button({
                if (!busy) {
                    busy = true
                    actions.save(ReminderDraft(food.id, selectedId, date, time, zone, offset), completions.once(::completion))
                }
            }, enabled = state.ready && !busy && !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedId == null) "Save reminder" else "Update reminder")
            }
            selected?.let { row ->
                OutlinedButton({
                    if (!busy) { busy = true; actions.disable(row.id, completions.once(::completion)) }
                }, enabled = !busy && !state.busy && row.enabled) { Text("Disable reminder") }
                OutlinedButton({
                    if (!busy) {
                        busy = true
                        actions.delete(row.id, completions.once { result ->
                            completion(result)
                            if (result.saved) { selectedId = null; creating = true; time = "" }
                        })
                    }
                }, enabled = !busy && !state.busy) { Text("Cancel and delete reminder") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            ReminderSettingsPanel(state, actions)
        }
    }

    if (showDate) {
        val initial = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton({
                picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString(); offset = null }
                showDate = false
            }, enabled = picker.selectedDateMillis != null) { Text("Use date") } },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
    if (showTime) {
        val initial = runCatching { LocalTime.parse(time) }.getOrNull() ?: LocalTime.now()
        val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp)) {
                    TimePicker(picker)
                    TextButton({
                        time = "%02d:%02d".format(java.util.Locale.ROOT, picker.hour, picker.minute); offset = null; showTime = false
                    }) { Text("Use time") }
                    TextButton({ showTime = false }) { Text("Cancel") }
                }
            }
        }
    }
}
