package com.freshcheck.app.ui.myfood
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.result.*

@Composable
fun FoodDetailsScreen(item: FoodItemUi, actions: FoodActions, onBack: () -> Unit) {
    Page {
        TextButton(onBack) { Text("← My Food") }
        if (item.isDemo) Text("DEMONSTRATION RECORD · SESSION ONLY", color = MaterialTheme.colorScheme.primary)
        ProduceArt(Modifier.fillMaxWidth().height(200.dp))
        SectionTitle(item.name, item.category.label)
        FreshnessStatusCard(item.condition)
        ConfidenceIndicator(item.latest?.confidence, item.isDemo)
        Panel {
            Text("${item.quantity} ${item.unit}", style = MaterialTheme.typography.titleLarge)
            Text("Stored · ${item.storage.label}")
            Text("Added · ${item.added}")
            Text("Next re-check · ${item.recheck ?: "Not set"}")
            Text("Re-check dates are check-in suggestions, never expiry guarantees.")
            if (item.notes.isNotBlank()) Text(item.notes)
        }
        Panel {
            SectionTitle("Storage advice")
            Text("Current location: ${item.storage.label}. Produce-specific reviewed storage guidance integration pending.")
            Text(VisualAssessmentNotice, style = MaterialTheme.typography.bodySmall)
        }
        SectionTitle("Recent scan history")
        item.scans.sortedByDescending { it.at }.take(2).forEach {
            Text("${it.at.toString().replace('T', ' ')} · ${it.condition.label}")
        }
        if (item.scans.isEmpty()) Text("No scans recorded for this item.")
        ActionButton("View Full Freshness Journey", { actions.onJourney(item.id) }, Modifier.fillMaxWidth())
        ActionButton("Rescan Food", { actions.onRescan(item.id) }, Modifier.fillMaxWidth())
        OutlinedButton({ actions.onEdit(item.id) }, Modifier.fillMaxWidth()) { Text("Edit Item") }
        OutlinedButton({ actions.onEdit(item.id) }, Modifier.fillMaxWidth()) { Text("Set or Edit Reminder") }
        TextButton({ actions.onRemove(item.id, "Mark as Used") }) { Text("Mark as Used") }
        TextButton({ actions.onRemove(item.id, "Mark as Discarded") }) { Text("Mark as Discarded") }
        TextButton({ actions.onRemove(item.id, "Delete") }) { Text("Delete Item", color = MaterialTheme.colorScheme.error) }
    }
}
