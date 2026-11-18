package com.freshcheck.app.ui.myfood
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.freshcheck.app.ui.components.*
import java.time.LocalDate

@Composable
fun EditFoodItemScreen(item: FoodItemUi, onSave: (FoodItemUi) -> Unit, onBack: () -> Unit) {
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity.toString()) }
    var unit by rememberSaveable(item.id) { mutableStateOf(item.unit) }
    var added by rememberSaveable(item.id) { mutableStateOf(item.added.toString()) }
    var recheck by rememberSaveable(item.id) { mutableStateOf(item.recheck?.toString() ?: "") }
    var notes by rememberSaveable(item.id) { mutableStateOf(item.notes) }
    var storageName by rememberSaveable(item.id) { mutableStateOf(item.storage.name) }
    var submitted by rememberSaveable(item.id) { mutableStateOf(false) }
    val values = FoodEditValues(quantity, unit, added, recheck, notes)
    val errors = validateFoodEdit(values, LocalDate.now())
    Page {
        TextButton(onBack) { Text("← Food Details") }
        SectionTitle("Edit ${item.name}", if (item.isDemo) "Sample changes stay in this session." else "Changes are saved after database confirmation.")
        fun error(key: String) = if (submitted) errors[key] else null
        @Composable fun Field(label: String, value: String, key: String, change: (String) -> Unit) {
            OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) },
                isError = error(key) != null, supportingText = { error(key)?.let { Text(it) } })
        }
        Field("Quantity", quantity, "quantity") { quantity = it }
        Field("Unit", unit, "unit") { unit = it }
        Text("Storage location")
        FoodStorage.entries.forEach { FilterChip(storageName == it.name, { storageName = it.name }, label = { Text(it.label) }) }
        Field("Date added · YYYY-MM-DD", added, "added") { added = it }
        Field("Optional re-check · YYYY-MM-DD", recheck, "recheck") { recheck = it }
        Text("Past re-check dates are allowed for existing overdue check-ins. No notification will be scheduled.")
        Field("Optional notes", notes, "notes") { notes = it }
        ActionButton(if (item.isDemo) "Apply sample changes" else "Submit changes", {
            submitted = true
            if (errors.isEmpty()) onSave(item.copy(quantity = quantity.toDouble(), unit = unit.trim(),
                storage = FoodStorage.valueOf(storageName), added = LocalDate.parse(added),
                recheck = recheck.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }, notes = notes.trim()))
        }, Modifier.fillMaxWidth())
    }
}
