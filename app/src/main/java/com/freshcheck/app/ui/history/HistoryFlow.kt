package com.freshcheck.app.ui.history
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.insights.*
import com.freshcheck.app.ui.result.*

/** Capabilities remain false until repository callbacks can report confirmed completion. */
data class HistoryActions(
    val canDelete: Boolean = false,
    val delete: (String, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    val canClear: Boolean = false,
    val clear: ((Boolean) -> Unit) -> Unit = { done -> done(false) }
)
@Composable
fun HistoryFlow(records: List<ScanHistoryRecord>, onClose: () -> Unit, onFood: (String) -> Unit,
    actions: HistoryActions = HistoryActions()) {
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All categories") }
    var condition by rememberSaveable { mutableStateOf("All conditions") }
    var mode by rememberSaveable { mutableStateOf("All modes") }
    var newest by rememberSaveable { mutableStateOf(true) }
    var samples by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val data = records + if (samples) sampleHistory(java.time.LocalDate.now()) else emptyList()
    val record = data.firstOrNull { it.id == id }
    BackHandler { if (id != null) id = null else onClose() }
    AnimatedContent(record, label = "History details") { selected ->
        Page {
            TextButton({ if (selected != null) id = null else onClose() }) { Text(if (selected != null) "← Scan History" else "← Back") }
            if (selected == null) {
                SectionTitle("Scan History", "Recorded moments, with their limitations.")
                TextButton({ samples = !samples }) { Text(if (samples) "Hide sample history" else "Explore sample history") }
                if (data.any { it.isDemo }) Text("DEMONSTRATION RECORDS INCLUDED", color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search produce name") }, singleLine = true)
                ChoiceMenu("Category", category, listOf("All categories") + ProduceCategory.entries.map { it.label }) { category = it }
                ChoiceMenu("Condition", condition, listOf("All conditions") + FreshnessCondition.entries.map { it.label }) { condition = it }
                ChoiceMenu("Mode", mode, listOf("All modes", "Before Buying", "After Buying", "Not recorded")) { mode = it }
                FilterChip(newest, { newest = !newest }, label = { Text(if (newest) "Newest first" else "Oldest first") })
                val matches = filterHistory(data, query, category, condition, mode, newest)
                if (matches.isEmpty()) EmptyPanel("No matching scans", "No connected history exists or your filters have no matches.", "Reset filters", {
                    query = ""; category = "All categories"; condition = "All conditions"; mode = "All modes"
                })
                matches.forEach { scan ->
                    Panel {
                        ProduceImage(scan.imageUri, "Scan of ${scan.name}", Modifier.size(72.dp))
                        Text("Scan image placeholder", style = MaterialTheme.typography.labelSmall)
                        Text(scan.name, style = MaterialTheme.typography.titleLarge)
                        Text("${scan.at.toString().replace('T', ' ')} · ${scan.mode?.label ?: "Mode not recorded"}")
                        Text("${scan.condition.symbol} ${scan.condition.label}")
                        scan.confidence?.let { Text("Model confidence · ${(it * 100).toInt()}%") }
                        if (scan.isDemo) Text("SAMPLE RECORD")
                        ActionButton("View scan · ${scan.name}", { id = scan.id }, Modifier.fillMaxWidth())
                    }
                }
            } else {
                SectionTitle(selected.name, selected.category.label)
                if (selected.isDemo) Text("DEMONSTRATION · NO REAL MODEL OUTPUT", color = MaterialTheme.colorScheme.primary)
                ProduceImage(selected.imageUri, "Scan of ${selected.name}", Modifier.fillMaxWidth().height(180.dp))
                Text("Local scan image when available; illustration otherwise", style = MaterialTheme.typography.bodySmall)
                Text("Analysed · ${selected.at.toString().replace('T', ' ')}")
                Text("Mode · ${selected.mode?.label ?: "Not recorded"}")
                FreshnessStatusCard(selected.condition)
                ConfidenceIndicator(selected.confidence, selected.isDemo)
                StorageGuidanceCard(AnalysisResult(selected.name, selected.category, selected.condition,
                    selected.confidence, guidance = selected.guidance, isDemo = selected.isDemo), true)
                Text(VisualAssessmentNotice)
                selected.foodId?.let { foodId -> ActionButton("Open associated Food Details", { onFood(foodId) }, Modifier.fillMaxWidth()) }
                OutlinedButton({ confirm = true }, enabled = actions.canDelete && !selected.isDemo && !busy) { Text("Delete scan") }
                Text("Deletion is disabled until the real history repository confirms operations. Samples cannot be deleted as real records.")
            }
        }
    }
    if (confirm && record != null) AlertDialog(onDismissRequest = { if (!busy) confirm = false },
        title = { Text("Delete scan?") }, text = { Text("This operation must be confirmed by the connected repository.") },
        confirmButton = { TextButton({
            busy = true
            actions.delete(record.id) { success ->
                busy = false; confirm = false
                if (success) { id = null; message = "Repository confirmed deletion." } else message = "Deletion was not confirmed."
            }
        }, enabled = !busy) { Text("Delete") } },
        dismissButton = { TextButton({ confirm = false }, enabled = !busy) { Text("Cancel") } })
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("History") },
        text = { Text(it) }, confirmButton = { TextButton({ message = null }) { Text("OK") } }) }
}
@Composable
fun ChoiceMenu(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label · $value") }
        DropdownMenu(open, { open = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false }) }
        }
    }
}
