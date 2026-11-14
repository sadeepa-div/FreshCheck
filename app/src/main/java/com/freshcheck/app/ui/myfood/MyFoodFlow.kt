package com.freshcheck.app.ui.myfood
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.navigation.*
import com.freshcheck.app.ui.theme.FreshCheckTheme
import java.time.LocalDate

enum class FoodMutation(val label: String) { Used("Mark as Used"), Discarded("Mark as Discarded"), Delete("Delete") }
@Composable
fun MyFoodFlow(inventory: DemoInventory, onScan: (String?) -> Unit, onRescue: () -> Unit = {},
    openItemId: String? = null, onOpenHandled: () -> Unit = {},
    connectedItems: List<FoodItemUi>? = null, mutations: FoodMutationActions = FoodMutationActions()) {
    var route by rememberSaveable { mutableStateOf("list") }
    var itemId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by rememberSaveable { mutableStateOf(FoodMutation.Delete.name) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val items = connectedItems ?: inventory.items
    LaunchedEffect(openItemId) {
        if (openItemId != null) { itemId = openItemId; route = "details"; onOpenHandled() }
    }
    val item = items.firstOrNull { it.id == itemId }
    fun back() { route = if (route == "edit" || route == "journey") "details" else "list" }
    fun ask(id: String, action: FoodMutation) { pendingId = id; pendingAction = action.name }
    fun completion(result: UiOperationResult) {
        busy = false; pendingId = null
        when (result) {
            UiOperationResult.Confirmed -> { route = "list"; feedback = "Repository confirmed the operation." }
            is UiOperationResult.Failed -> feedback = result.message
        }
    }
    BackHandler(route != "list" || pendingId != null) { if (!busy) { if (pendingId != null) pendingId = null else back() } }
    val actions = FoodActions(
        onOpen = { itemId = it; route = "details" }, onEdit = { itemId = it; route = "edit" },
        onJourney = { itemId = it; route = "journey" }, onRescan = onScan,
        onMarkUsed = { ask(it, FoodMutation.Used) }, onMarkDiscarded = { ask(it, FoodMutation.Discarded) },
        onDelete = { ask(it, FoodMutation.Delete) })
    AnimatedContent(if (item == null) "list" else route, label = "Food navigation") { screen ->
        when (screen) {
            "details" -> item?.let { FoodDetailsScreen(it, actions, ::back) }
            "edit" -> item?.let { EditFoodItemScreen(it, { updated ->
                if (connectedItems == null && updated.isDemo) {
                    inventory.update(updated); route = "details"; feedback = "Sample updated for this session only. No notification scheduled."
                } else if (!busy) {
                    busy = true
                    mutations.edit(updated) { result ->
                        busy = false
                        if (result == UiOperationResult.Confirmed) { route = "details"; feedback = "Repository confirmed changes." }
                        else feedback = (result as UiOperationResult.Failed).message
                    }
                }
            }, ::back) }
            "journey" -> item?.let { FreshnessJourneyScreen(it, ::back, { onScan(it.id) }) }
            else -> MyFoodScreen(items, actions, { onScan(null) },
                { if (connectedItems == null) inventory.load(LocalDate.now()) },
                { if (connectedItems == null) { inventory.clear(); itemId = null; route = "list" } }, onRescue,
                allowDemo = connectedItems == null)
        }
    }
    pendingId?.let { id ->
        val action = FoodMutation.valueOf(pendingAction)
        val isDemo = connectedItems == null && items.any { it.id == id && it.isDemo }
        val description = when (action) {
            FoodMutation.Used -> "Record this item as Used and remove it from active inventory. Its scan history should be retained."
            FoodMutation.Discarded -> "Record this item as Discarded and remove it from active inventory. Its scan history should be retained."
            FoodMutation.Delete -> "Delete this food item. This is not a Used or Discarded outcome. Scan retention is controlled by the repository."
        }
        AlertDialog(onDismissRequest = { if (!busy) pendingId = null }, title = { Text("${action.label} item?") },
            text = { Text(if (isDemo) "DEMO ONLY · $description No real database will be updated." else description) },
            confirmButton = { TextButton({
                if (isDemo) {
                    when (action) {
                        FoodMutation.Used -> inventory.markUsed(id)
                        FoodMutation.Discarded -> inventory.markDiscarded(id)
                        FoodMutation.Delete -> inventory.remove(id)
                    }
                    pendingId = null; route = "list"; feedback = "Demo action applied locally only."
                } else {
                    busy = true
                    when (action) {
                        FoodMutation.Used -> mutations.markUsed(id, ::completion)
                        FoodMutation.Discarded -> mutations.markDiscarded(id, ::completion)
                        FoodMutation.Delete -> mutations.delete(id, ::completion)
                    }
                }
            }, enabled = !busy) { Text("Confirm ${action.label}") } },
            dismissButton = { TextButton({ pendingId = null }, enabled = !busy) { Text("Cancel") } })
    }
    feedback?.let { AlertDialog(onDismissRequest = { feedback = null }, title = { Text("Food management") },
        text = { Text(it) }, confirmButton = { TextButton({ feedback = null }) { Text("Got it") } }) }
}
@Preview(name = "Sample inventory · small phone", widthDp = 320, heightDp = 740)
@Composable
private fun InventoryPreview() {
    FreshCheckTheme { Surface { MyFoodFlow(remember { DemoInventory(sampleFood(LocalDate.now())) }, {}) } }
}
