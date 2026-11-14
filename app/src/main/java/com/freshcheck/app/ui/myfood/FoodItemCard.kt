package com.freshcheck.app.ui.myfood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.ProduceArt
import java.time.LocalDate

@Composable
fun FoodItemCard(item: FoodItemUi, today: LocalDate, actions: FoodActions) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = { actions.onOpen(item.id) }, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                ProduceArt(Modifier.size(72.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text("${item.category.label} · ${item.quantity} ${item.unit}", style = MaterialTheme.typography.bodyMedium)
                }
                Box {
                    TextButton({ menu = true }, Modifier.semantics { contentDescription = "Actions for ${item.name}" }) { Text("•••") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("View details") }, onClick = { menu = false; actions.onOpen(item.id) })
                        DropdownMenuItem(text = { Text("Edit item") }, onClick = { menu = false; actions.onEdit(item.id) })
                        DropdownMenuItem(text = { Text("Delete item") }, onClick = { menu = false; actions.onRemove(item.id, "Delete") })
                    }
                }
            }
            Text("${item.condition.symbol}  ${item.condition.label}", style = MaterialTheme.typography.titleMedium,
                color = when (item.condition) {
                    com.freshcheck.app.ui.result.FreshnessCondition.VisiblySpoiled -> MaterialTheme.colorScheme.error
                    com.freshcheck.app.ui.result.FreshnessCondition.UseSoon -> MaterialTheme.colorScheme.tertiary
                    com.freshcheck.app.ui.result.FreshnessCondition.Uncertain -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                })
            Text(item.storage.label)
            Text("Last scanned · ${item.latest?.at?.toString()?.replace('T', ' ') ?: "Not scanned"}", style = MaterialTheme.typography.bodySmall)
            Text("◷ ${priorityLabel(item, today)}", style = MaterialTheme.typography.labelLarge)
            Text("Next re-check · ${item.recheck?.toString() ?: "Not set"}", style = MaterialTheme.typography.bodySmall)
            if (item.isDemo) Text("SAMPLE RECORD", style = MaterialTheme.typography.labelSmall)
        }
    }
}
