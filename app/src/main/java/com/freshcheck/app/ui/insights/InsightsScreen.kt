package com.freshcheck.app.ui.insights
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.nutrition.NutritionScreen
import com.freshcheck.app.ui.meals.*

/** Receives inventory projections; no database or recommendation service is created here. */
@Composable
fun InsightsScreen(topic: String, onTopic: (String) -> Unit, food: List<FoodItemUi> = emptyList(),
    onExit: () -> Unit = {}, onInventory: () -> Unit = {}, entryId: Int = 0,
    onHistory: () -> Unit = {}) {
    var recipeId by rememberSaveable(entryId) { mutableStateOf<String?>(null) }
    var message by rememberSaveable(entryId) { mutableStateOf<String?>(null) }
    var trail by rememberSaveable(entryId) { mutableStateOf(arrayListOf<String>()) }
    val recipe = DemoRecipes.firstOrNull { it.id == recipeId }
    fun open(target: String) { trail = ArrayList(trail + topic); onTopic(target) }
    fun back() {
        if (trail.isEmpty()) onExit()
        else { val target = trail.last(); trail = ArrayList(trail.dropLast(1)); onTopic(target) }
    }
    BackHandler(topic != "Overview" && recipe == null) { back() }
    AnimatedContent(topic, label = "Food inspiration pages") { current ->
        when (current) {
            "Nutrition" -> NutritionScreen(onBack = ::back)
            "Meals" -> MealSuggestionsScreen(food, { recipeId = it }, ::back, { open("Rescue") })
            "Rescue" -> FoodRescueScreen(food, { recipeId = it }, ::back, onInventory)
            else -> InsightsDashboard(food, inventoryHistory(food), ::open, onInventory, onHistory)
        }
    }
    recipe?.let { selected ->
        Dialog(onDismissRequest = { recipeId = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.safeDrawingPadding()) {
                    RecipeDetailsScreen(selected, food, RecipeActions(
                        onBack = { recipeId = null },
                        onSave = { message = "Recipe persistence integration pending. No recipe was saved." },
                        onShoppingList = { missing -> message = "Shopping list integration pending. Nothing was saved.\nMissing: ${missing.joinToString()}." }
                    ))
                }
            }
            message?.let { text ->
                AlertDialog(onDismissRequest = { message = null }, title = { Text("Coming soon") },
                    text = { Text(text) }, confirmButton = { TextButton({ message = null }) { Text("Got it") } })
            }
        }
    }
}
