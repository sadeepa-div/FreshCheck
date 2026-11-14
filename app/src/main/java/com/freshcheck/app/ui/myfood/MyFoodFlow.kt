package com.freshcheck.app.ui.myfood
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.ui.theme.FreshCheckTheme
import java.time.LocalDate

@Composable
fun MyFoodFlow(inventory: DemoInventory, onScan: (String?) -> Unit, onRescue: () -> Unit = {}) {
    var route by rememberSaveable { mutableStateOf("list") }
    var itemId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val item = inventory.items.firstOrNull { it.id == itemId }
    fun back() { route = if (route == "edit" || route == "journey") "details" else "list" }
    BackHandler(route != "list" || pendingId != null) { if (pendingId != null) pendingId = null else back() }
    val actions = FoodActions(
        onOpen = { itemId = it; route = "details" }, onEdit = { itemId = it; route = "edit" },
        onJourney = { itemId = it; route = "journey" }, onRescan = { onScan(it) },
        onRemove = { id, action -> pendingId = id; pendingAction = action })
    AnimatedContent(if (item == null) "list" else route, label = "Food navigation") { screen ->
        when (screen) {
            "details" -> item?.let { FoodDetailsScreen(it, actions, ::back) }
            "edit" -> item?.let { EditFoodItemScreen(it, { updated ->
                if (updated.isDemo) { inventory.update(updated); route = "details"; feedback = "Sample updated for this session only. No notification scheduled." }
                else feedback = "Food repository integration pending. No changes saved."
            }, ::back) }
            "journey" -> item?.let { FreshnessJourneyScreen(it, ::back, { onScan(it.id) }) }
            else -> MyFoodScreen(inventory.items, actions, { onScan(null) },
                { inventory.load(LocalDate.now()) }, { inventory.clear(); itemId = null; route = "list" }, onRescue)
        }
    }
    pendingId?.let { id ->
        AlertDialog(onDismissRequest = { pendingId = null }, title = { Text("$pendingAction item?") },
            text = { Text("This removes the sample from this session's tracked list only. No real database will be updated.") },
            confirmButton = { TextButton({
                inventory.remove(id); pendingId = null
                if (id == itemId) { route = "list"; itemId = null }
                feedback = "Demo action applied locally only."
            }) { Text("Confirm $pendingAction") } },
            dismissButton = { TextButton({ pendingId = null }) { Text("Cancel") } })
    }
    feedback?.let { AlertDialog(onDismissRequest = { feedback = null }, title = { Text("Demo inventory") },
        text = { Text(it) }, confirmButton = { TextButton({ feedback = null }) { Text("Got it") } }) }
}
@Preview(name = "Sample inventory · small phone", widthDp = 320, heightDp = 740)
@Composable
private fun InventoryPreview() {
    FreshCheckTheme { Surface { MyFoodFlow(remember { DemoInventory(sampleFood(LocalDate.now())) }, {}) } }
}
