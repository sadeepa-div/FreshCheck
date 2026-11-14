package com.freshcheck.app.ui.meals
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.result.VisualAssessmentNotice
import java.util.Locale

data class RecipeActions(val onBack: () -> Unit, val onSave: (String) -> Unit, val onShoppingList: (List<String>) -> Unit)
@Composable
fun RecipeDetailsScreen(recipe: RecipeUi, food: List<FoodItemUi>, actions: RecipeActions) {
    var servings by rememberSaveable(recipe.id) { mutableStateOf(recipe.servings) }
    val matches = matchIngredients(recipe, food)
    Page {
        TextButton(actions.onBack) { Text("← Meal recommendations") }
        Text("ORIGINAL SAMPLE RECIPE · NOT AI GENERATED", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        ProduceArt(Modifier.fillMaxWidth().height(180.dp))
        Text("Meal illustration placeholder", style = MaterialTheme.typography.bodySmall)
        SectionTitle(recipe.name, "${recipe.category} · ${recipe.minutes} min")
        Text("Servings · $servings", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ servings-- }, enabled = servings > 1) { Text("− Serving") }
            OutlinedButton({ servings++ }, enabled = servings < 8) { Text("+ Serving") }
        }
        SectionTitle("Your ingredients", "Quantities below are for $servings servings.")
        matches.forEach { match -> IngredientMatchCard(match, recipe, servings, food.any { it.isDemo }) }
        Text("Matches indicate ingredient-name presence only; quantities and condition must be checked manually.",
            style = MaterialTheme.typography.bodySmall)
        SectionTitle("Make it step by step")
        recipe.steps.forEachIndexed { index, instruction -> Panel {
            Text("STEP ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(instruction)
        } }
        ActionButton("Save Recipe · integration pending", { actions.onSave(recipe.id) }, Modifier.fillMaxWidth())
        val missing = matches.filterNot { it.available }.map { it.ingredient.name }
        if (missing.isNotEmpty()) OutlinedButton({ actions.onShoppingList(missing) }, Modifier.fillMaxWidth()) {
            Text("Add missing ingredients · pending")
        }
        Text(VisualAssessmentNotice, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun IngredientMatchCard(match: IngredientMatch, recipe: RecipeUi, servings: Int, isDemo: Boolean) {
    Panel {
        Text(match.ingredient.name, style = MaterialTheme.typography.titleMedium)
        Text(String.format(Locale.getDefault(), "%.1f %s", scaledIngredient(match.ingredient, recipe, servings), match.ingredient.unit))
        Text(when {
            match.available -> "✓ Matched in ${if (isDemo) "sample " else ""}My Food · check quantity and condition"
            match.needsReplacementOrCheck -> "! Not available from current record · replace or manually check; do not use visibly spoiled produce"
            else -> "+ Missing · source separately"
        }, color = if (match.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
