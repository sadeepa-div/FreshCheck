package com.freshcheck.app.ui.myfood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.result.FreshnessCondition
import java.time.LocalDate

@Composable
fun MyFoodScreen(items: List<FoodItemUi>, actions: FoodActions, onScan: () -> Unit,
    onLoadDemo: () -> Unit, onClearDemo: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(FoodFilter.All.name) }
    var sortName by rememberSaveable { mutableStateOf(FoodSort.Priority.name) }
    var sortMenu by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val shown = visibleFood(items, query, FoodFilter.valueOf(filterName), FoodSort.valueOf(sortName), today)
    Page {
        SectionTitle("My Food", "A thoughtful kitchen, one item at a time.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) { Text("${items.size}", style = MaterialTheme.typography.headlineLarge); Text("Tracked items") }
            Panel(Modifier.weight(1f)) { Text("${items.count { it.condition == FreshnessCondition.UseSoon }}", style = MaterialTheme.typography.headlineLarge); Text("Use Soon") }
        }
        Text(items.maxByOrNull { it.added }?.let { "Recently added · ${it.name} · ${it.added}" } ?: "Nothing added yet")
        ActionButton("Scan Food", onScan, Modifier.fillMaxWidth())
        if (items.any { it.isDemo }) {
            Text("DEMONSTRATION DATA · Edits affect session samples only. No notifications are scheduled.",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            TextButton(onClearDemo) { Text("Clear demo inventory") }
        } else TextButton(onLoadDemo) { Text("Explore sample inventory") }
        if (items.isEmpty()) {
            ProduceArt(Modifier.fillMaxWidth().height(150.dp))
            EmptyPanel("Your kitchen collection starts here", "No saved items are connected. Explore samples or scan modes to get started.",
                "Explore sample inventory", onLoadDemo)
        } else {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search produce name or type") }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoodFilter.entries.forEach { FilterChip(filterName == it.name, { filterName = it.name }, label = { Text(it.label) }) }
            }
            Box {
                OutlinedButton({ sortMenu = true }) { Text("Sort · ${FoodSort.valueOf(sortName).label}") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    FoodSort.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) },
                        onClick = { sortName = item.name; sortMenu = false }) }
                }
            }
            Text("${shown.size} matching items", style = MaterialTheme.typography.labelLarge)
            if (shown.isEmpty()) EmptyPanel("No matches", "Try another name, produce type or filter.", "Clear search and filters",
                { query = ""; filterName = FoodFilter.All.name })
            shown.forEach { item -> key(item.id) { FoodItemCard(item, today, actions) } }
        }
    }
}
