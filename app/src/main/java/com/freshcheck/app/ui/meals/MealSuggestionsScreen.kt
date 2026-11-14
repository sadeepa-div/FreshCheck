package com.freshcheck.app.ui.meals
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.myfood.FoodItemUi
import java.time.LocalDate

fun filterRecipes(recipes: List<RecipeUi>, query: String, category: String, preference: String) = recipes.filter {
    (it.name.contains(query.trim(), true) || it.ingredients.any { ingredient -> ingredient.name.contains(query.trim(), true) }) &&
    (category == "All" || it.category == category) &&
    (preference == "Any" || preference in it.preferences || (preference == "Vegetarian" && "Vegan" in it.preferences))
}
@Composable
fun MealSuggestionsScreen(food: List<FoodItemUi>, onRecipe: (String) -> Unit, onBack: () -> Unit, onRescue: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var preference by rememberSaveable { mutableStateOf("Any") }
    val recipes = filterRecipes(recommendRecipes(DemoRecipes, food, false, LocalDate.now()), query, category, preference)
    Page {
        TextButton(onBack) { Text("← Insights") }
        SectionTitle("Smart Meals", "A little inspiration from your kitchen.")
        Text("SAMPLE RECIPES · Deterministic ingredient matches, not AI-generated recommendations.",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        ActionButton("Rescue My Food", onRescue, Modifier.fillMaxWidth())
        if (food.isEmpty()) Text("No inventory connected or sample records loaded. Browse ideas; all ingredients are shown as missing.")
        else Text("Matches use ${if (food.any { it.isDemo }) "sample" else "recorded"} inventory. Presence does not establish safety or sufficient quantity.")
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search recipes or ingredients") }, singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Breakfast", "Lunch", "Dinner", "Snacks", "Smoothies", "Salads").forEach {
                FilterChip(category == it, { category = it }, label = { Text(it) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Any", "Vegan", "Vegetarian").forEach { FilterChip(preference == it, { preference = it }, label = { Text(it) }) }
        }
        Text("Dietary tags describe sample ingredients only. Check product labels, allergies and substitutions.",
            style = MaterialTheme.typography.bodySmall)
        if (recipes.isEmpty()) EmptyPanel("No matching meal ideas", "Try a different ingredient or preference.", "Reset filters",
            { query = ""; category = "All"; preference = "Any" })
        recipes.forEach { recipe -> key(recipe.id) { RecipeCard(recipe, food, onRecipe) } }
    }
}
@Composable
fun RecipeCard(recipe: RecipeUi, food: List<FoodItemUi>, onRecipe: (String) -> Unit) {
    val matches = matchIngredients(recipe, food)
    Panel {
        ProduceArt(Modifier.fillMaxWidth().height(110.dp))
        Text("Meal illustration placeholder · sample recipe", style = MaterialTheme.typography.labelSmall)
        Text(recipe.name, style = MaterialTheme.typography.titleLarge)
        Text("${recipe.category} · ${recipe.minutes} min · ${recipe.servings} servings")
        Text(recipe.ingredients.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium)
        Text("${matches.count { it.available }}/${matches.size} ingredient types matched",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text("Matched: ${matches.filter { it.available }.joinToString { it.ingredient.name }.ifBlank { "None" }}", style = MaterialTheme.typography.bodySmall)
        Text("Missing or needs replacement/checking: ${matches.filterNot { it.available }.joinToString { it.ingredient.name }.ifBlank { "None" }}", style = MaterialTheme.typography.bodySmall)
        Text("Suggested by recorded ingredient names only. Check condition and quantities manually.")
        Text(recipe.preferences.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
        ActionButton("View ${recipe.name}", { onRecipe(recipe.id) }, Modifier.fillMaxWidth())
    }
}
