package com.freshcheck.app.ui.meals
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.*
import java.time.LocalDate

@Composable
fun FoodRescueScreen(food: List<FoodItemUi>, onRecipe: (String) -> Unit, onBack: () -> Unit, onInventory: () -> Unit) {
    val today = LocalDate.now()
    val priority = rescueItems(food, today)
    val recipes = recommendRecipes(DemoRecipes, food, true, today)
    Page {
        TextButton(onBack) { Text("← Back") }
        Panel {
            SectionTitle("Rescue My Food", "Give the ingredients needing attention a plan.")
            ProduceArt(Modifier.fillMaxWidth().height(140.dp))
            Text("SAMPLE RECIPE IDEAS · ${if (food.any { it.isDemo }) "DEMONSTRATION INVENTORY" else "RECORDED INVENTORY"}")
            Text("Re-check dates are check-in suggestions, not expiry dates. Manually check ingredients before preparing.")
        }
        val uncertain = food.filter { it.condition == FreshnessCondition.Uncertain }
        val spoiled = food.filter { it.condition == FreshnessCondition.VisiblySpoiled }
        if (spoiled.isNotEmpty()) Text("Excluded · ${spoiled.joinToString { it.name }}. Do not consume visibly spoiled produce.",
            color = MaterialTheme.colorScheme.error)
        if (uncertain.isNotEmpty()) Text("Manual check needed · ${uncertain.joinToString { it.name }}. These records are not counted as available or recommended for consumption.")
        if (priority.isEmpty()) EmptyPanel("Nothing eligible needs attention", "No eligible Use Soon or due-for-re-check items are available.", "Open My Food", onInventory)
        priority.forEach { item ->
            Panel {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                Text("${item.condition.symbol} ${item.condition.label} · ${priorityLabel(item, today)}")
                Text("Recorded condition only. Check manually; a reminder is not a safety decision.")
            }
        }
        if (recipes.isNotEmpty()) SectionTitle("A few ways forward", "Toast, toss or blend · sample preparation ideas")
        recipes.forEach { RecipeCard(it, food, onRecipe) }
        Text(VisualAssessmentNotice, style = MaterialTheme.typography.bodySmall)
    }
}
